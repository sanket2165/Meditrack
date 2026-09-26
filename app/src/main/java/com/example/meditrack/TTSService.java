package com.example.meditrack;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import androidx.core.app.NotificationCompat;
import java.util.Locale;

public class TTSService extends Service implements TextToSpeech.OnInitListener {
    private static final String CHANNEL_ID = "TTS_SERVICE_CHANNEL";
    private TextToSpeech tts;
    private String textToSpeak;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_capsule)
                .setContentTitle("Meditrack Voice Assistant")
                .setContentText("Speaking medicine reminder...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(1002, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Assistant Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            textToSpeak = intent.getStringExtra("text");
        }
        if (tts == null) {
            tts = new TextToSpeech(this, this);
        } else if (textToSpeak != null) {
            speakText();
        }
        return START_NOT_STICKY;
    }

    private void speakText() {
        if (tts != null && textToSpeak != null) {
            tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "MedID");
            new Handler(getMainLooper()).postDelayed(() -> {
                stopSelf();
            }, 10000);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                speakText();
            }
        }
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}