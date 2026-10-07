package al.makina;

import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.provider.AlarmClock;
import android.provider.ContactsContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "Truri": kupton komandat shqip dhe vendos çfarë te beje. */
public class Brain {

    private final MainActivity a;
    private final SharedPreferences prefs;
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final JSONArray history = new JSONArray();
    private final Random rnd = new Random();
    private String pendingCall = null;

    static final String[] DITET = {"e diel", "e hënë", "e martë", "e mërkurë", "e enjte", "e premte", "e shtunë"};
    static final String[] MUAJT = {"janar", "shkurt", "mars", "prill", "maj", "qershor", "korrik",
            "gusht", "shtator", "tetor", "nëntor", "dhjetor"};
    static final Map<String, Integer> NUMRAT = new HashMap<>();
    static {
        String[] w = {"zero", "nje", "dy", "tre", "kater", "pese", "gjashte", "shtate", "tete", "nente",
                "dhjete", "njembedhjete", "dymbedhjete"};
        for (int i = 0; i < w.length; i++) NUMRAT.put(w[i], i);
        NUMRAT.put("pesembedhjete", 15);
        NUMRAT.put("njezet", 20);
        NUMRAT.put("tridhjete", 30);
        NUMRAT.put("dyzet", 40);
        NUMRAT.put("pesedhjete", 50);
    }

