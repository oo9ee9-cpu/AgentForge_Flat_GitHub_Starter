package com.talkingrobot.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * كل دالة هنا مطابقة لاستدعاء N("اسمها", ...) الموجود بـ index.html.
 * إذا تريد تضيف ميزة جديدة لاحقاً: أضف دالة هنا بنفس الاسم اللي يناديه الجافاسكربت،
 * وأضف لها @JavascriptInterface، وراح تشتغل تلقائياً بدون أي تعديل إضافي بالـ HTML.
 */
public class AndroidBridge {

    private final MainActivity activity;
    private final WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SpeechRecognizer recognizer;
    private boolean speaking = false;
    private Intent launchIntentSnapshot;

    AndroidBridge(MainActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.launchIntentSnapshot = activity.getIntent();
        initTts();
    }

    // ---------------------------------------------------------------- helpers

    private void runJs(String js) {
        activity.runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private void fireSR(String type, Object data) {
        String payload;
        if (data == null) payload = "null";
        else if (data instanceof String) payload = JSONObject.quote((String) data);
        else if (data instanceof List) payload = new JSONArray((List<?>) data).toString();
        else payload = String.valueOf(data);
        runJs("window.onSR && window.onSR('" + type + "', " + payload + ")");
    }

    void notifyNewIntent(Intent intent) {
        this.launchIntentSnapshot = intent;
        runJs("window.onAppIntent && window.onAppIntent()");
    }

    void destroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (recognizer != null) recognizer.destroy();
    }

    // ---------------------------------------------------------------- TTS: speak/stop/voice/rate

    private void initTts() {
        tts = new TextToSpeech(activity, status -> {
            ttsReady = (status == TextToSpeech.SUCCESS);
            if (ttsReady) {
                try { tts.setLanguage(new Locale("ar")); } catch (Exception ignored) {}
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { speaking = true; }
                    @Override public void onDone(String utteranceId) { speaking = false; }
                    @Override public void onError(String utteranceId) { speaking = false; }
                });
            }
        });
    }

    @JavascriptInterface
    public int ttsState() {
        if (tts == null) return 0;
        return ttsReady ? 1 : 0;
    }

    @JavascriptInterface
    public void speak(String text, String lang) {
        if (tts == null || text == null || text.isEmpty()) return;
        try {
            if (lang != null && !lang.isEmpty()) {
                Locale l = lang.startsWith("ar") ? new Locale("ar") : Locale.US;
                tts.setLanguage(l);
            }
            String id = "u" + System.currentTimeMillis();
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, id);
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public boolean isSpeaking() {
        return tts != null && (tts.isSpeaking() || speaking);
    }

    @JavascriptInterface
    public void stop() {
        if (tts != null) tts.stop();
        speaking = false;
    }

    @JavascriptInterface
    public boolean setVoice(String voiceName) {
        if (tts == null || voiceName == null) return false;
        try {
            for (android.speech.tts.Voice v : tts.getVoices()) {
                if (v.getName().equalsIgnoreCase(voiceName)) {
                    tts.setVoice(v);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    @JavascriptInterface
    public void setRate(float rate) {
        if (tts != null) try { tts.setSpeechRate(rate); } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------- Speech recognition (listen)

    @JavascriptInterface
    public boolean srAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(activity);
    }

    @JavascriptInterface
    public void listen(String lang) {
        activity.runOnUiThread(() -> {
            if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, (lang == null || lang.isEmpty()) ? "ar-IQ" : lang);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(android.os.Bundle params) { fireSR("ready", null); }
                @Override public void onBeginningOfSpeech() { fireSR("begin", null); }
                @Override public void onRmsChanged(float rmsdB) { fireSR("rms", String.valueOf(rmsdB)); }
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { fireSR("end", null); }
                @Override public void onError(int error) { fireSR("error", error); }
                @Override public void onResults(android.os.Bundle results) {
                    fireSR("final", results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
                }
                @Override public void onPartialResults(android.os.Bundle partialResults) {
                    fireSR("partial", partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
                }
                @Override public void onEvent(int eventType, android.os.Bundle params) {}
            });
            try { recognizer.startListening(i); } catch (Exception e) { fireSR("error", -1); }
        });
    }

    @JavascriptInterface
    public void cancelListen() {
        activity.runOnUiThread(() -> { if (recognizer != null) try { recognizer.cancel(); } catch (Exception ignored) {} });
    }

    @JavascriptInterface
    public void stopListen() {
        activity.runOnUiThread(() -> { if (recognizer != null) try { recognizer.stopListening(); } catch (Exception ignored) {} });
    }

    @JavascriptInterface
    public void resetListen() {
        activity.runOnUiThread(() -> {
            if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        });
    }

    // ---------------------------------------------------------------- Permissions

    @JavascriptInterface
    public int perm(String permission) {
        if (permission == null) return -1;
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED ? 0 : -1;
    }

    @JavascriptInterface
    public void askPerm(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isEmpty()) return;
        String[] perms = commaSeparated.split(",");
        activity.runOnUiThread(() ->
            ActivityCompat.requestPermissions(activity, perms, MainActivity.REQ_RUNTIME_PERMS));
    }

    @JavascriptInterface
    public void askPerms() {
        askPerm(Manifest.permission.RECORD_AUDIO);
    }

    // ---------------------------------------------------------------- Contacts

    @JavascriptInterface
    public String contacts() {
        JSONArray out = new JSONArray();
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return out.toString();
        ContentResolver cr = activity.getContentResolver();
        try (Cursor c = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                             ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    out.put(c.getString(0));
                    out.put(c.getString(1));
                }
            }
        } catch (Exception ignored) {}
        return out.toString();
    }

    // ---------------------------------------------------------------- Apps

    @JavascriptInterface
    public String apps() {
        JSONArray out = new JSONArray();
        PackageManager pm = activity.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            try {
                out.put(ri.loadLabel(pm).toString());
                out.put(ri.activityInfo.packageName);
            } catch (Exception ignored) {}
        }
        return out.toString();
    }

    @JavascriptInterface
    public boolean openApp(String pkg) {
        try {
            Intent i = activity.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
            return true;
        } catch (Exception e) { return false; }
    }

    // ---------------------------------------------------------------- Generic intent / browse

    @JavascriptInterface
    public boolean intent(String jsonIntent) {
        try {
            JSONObject o = new JSONObject(jsonIntent);
            Intent i;
            if (o.has("action")) i = new Intent(o.getString("action"));
            else i = new Intent();
            if (o.has("selector")) {
                Intent sel = new Intent(Intent.ACTION_MAIN);
                sel.addCategory(o.getString("selector"));
                i = sel;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
            return true;
        } catch (Exception e) { return false; }
    }

    @JavascriptInterface
    public void openUrl(String url) { activity.openExternalUrl(url); }

    @JavascriptInterface
    public void browse(String url) { activity.openExternalUrl(url); }

    // ---------------------------------------------------------------- Screen / background

    @JavascriptInterface
    public void screenOn() { activity.keepScreenOn(true); }

    @JavascriptInterface
    public void screenAuto() { activity.keepScreenOn(false); }

    @JavascriptInterface
    public void bg(boolean enabled) {
        Intent svc = new Intent(activity, BgListenService.class);
        if (enabled) ContextCompat.startForegroundService(activity, svc);
        else activity.stopService(svc);
    }

    @JavascriptInterface
    public String launchAction() {
        return launchIntentSnapshot != null && launchIntentSnapshot.getAction() != null
                ? launchIntentSnapshot.getAction() : "";
    }
}
