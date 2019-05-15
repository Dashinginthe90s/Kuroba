package com.github.adamantcheese.chan.ui.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import com.adamantcheese.github.chan.R;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.StartActivity;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class LastPageNotification extends Service {
    //random notification ID's, so one notification per thread
    private Random random = new Random();

    @Inject
    WatchManager watchManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();
            int pinId = extras.getInt("pin_id");
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(random.nextInt(), getNotification(pinId));
        }
        return START_STICKY;
    }

    private Notification getNotification(int pinId) {
        Pin pin = watchManager.findPinById(pinId);

        Intent intent = new Intent(this, StartActivity.class);
        intent.setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra("pin_id", pinId);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, random.nextInt(), intent, PendingIntent.FLAG_ONE_SHOT);

        DateFormat time = SimpleDateFormat.getTimeInstance(DateFormat.SHORT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_notify_alert)
                .setContentTitle(time.format(new Date()) + " - " + getString(R.string.thread_page_limit))
                .setContentText(pin.loadable.title)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setLights(Color.RED, 1000, 1000)
                .setAutoCancel(true);

        return builder.build();
    }
}
