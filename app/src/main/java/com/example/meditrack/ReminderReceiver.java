package com.example.meditrack;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "MEDICINE_REMINDER_CHANNEL";

    @Override
    public void onReceive(Context context, Intent intent) {
        int medId = intent.getIntExtra("medId", -1);
        String medName = intent.getStringExtra("medName");
        String medDetails = intent.getStringExtra("medDetails");

        showNotification(context, medName, medDetails, medId);
        
        // FEATURE: Text-to-Speech Voice Assistant
        String speakText = "Hi! It's time to take your " + medName + ". " + medDetails;
        Intent ttsIntent = new Intent(context, TTSService.class);
        ttsIntent.putExtra("text", speakText);
        ContextCompat.startForegroundService(context, ttsIntent);
        
        // Reschedule for next day
        rescheduleNextDay(context, intent, medId);
    }

    private void showNotification(Context context, String medName, String medDetails, int medId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // Using ALARM sound instead of notification sound for better visibility
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Medicine Reminders", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Critical Channel for Medicine Reminders");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000}); // Longer vibration pattern
            
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();
            channel.setSound(alarmSound, audioAttributes);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_capsule)
                .setContentTitle("Time to take your medicine!")
                .setContentText(medName + " - " + medDetails)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(alarmSound)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setAutoCancel(true);

        notificationManager.notify(medId != -1 ? medId : (int) System.currentTimeMillis(), builder.build());
    }

    private void rescheduleNextDay(Context context, Intent intent, int medId) {
        if (medId == -1) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, medId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            }
        }
    }
}