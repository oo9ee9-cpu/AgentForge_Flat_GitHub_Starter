package com.talkingrobot.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

/**
 * خدمة أمامية بسيطة (Foreground Service) هدفها بس إبقاء التطبيق حي وياخذ صلاحية المايك
 * بالخلفية (bg(true) من JS). ما تسوي تعرّف صوت بنفسها - المنطق الفعلي يبقى بـ MainActivity.
 * وسّعها إذا تريد استماع حقيقي للكلمة المفتاحية وشاشة التطبيق مقفلة.
 */
public class BgListenService extends Service {
    private static final String CHANNEL_ID = "talking_robot_bg";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "استماع بالخلفية", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("الروبوت المتكلّم يستمع بالخلفية")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
