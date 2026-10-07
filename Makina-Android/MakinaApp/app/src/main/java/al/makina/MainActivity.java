package al.makina;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends Activity implements RecognitionListener {

    static final int REQ_MIC = 1, REQ_CONTACTS = 2;
    static final int C_DIM = Color.rgb(120, 120, 110);

    private HudView hud;
    private TextView logView;
    private ScrollView scroll;
    private final SpannableStringBuilder logText = new SpannableStringBuilder();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private SpeechRecognizer recognizer;
    private Voice voice;
    private Brain brain;
    private boolean continuous;
    private boolean quitAfterSpeech = false;
    private boolean booted = false;
    private boolean listenOnce = false;

    // ------------------------------------------------------------ jeta e aktivitetit
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        prefs = getSharedPreferences("makina", MODE_PRIVATE);
        continuous = prefs.getBoolean("vazhdim", false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(5, 5, 5));

        hud = new HudView(this);
        hud.setAdmin(prefs.getString("emri", ""));
        hud.setOnClickListener(v -> toggleListen());
        hud.setOnLongClickListener(v -> { showMenu(); return true; });
        root.addView(hud, new FrameLayout.LayoutParams(-1, -1));

        float dp = getResources().getDisplayMetrics().density;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(13.5f);
        logView.setTextColor(HudView.WHITE);
        logView.setLineSpacing(3 * dp, 1f);
        logView.setPadding((int) (22 * dp), (int) (8 * dp), (int) (22 * dp), (int) (22 * dp));
        scroll.addView(logView);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, (int) (screenH * 0.36f), Gravity.BOTTOM);
        root.addView(scroll, lp);

        setContentView(root);

        voice = new Voice(this, this::onSpeechDone);
        brain = new Brain(this);
        boot();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (isAssistIntent(intent) && booted) ui.postDelayed(this::startListening, 300);
    }

    private boolean isAssistIntent(Intent i) {
        if (i == null || i.getAction() == null) return false;
        return i.getAction().equals(Intent.ACTION_ASSIST) || i.getAction().equals(Intent.ACTION_VOICE_COMMAND);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (recognizer != null) recognizer.cancel();
        if (hud.getState() == HudView.LISTEN) hud.setState(HudView.IDLE);
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        voice.shutdown();
        brain.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------ nisja
    private void boot() {
        String[] seq = {
                "[ INICIALIZIMI I SISTEMIT ......... OK ]",
                "[ MODULI I ZËRIT .................. OK ]",
                "[ NJOHJA E FOLURËS ................ " + (SpeechRecognizer.isRecognitionAvailable(this) ? "OK" : "MUNGON") + " ]",
                "[ BËRTHAMA E ARSYETIMIT ........... " + (prefs.getString("api", "").isEmpty() ? "JASHTË" : "OK") + " ]",
        };
        for (int i = 0; i < seq.length; i++) {
            final String s = seq[i];
            ui.postDelayed(() -> append(s + "\n", HudView.YELLOW), 250L * i);
        }
        ui.postDelayed(() -> {
            booted = true;
            if (prefs.getString("emri", "").isEmpty()) askName();
            else greet();
        }, 250L * seq.length + 300);
    }

    private void greet() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = (h >= 5 && h < 12) ? "Mirëmëngjes" : (h >= 12 && h < 18) ? "Mirëdita" : "Mirëmbrëma";
        reply(g + ", " + prefs.getString("emri", "") + ". Sistemi është online.");
        if (isAssistIntent(getIntent())) listenOnce = true;
    }

    private void askName() {
        EditText in = field("Emri yt", false);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("ADMINISTRATOR I RI")
                .setMessage("Si të të thërras?")
                .setView(wrap(in))
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (n.isEmpty()) n = "Admin";
                    n = Brain.cap(n.split("\\s+")[0]);
                    prefs.edit().putString("emri", n).apply();
                    refreshAdmin();
                    greet();
                })
                .show();
    }

    // ------------------------------------------------------------ dalja (thirren nga Brain)
    public void reply(String text) {
        append("[MAKINA] ", HudView.YELLOW);
        append(text + "\n", HudView.WHITE);
        hud.setSubtitle("");
        hud.setState(HudView.SPEAK);
        voice.speak(text);
    }

    public void replyThenQuit(String text) {
        quitAfterSpeech = true;
        reply(text);
    }

    public void thinking() { hud.setState(HudView.THINK); }

    public void logSystem(String text) { append(text + "\n", C_DIM); }

    public void logBox(String title, String[] lines) {
        int w = title.length() + 4;
        for (String l : lines) w = Math.max(w, l.length() + 2);
        StringBuilder sb = new StringBuilder("┌─ " + title + " ");
        while (sb.length() < w + 1) sb.append('─');
        sb.append("┐\n");
        for (String l : lines) {
            sb.append("│ ").append(l);
            for (int i = l.length(); i < w - 1; i++) sb.append(' ');
            sb.append("│\n");
        }
        sb.append('└');
        for (int i = 0; i < w; i++) sb.append('─');
        sb.append("┘\n");
        append(sb.toString(), HudView.YELLOW);
    }

    public void refreshAdmin() { hud.setAdmin(prefs.getString("emri", "")); }

    public String voiceInfo() { return voice.engineInfo(); }

    public void setContinuous(boolean on) {
        continuous = on;
        prefs.edit().putBoolean("vazhdim", on).apply();
    }

    private void append(CharSequence s, int color) {
        int start = logText.length();
        logText.append(s);
        logText.setSpan(new ForegroundColorSpan(color), start, logText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (logText.length() > 20000) logText.delete(0, logText.length() - 15000);
        logView.setText(logText);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void onSpeechDone() {
        if (quitAfterSpeech) { finish(); return; }
        if (listenOnce) { listenOnce = false; hud.setState(HudView.IDLE); ui.postDelayed(this::startListening, 300); return; }
        if (hud.getState() == HudView.SPEAK) hud.setState(HudView.IDLE);
        if (continuous && !isFinishing() && hasWindowFocus()) ui.postDelayed(this::startListening, 350);
    }

    // ------------------------------------------------------------ hyrja (degjimi)
    private void toggleListen() {
        if (hud.getState() == HudView.LISTEN) {
            if (recognizer != null) recognizer.stopListening();
            return;
        }
        startListening();
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            reply("Njohja e zërit mungon në këtë telefon. Instalo aplikacionin Google, ose mbaj shtypur ekranin për të shkruar.");
            return;
        }
        voice.stop();
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sq-AL");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "sq-AL");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        hud.setSubtitle("");
        hud.setState(HudView.LISTEN);
        recognizer.startListening(i);
    }

    private void handleInput(String text) {
        if (text == null || text.trim().isEmpty()) return;
        append("[ADMIN] ", HudView.CYAN);
        append(text.trim() + "\n", HudView.CYAN);
        hud.setSubtitle("");
        if (hud.getState() == HudView.LISTEN) hud.setState(HudView.IDLE);
        brain.handle(text.trim());
    }

    @Override public void onReadyForSpeech(Bundle b) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rms) { hud.setRms(rms); }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { hud.setState(HudView.THINK); }
    @Override public void onEvent(int type, Bundle b) { }

    @Override
    public void onError(int error) {
        hud.setState(HudView.IDLE);
        hud.setSubtitle("");
        boolean silent = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            logSystem("Mungon leja e mikrofonit.");
            return;
        }
        if (!silent) logSystem("Gabim i njohjes së zërit (" + error + ").");
        else if (!continuous) logSystem("Nuk dëgjova asgjë.");
        if (continuous && hasWindowFocus()) ui.postDelayed(this::startListening, silent ? 400 : 1500);
    }

    @Override
    public void onResults(Bundle b) {
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        hud.setState(HudView.IDLE);
        if (r == null || r.isEmpty() || r.get(0).trim().isEmpty()) {
            if (continuous) ui.postDelayed(this::startListening, 400);
            return;
        }
        handleInput(r.get(0));
    }

    @Override
    public void onPartialResults(Bundle b) {
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (r != null && !r.isEmpty()) hud.setSubtitle(r.get(0));
    }

    // ------------------------------------------------------------ lejet
    public boolean hasContactsPermission() {
        return checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestContacts() {
        requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_MIC) {
            if (ok) startListening();
            else reply("Pa mikrofon nuk të dëgjoj dot. Mbaj shtypur ekranin për të shkruar.");
        } else if (code == REQ_CONTACTS) {
            if (ok) brain.onContactsGranted();
            else reply("Pa lejen e kontakteve nuk mund të telefonoj me emër.");
        }
    }

    // ------------------------------------------------------------ menuja
    private void showMenu() {
        String[] items = {
                "⌨  Shkruaj komandë",
                "⚙  Cilësimet",
                (continuous ? "◉" : "○") + "  Dëgjim i vazhdueshëm: " + (continuous ? "PO" : "JO"),
                "✕  Pastro ekranin"
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("MAKINA")
                .setItems(items, (d, which) -> {
                    if (which == 0) typeCommand();
                    else if (which == 1) showSettings();
                    else if (which == 2) {
                        setContinuous(!continuous);
                        logSystem("Dëgjimi i vazhdueshëm: " + (continuous ? "PO" : "JO"));
                        if (continuous) startListening();
                    } else {
                        logText.clear();
                        logView.setText("");
                    }
                })
                .show();
    }

    private void typeCommand() {
        EditText in = field("p.sh. si është moti", false);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("KOMANDË")
                .setView(wrap(in))
                .setPositiveButton("Dërgo", (d, w) -> handleInput(in.getText().toString()))
                .setNegativeButton("Anulo", null)
                .show();
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText name = field("Emri yt", false);
        name.setText(prefs.getString("emri", ""));
        EditText city = field("Qyteti (për motin)", false);
        city.setText(prefs.getString("qyteti", "Tirana"));
        EditText api = field("Çelësi API i Anthropic (opsional)", true);
        api.setText(prefs.getString("api", ""));
        EditText model = field("Modeli", false);
        model.setText(prefs.getString("model", "claude-sonnet-5-5"));
        box.addView(label("Emri"));
        box.addView(name);
        box.addView(label("Qyteti"));
        box.addView(city);
        box.addView(label("Çelësi API (për pyetje të lira)"));
        box.addView(api);
        box.addView(label("Modeli i AI"));
        box.addView(model);
        box.addView(label("Zëri: " + voice.engineInfo()));
        ScrollView sv = new ScrollView(this);
        sv.addView(wrap(box));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("CILËSIMET")
                .setView(sv)
                .setPositiveButton("Ruaj", (d, w) -> {
                    prefs.edit()
                            .putString("emri", Brain.cap(name.getText().toString().trim()))
                            .putString("qyteti", Brain.cap(city.getText().toString().trim()))
                            .putString("api", api.getText().toString().trim())
                            .putString("model", model.getText().toString().trim().isEmpty()
                                    ? "claude-sonnet-5-5" : model.getText().toString().trim())
                            .apply();
                    refreshAdmin();
                    logSystem("Cilësimet u ruajtën.");
                })
                .setNegativeButton("Anulo", null)
                .show();
    }

    private EditText field(String hint, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(secret
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(HudView.YELLOW);
        t.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
        return t;
    }

    private View wrap(View v) {
        FrameLayout f = new FrameLayout(this);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        f.setPadding(p, p / 2, p, 0);
        f.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return f;
    }
}
