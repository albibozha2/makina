package al.makina;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zeri i Makines.
 * 1) Nese telefoni ka ze shqip (TTS) -> perdor ate, pa internet.
 * 2) Perndryshe -> zeri shqip i Google Translate (me internet).
 * 3) Pa internet dhe pa ze shqip -> zeri i parazgjedhur i telefonit.
 */
public class Voice implements TextToSpeech.OnInitListener {

    private final Context ctx;
    private final Runnable onDone;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final TextToSpeech tts;
    private final LinkedList<String> queue = new LinkedList<>();

    private boolean ready = false;
    private boolean nativeSq = false;
    private MediaPlayer player;
    private int gen = 0;   // brezi aktual i fjalimit; callback-et e vjetra injorohen

    public Voice(Context c, Runnable onDone) {
        this.ctx = c;
        this.onDone = onDone;
        this.tts = new TextToSpeech(c, this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        int r = tts.setLanguage(new Locale("sq", "AL"));
        nativeSq = r >= TextToSpeech.LANG_AVAILABLE;
        tts.setPitch(0.92f);
        tts.setSpeechRate(1.0f);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { finished(id); }
            @Override public void onError(String id) { finished(id); }
        });
        ready = true;
    }

    public String engineInfo() {
        if (!ready) return "duke u ngarkuar";
        return nativeSq ? "shqip (në telefon)" : "shqip (online)";
    }

    public void speak(String text) {
        stop();
        final int g = ++gen;
        if (text == null || text.trim().isEmpty()) { done(g); return; }
        if (ready && nativeSq) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), "m" + g);
        } else {
            queue.addAll(chunks(text, 180));
            playNext(g, text);
        }
    }

    private void finished(String id) {
        try {
            int g = Integer.parseInt(id.substring(1));
            ui.post(() -> done(g));
        } catch (Exception ignored) { }
    }

    private void done(int g) {
        if (g != gen) return;
        onDone.run();
    }

    private void playNext(final int g, final String full) {
        if (g != gen) return;
        String part = queue.poll();
        if (part == null) { done(g); return; }
        try {
            String url = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=sq&q="
                    + URLEncoder.encode(part, "UTF-8");
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Linux; Android) Makina/1.0");
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(ctx, Uri.parse(url), headers);
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnCompletionListener(p -> {
                p.release();
                if (player == p) player = null;
                playNext(g, full);
            });
            mp.setOnErrorListener((p, what, extra) -> {
                p.release();
                if (player == p) player = null;
                offlineFallback(g, part);
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            offlineFallback(g, part);
        }
    }

    /** pa internet: lexo pjesen qe mbetet me zerin e telefonit */
    private void offlineFallback(int g, String part) {
        if (g != gen) return;
        StringBuilder rest = new StringBuilder(part);
        while (!queue.isEmpty()) rest.append(' ').append(queue.poll());
        if (ready) tts.speak(rest.toString(), TextToSpeech.QUEUE_FLUSH, new Bundle(), "m" + g);
        else done(g);
    }

    static List<String> chunks(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (cur.length() + word.length() + 1 > max && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
            char last = word.charAt(word.length() - 1);
            if ((last == '.' || last == '?' || last == '!') && cur.length() > max / 2) {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    public void stop() {
        gen++;
        queue.clear();
        if (ready) tts.stop();
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    public void shutdown() {
        stop();
        tts.shutdown();
    }
}