    public Brain(MainActivity a) {
        this.a = a;
        this.prefs = a.getSharedPreferences("makina", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ ndihmes
    static String norm(String s) {
        String t = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        t = t.replaceAll("\\p{M}+", "");
        t = t.replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    static boolean has(String t, String regex) {
        return Pattern.compile(regex).matcher(t).find();
    }

    static Integer number(String t) {
        Matcher m = Pattern.compile("\\d+").matcher(t);
        if (m.find()) return Integer.parseInt(m.group());
        for (String w : t.split(" ")) if (NUMRAT.containsKey(w)) return NUMRAT.get(w);
        return null;
    }

    private String name() { return prefs.getString("emri", ""); }
    private String city() { return prefs.getString("qyteti", "Tirana"); }

    static String http(String url, String postBody, Map<String, String> headers) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(45000);
        c.setRequestProperty("User-Agent", "Makina/1.0 (Android)");
        if (headers != null) for (Map.Entry<String, String> h : headers.entrySet())
            c.setRequestProperty(h.getKey(), h.getValue());
        if (postBody != null) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            try (OutputStream o = c.getOutputStream()) {
                o.write(postBody.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
        }
        if (code >= 400) throw new Exception("HTTP " + code + ": " + sb);
        return sb.toString().trim();
    }

    private void async(Runnable r) {
        a.thinking();
        bg.execute(() -> {
            try { r.run(); }
            catch (Exception e) { a.runOnUiThread(() -> a.reply("Ndodhi një gabim: " + e.getMessage())); }
        });
    }

    private void say(String s) { a.runOnUiThread(() -> a.reply(s)); }

    // ------------------------------------------------------------ ruteri
    public void handle(String raw) {
        String t = norm(raw);
        if (t.isEmpty()) return;

        if (has(t, "^(makina )?(mirupafshim|dil|mbyll aplikacionin|fik sistemin)$")) {
            a.replyThenQuit("Sistemi po kalon në pritje. Do të jem këtu."); return;
        }
        if (has(t, "\\b(ndihme|komandat|cfare di te besh|cfare mund te besh)\\b")) { help(); return; }
        if (has(t, "\\b(me quaj|emri im eshte)\\b")) { setName(t); return; }
        if (has(t, "\\bqyteti im\\b")) { setCity(t); return; }
        if (has(t, "\\b(ndalo degjimin|mjaft degjim|mos degjo)\\b")) { a.setContinuous(false); a.reply("Dëgjimi i vazhdueshëm u çaktivizua."); return; }
        if (has(t, "\\b(degjo vazhdimisht|degjim i vazhdueshem)\\b")) { a.setContinuous(true); a.reply("Do të dëgjoj vazhdimisht."); return; }
        if (has(t, "\\b(zgjo ?me|vendos alarm|alarm ne|alarm per)\\b")) { alarm(t); return; }
        if (has(t, "\\b(me kujto|kujtese|kohemates|timer)\\b")) { reminder(t, raw); return; }
        if (has(t, "\\b(sa eshte ora|ora sa|sa ore jane)\\b") || t.equals("ora")) { time(); return; }
        if (has(t, "\\b(data|date|cfare dite|cila dite|sa eshte sot)\\b")) { date(); return; }
        if (has(t, "bateri")) { battery(); return; }
        if (has(t, "\\b(moti|motit|temperatura|bie shi|parashikimi)\\b")) { weather(t); return; }
        if (has(t, "\\b(skano|statusi|raport)\\b")) { scan(); return; }
        if (has(t, "\\bdrit|\\bpiles|\\bblic")) { torch(t); return; }
        if (has(t, "\\b(shenim|mbaj mend)")) { notes(t, raw); return; }
        if (has(t, "\\b(telefono|merr ne telefon|thirr)\\b")) { call(t); return; }
        if (has(t, "monedh")) { a.reply(rnd.nextBoolean() ? "Kokë." : "Pil."); return; }
        if (has(t, "numer (i rastit|rastesor)")) {
            Integer n = number(t); int hi = n == null || n < 2 ? 100 : n;
            a.reply("Numri është " + (1 + rnd.nextInt(hi)) + "."); return;
        }
        Matcher yt = Pattern.compile("^(luaj|kerko ne youtube)\\s+(.+)$").matcher(t);
        if (yt.find()) { openUrl("https://www.youtube.com/results?search_query=" + enc(yt.group(2)), "Po kërkoj në YouTube: " + yt.group(2) + "."); return; }
        Matcher gg = Pattern.compile("^kerko( ne google| ne internet)?\\s+(.+)$").matcher(t);
        if (gg.find()) { openUrl("https://www.google.com/search?q=" + enc(gg.group(2)), "Po kërkoj: " + gg.group(2) + "."); return; }
        Matcher op = Pattern.compile("^(hap|nis|ndiz aplikacionin)\\s+(.+)$").matcher(t);
        if (op.find()) { openApp(op.group(2)); return; }
        if (has(t, "^(kush (eshte|ishte|jane)|cfare (eshte|jane)|me trego per)\\b")) { wiki(t, raw); return; }

        askAi(raw);
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    // ------------------------------------------------------------ komandat
    private void help() {
        a.logBox("KOMANDAT", new String[]{
                "sa është ora · çfarë date është sot",
                "bateria · skano sistemin",
                "si është moti (në Durrës)",
                "kush është Skënderbeu · çfarë është ...",
                "ndiz dritën · fik dritën",
                "shkruaj shënim ... · lexo shënimet · fshi shënimet",
                "më kujto pas 10 minutash të ...",
                "zgjomë në orën 7 e 30",
                "telefono Artanin",
                "hap YouTube · luaj Dua Lipa · kërko ...",
                "hidh monedhën · numër i rastit deri në 50",
                "më quaj Arben · qyteti im është Shkodër",
                "dëgjo vazhdimisht · ndalo dëgjimin · mirupafshim"});
        a.reply("Këto janë komandat. Pyetjet e tjera i trajtoj me bërthamën e arsyetimit, nëse ke vendosur çelësin API.");
    }

    private void setName(String t) {
        Matcher m = Pattern.compile("(?:me quaj|emri im eshte)\\s+(\\p{L}+)").matcher(t);
        if (!m.find()) { a.reply("Si të të thërras?"); return; }
        String n = cap(m.group(1));
        prefs.edit().putString("emri", n).apply();
        a.refreshAdmin();
        a.reply("U regjistrua. Administratori: " + n + ".");
    }

    private void setCity(String t) {
        Matcher m = Pattern.compile("qyteti im (?:eshte\\s+)?(\\p{L}+)").matcher(t);
        if (!m.find()) return;
        String c = cap(m.group(1));
        prefs.edit().putString("qyteti", c).apply();
        a.reply("Qyteti yt u vendos: " + c + ".");
    }

    static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private void time() {
        Calendar c = Calendar.getInstance();
        a.reply(String.format(Locale.ROOT, "Ora është %d e %02d.", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)));
    }

    private void date() {
        Calendar c = Calendar.getInstance();
        a.reply("Sot është " + DITET[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + c.get(Calendar.DAY_OF_MONTH)
                + " " + MUAJT[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR) + ".");
    }

    private Intent batteryIntent() {
        return a.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private int batteryPct(Intent b) {
        int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return Math.round(lvl * 100f / scale);
    }

    private void battery() {
        Intent b = batteryIntent();
        if (b == null) { a.reply("Nuk arrita ta lexoj baterinë."); return; }
        boolean plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        int temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10;
        a.reply("Bateria është në " + batteryPct(b) + " përqind, " + (plugged ? "në karikim" : "pa karikues")
                + ". Temperatura " + temp + " gradë.");
    }

    private void weather(String t) {
        String c = city();
        Matcher m = Pattern.compile("\\b(?:ne|per)\\s+(\\p{L}+)").matcher(t);
        if (m.find() && !m.group(1).matches("sot|neser|qytet|qytetin")) c = cap(m.group(1));
        final String city = c;
        async(() -> {
            try {
                String r = http("https://wttr.in/" + enc(city) + "?format=%25C%7C%25t&lang=sq", null, null);
                String[] p = r.split("\\|");
                String temp = p[1].replace("+", "").replace("°C", "").trim();
                say("Në " + city + ": " + p[0].trim().toLowerCase(Locale.ROOT) + ", " + temp + " gradë.");
            } catch (Exception e) {
                say("Nuk mund ta marr motin tani. Kontrollo internetin.");
            }
        });
    }

    private void scan() {
        List<String> lines = new ArrayList<>();
        Intent b = batteryIntent();
        lines.add("BATERIA ....... " + (b == null ? "?" : batteryPct(b) + "%"));
        String net = "JASHTË LINJE";
        try {
            ConnectivityManager cm = a.getSystemService(ConnectivityManager.class);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc != null) {
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) net = "WI-FI";
                else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) net = "CELULAR";
                else net = "AKTIV";
            }
        } catch (Exception ignored) { }
        lines.add("RRJETI ........ " + net);
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        lines.add("HAPËSIRA ...... " + (fs.getAvailableBytes() >> 30) + " / " + (fs.getTotalBytes() >> 30) + " GB të lira");
        ActivityManager am = a.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        lines.add("MEMORIA ....... " + (mi.availMem >> 20) + " / " + (mi.totalMem >> 20) + " MB");
        lines.add("ZËRI .......... " + a.voiceInfo());
        lines.add("SHËNIME ....... " + loadNotes().length());
        lines.add("ARSYETIMI ..... " + (prefs.getString("api", "").isEmpty() ? "JASHTË" : "ONLINE"));
        a.logBox("VLERËSIM I SISTEMIT", lines.toArray(new String[0]));
        a.reply("Skanimi përfundoi. Të gjitha sistemet janë funksionale.");
    }

    private void torch(String t) {
        boolean on = !has(t, "\\b(fik|shuaj|mbyll|hiq)\\b");
        try {
            CameraManager cm = a.getSystemService(CameraManager.class);
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) {
                    cm.setTorchMode(id, on);
                    a.reply(on ? "Drita u ndez." : "Drita u fik.");
                    return;
                }
            }
            a.reply("Ky telefon nuk ka blic.");
        } catch (Exception e) {
            a.reply("Nuk arrita ta kontrolloj dritën.");
        }
    }

