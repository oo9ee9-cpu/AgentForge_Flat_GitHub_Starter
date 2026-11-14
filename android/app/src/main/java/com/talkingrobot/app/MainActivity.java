package com.talkingrobot.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

/**
 * يستضيف الواجهة الكاملة (assets/index.html) ويربطها بأندرويد عبر AndroidBridge.
 * كل منطق التطبيق (الصوت، المحادثة، الواجهة) موجود بـ index.html -
 * هذا الملف مسؤول فقط عن: تشغيل WebView + منح الصلاحيات + تمرير أحداث النظام.
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private AndroidBridge bridge;
    public static final int REQ_RUNTIME_PERMS = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFormat(android.graphics.PixelFormat.TRANSLUCENT);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        bridge = new AndroidBridge(this, webView);
        webView.addJavascriptInterface(bridge, "AndroidBridge");

        // يسمح لـ getUserMedia (الكاميرا/المايك) داخل الصفحة إذا الصلاحيات ممنوحة فعلياً على مستوى النظام
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // الجافاسكربت ما يحتاج استدعاء رجوع - يتحقق من حالة الصلاحية عبر perm() بنفسه (polling)
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (bridge != null) bridge.notifyNewIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (bridge != null) bridge.destroy();
        super.onDestroy();
    }

    void keepScreenOn(boolean on) {
        runOnUiThread(() -> {
            if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    void openExternalUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }
}
