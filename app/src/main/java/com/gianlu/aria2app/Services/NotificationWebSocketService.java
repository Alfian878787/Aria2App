package com.gianlu.aria2app.Services;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.NotificationCompat;

import com.gianlu.aria2app.Main.Profile.MultiModeProfileItem;
import com.gianlu.aria2app.Main.Profile.ProfileItem;
import com.gianlu.aria2app.Main.Profile.SingleModeProfileItem;
import com.gianlu.aria2app.MainActivity;
import com.gianlu.aria2app.R;
import com.gianlu.aria2app.Utils;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class NotificationWebSocketService extends IntentService {
    public NotificationWebSocketService() {
        super("NotificationWebSocketService");
    }

    @Nullable
    public static Intent createStartIntent(Context context, String profileFileName) {
        if (profileFileName == null) return null;

        SingleModeProfileItem profile;
        try {
            if (ProfileItem.isSingleMode(context, profileFileName)) {
                profile = SingleModeProfileItem.fromString(context, profileFileName);
            } else {
                profile = MultiModeProfileItem.fromString(context, profileFileName).getCurrentProfile(context);
            }
        } catch (IOException | JSONException ex) {
            ex.printStackTrace();
            return null;
        }

        Intent intent = new Intent(context, NotificationWebSocketService.class);
        intent.putExtra("profileName", profile.getGlobalProfileName());
        intent.putExtra("foreground", PreferenceManager.getDefaultSharedPreferences(context).getBoolean("a2_enablePersistent", true));
        intent.putExtra("SSL", profile.isServerSSL());
        intent.putExtra("serverIP", profile.getFullServerAddr());

        return intent;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
        }

        if (intent != null && intent.getBooleanExtra("foreground", true) && !Objects.equals(intent.getAction(), "STOP")) {
            Notification.Builder builder = new Notification.Builder(this);
            builder.setShowWhen(false)
                    .setPriority(Notification.PRIORITY_MIN)
                    .setContentTitle("Notification service")
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentText(intent.getStringExtra("profileName"))
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setSmallIcon(R.drawable.ic_notification)
                    .addAction(new Notification.Action(R.drawable.ic_clear_black_48dp,
                            getApplicationContext().getString(R.string.stopNotificationService),
                            PendingIntent.getService(getApplicationContext(), 0,
                                    new Intent(getApplicationContext(),
                                            NotificationWebSocketService.class).setAction("STOP"), 0)))
                    .setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorAccent))
                    .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));

            startForeground(startId, builder.build());
        }

        onHandleIntent(intent);
        return START_STICKY;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null || Objects.equals(intent.getAction(), "STOP")) {
            stopSelf();
            return;
        }

        WebSocket webSocket;
        try {
            webSocket = Utils.readyWebSocket(intent.getBooleanExtra("SSL", false), intent.getStringExtra("serverIP"));
        } catch (IOException | NoSuchAlgorithmException ex) {
            ex.printStackTrace();
            stopSelf();
            return;
        }

        webSocket.addListener(new NotificationAdapter())
                .connectAsynchronously();
    }

    private enum EVENT {
        START,
        PAUSE,
        STOP,
        COMPLETE,
        ERROR,
        BTCOMPLETE,
        UNKNOWN;

        private static EVENT parseEvent(String event) {
            switch (event.replace("aria2.", "")) {
                case "onDownloadStart":
                    return EVENT.START;
                case "onDownloadPause":
                    return EVENT.PAUSE;
                case "onDownloadStop":
                    return EVENT.STOP;
                case "onDownloadComplete":
                    return EVENT.COMPLETE;
                case "onDownloadError":
                    return EVENT.ERROR;
                case "onBtDownloadComplete":
                    return EVENT.BTCOMPLETE;
                default:
                    return EVENT.UNKNOWN;
            }
        }
    }

    private class NotificationAdapter extends WebSocketAdapter {
        @Override
        public void onTextMessage(WebSocket websocket, String text) throws Exception {
            JSONObject jResponse = new JSONObject(text);

            for (int c = 0; c < jResponse.getJSONArray("params").length(); c++) {
                String gid = jResponse.getJSONArray("params").getJSONObject(c).getString("gid");
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                Set<String> selectedNotifications = sharedPreferences.getStringSet("a2_selectedNotifications", null);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
                builder.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(), MainActivity.class).putExtra("gid", gid), PendingIntent.FLAG_UPDATE_CURRENT))
                        .setContentText("GID: " + gid)
                        .setGroup(gid)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorAccent))
                        .setAutoCancel(true);
                if (sharedPreferences.getBoolean("a2_enableSound", true))
                    builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

                if (selectedNotifications == null) return;

                switch (EVENT.parseEvent(jResponse.getString("method"))) {
                    case START:
                        if (!selectedNotifications.contains("START")) return;
                        builder.setContentTitle(getString(R.string.notificationStarted));
                        break;
                    case STOP:
                        if (!selectedNotifications.contains("STOP")) return;
                        builder.setContentTitle(getString(R.string.notificationStopped));
                        break;
                    case PAUSE:
                        if (!selectedNotifications.contains("PAUSE")) return;
                        builder.setContentTitle(getString(R.string.notificationPaused));
                        break;
                    case COMPLETE:
                        if (!selectedNotifications.contains("COMPLETE")) return;
                        builder.setContentTitle(getString(R.string.notificationComplete));
                        break;
                    case BTCOMPLETE:
                        if (!selectedNotifications.contains("BTCOMPLETE")) return;
                        builder.setContentTitle(getString(R.string.notificationBTComplete));
                        break;
                    case ERROR:
                        if (!selectedNotifications.contains("ERROR")) return;
                        builder.setContentTitle(getString(R.string.notificationError));
                        break;
                }

                NotificationManagerCompat manager = NotificationManagerCompat.from(getApplicationContext());
                manager.notify(new Random().nextInt(100), builder.build());
            }
        }
    }
}