    private JSONArray loadNotes() {
        try { return new JSONArray(prefs.getString("shenime", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void notes(String t, String raw) {
        JSONArray notes = loadNotes();
        if (has(t, "\\b(lexo|cfare shenimesh|shenimet e mia)\\b")) {
            if (notes.length() == 0) { a.reply("Nuk ke asnjë shënim."); return; }
            StringBuilder sb = new StringBuilder("Ke " + notes.length() + " shënime. ");
            int from = Math.max(0, notes.length() - 5);
            for (int i = from; i < notes.length(); i++)
                sb.append(i - from + 1).append(". ").append(notes.optString(i)).append(". ");
            a.reply(sb.toString().trim());
            return;
        }
        if (has(t, "\\bfshi\\b")) {
            prefs.edit().putString("shenime", "[]").apply();
            a.reply("Të gjitha shënimet u fshinë.");
            return;
        }
        String body = raw.replaceFirst("(?iu)^\\W*((shkruaj|ruaj|mbaj mend|b[eë]j|nj[eë]|sh[eë]nim)\\w*\\s*)+", "").trim();
        if (body.isEmpty()) { a.reply("Çfarë të shënoj? Thuaj p.sh. shkruaj shënim të blej bukë."); return; }
        notes.put(body);
        prefs.edit().putString("shenime", notes.toString()).apply();
        a.reply("U regjistrua.");
    }

    private void reminder(String t, String raw) {
        Integer n = number(t);
        if (n == null || n <= 0) { a.reply("Pas sa minutash? Thuaj p.sh. më kujto pas 10 minutash të marr ilaçin."); return; }
        boolean hours = has(t, "\\b(ore|oresh|oret)\\b");
        boolean secs = has(t, "\\bsekond");
        int seconds = hours ? n * 3600 : secs ? n : n * 60;
        String msg = raw.replaceFirst("(?iu)^.*?\\b(minut\\w*|or[eë]\\w*|sekond\\w*)\\s*(q[eë]\\s+|t[eë]\\s+)?", "").trim();
        if (msg.isEmpty() || msg.equals(raw.trim())) msg = "Kujtesë nga Makina";
        Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, Math.min(seconds, 86400))
                .putExtra(AlarmClock.EXTRA_MESSAGE, msg)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        try {
            a.startActivity(i);
            a.reply("Në rregull. Do të të kujtoj pas " + n + (hours ? " orësh." : secs ? " sekondash." : " minutash."));
        } catch (ActivityNotFoundException e) {
            a.reply("Nuk gjeta aplikacionin e orës për kujtesën.");
        }
    }

    private void alarm(String t) {
        Matcher m = Pattern.compile("\\b(?:ne|per|ora|oren)\\s+(?:oren\\s+)?(\\d{1,2}|\\p{L}+)(?:\\s*(?:e|dhe|:)?\\s*(\\d{1,2}))?").matcher(t);
        Integer h = null, min = 0;
        while (m.find()) {
            String hs = m.group(1);
            h = hs.matches("\\d+") ? Integer.valueOf(hs) : NUMRAT.get(hs);
            if (m.group(2) != null) min = Integer.parseInt(m.group(2));
            if (h != null) break;
        }
        if (h == null) { a.reply("Në çfarë ore? Thuaj p.sh. zgjomë në orën 7 e 30."); return; }
        if (has(t, "\\b(pasdite|mbremj|darke|nates)") && h < 12) h += 12;
        if (h > 23 || min > 59) { a.reply("Ora nuk është e vlefshme."); return; }
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, min)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Makina")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        try {
            a.startActivity(i);
            a.reply(String.format(Locale.ROOT, "Alarmi u vendos për orën %d e %02d.", h, min));
        } catch (ActivityNotFoundException e) {
            a.reply("Nuk gjeta aplikacionin e orës.");
        }
    }

    private void call(String t) {
        String name = t.replaceFirst("^.*?\\b(telefono|merr ne telefon|thirr)\\s*", "").trim();
        if (name.isEmpty()) { a.reply("Kë të telefonoj?"); return; }
        if (!a.hasContactsPermission()) {
            pendingCall = name;
            a.requestContacts();
            return;
        }
        callNow(name);
    }

    /** thirret pasi perdoruesi jep lejen e kontakteve */
    public void onContactsGranted() {
        if (pendingCall != null) { String n = pendingCall; pendingCall = null; callNow(n); }
    }

    private void callNow(String name) {
        List<String> stems = new ArrayList<>();
        stems.add(name);
        for (String suf : new String[]{"in", "n", "ne", "en", "it", "es"})
            if (name.endsWith(suf) && name.length() - suf.length() >= 3) stems.add(name.substring(0, name.length() - suf.length()));
        String bestName = null, bestNum = null;
        try (Cursor c = a.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null)) {
            while (c != null && c.moveToNext()) {
                String dn = c.getString(0);
                if (dn == null) continue;
                String nd = norm(dn);
                for (String s : stems) {
                    if (nd.equals(s) || nd.startsWith(s + " ") || nd.contains(s)) {
                        bestName = dn; bestNum = c.getString(1);
                        break;
                    }
                }
                if (bestName != null && norm(bestName).startsWith(stems.get(stems.size() - 1))) break;
            }
        } catch (Exception e) {
            a.reply("Nuk i lexova dot kontaktet.");
            return;
        }
        if (bestNum == null) { a.reply("Nuk gjeta kontakt me emrin " + name + "."); return; }
        try {
            a.startActivity(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", bestNum, null)));
            a.reply("Po hap numrin e " + bestName + ". Shtyp butonin e thirrjes.");
        } catch (ActivityNotFoundException e) {
            a.reply("Ky telefon nuk bën thirrje.");
        }
    }

    private void openUrl(String url, String speech) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            a.reply(speech);
        } catch (ActivityNotFoundException e) {
            a.reply("Nuk gjeta shfletues.");
        }
    }

    private void openApp(String what) {
        PackageManager pm = a.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        ResolveInfo best = null;
        String w = what.replace(" ", "");
        for (ResolveInfo ri : apps) {
            String label = norm(ri.loadLabel(pm).toString());
            if (label.equals(what) || label.replace(" ", "").equals(w)) { best = ri; break; }
            if (best == null && (label.contains(what) || label.replace(" ", "").contains(w))) best = ri;
        }
        if (best == null) { a.reply("Nuk gjeta aplikacion me emrin " + what + "."); return; }
        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch == null) { a.reply("Nuk mund ta hap."); return; }
        a.reply("Po hap " + best.loadLabel(pm) + ".");
        a.startActivity(launch);
    }

    private void wiki(String t, String raw) {
        String q = t.replaceFirst("^(kush (eshte|ishte|jane)|cfare (eshte|jane)|me trego per)\\s*", "").trim();
        if (q.isEmpty()) { a.reply("Çfarë të kërkoj?"); return; }
        async(() -> {
            try {
                JSONArray s = new JSONArray(http("https://sq.wikipedia.org/w/api.php?action=opensearch&limit=1&format=json&search=" + enc(q), null, null));
                JSONArray titles = s.getJSONArray(1);
                if (titles.length() == 0) {
                    if (!prefs.getString("api", "").isEmpty()) { a.runOnUiThread(() -> askAi(raw)); }
                    else say("Nuk gjeta asgjë për " + q + ".");
                    return;
                }
                String title = titles.getString(0);
                JSONObject d = new JSONObject(http("https://sq.wikipedia.org/api/rest_v1/page/summary/" + enc(title.replace(' ', '_')), null, null));
                String[] sent = d.optString("extract", "").split("(?<=[.!?])\\s+");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(2, sent.length); i++) sb.append(sent[i]).append(' ');
                String out = sb.toString().trim();
                say(out.isEmpty() ? "Nuk ka përmbledhje për " + title + "." : out);
            } catch (Exception e) {
                say("Kërkimi dështoi. Kontrollo internetin.");
            }
        });
    }

    // ------------------------------------------------------------ AI
    private void askAi(String raw) {
        String key = prefs.getString("api", "").trim();
        if (key.isEmpty()) {
            a.reply("Nuk e kuptova. Thuaj ndihmë për listën e komandave.");
            return;
        }
        String model = prefs.getString("model", "claude-sonnet-5-5");
        async(() -> {
            try {
                JSONObject user = new JSONObject().put("role", "user").put("content", raw);
                history.put(user);
                while (history.length() > 12) history.remove(0);
                String sys = "Ti je MAKINA, një asistent zanor në telefonin Android të administratorit tënd ("
                        + (name().isEmpty() ? "administratori" : name()) + "). Flet VETËM shqip standard. "
                        + "Përgjigjet lexohen me zë, prandaj ji i shkurtër: 1-3 fjali, pa markdown, pa lista, pa emoji. "
                        + "Ton i qetë, i saktë, pak misterioz, si një inteligjencë besnike që mbron njeriun e saj.";
                JSONObject body = new JSONObject()
                        .put("model", model)
                        .put("max_tokens", 300)
                        .put("system", sys)
                        .put("messages", history);
                Map<String, String> h = new HashMap<>();
                h.put("x-api-key", key);
                h.put("anthropic-version", "2023-06-01");
                h.put("content-type", "application/json");
                JSONObject resp = new JSONObject(http("https://api.anthropic.com/v1/messages", body.toString(), h));
                JSONArray content = resp.getJSONArray("content");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < content.length(); i++) sb.append(content.getJSONObject(i).optString("text", ""));
                String answer = sb.toString().trim();
                history.put(new JSONObject().put("role", "assistant").put("content", answer));
                say(answer.isEmpty() ? "Nuk kam përgjigje." : answer);
            } catch (Exception e) {
                if (history.length() > 0) history.remove(history.length() - 1);
                a.runOnUiThread(() -> a.logSystem(String.valueOf(e.getMessage())));
                say("Bërthama e arsyetimit nuk u përgjigj.");
            }
        });
    }

    public void shutdown() { bg.shutdownNow(); }
}
