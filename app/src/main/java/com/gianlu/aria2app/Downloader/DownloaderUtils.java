package com.gianlu.aria2app.Downloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Environment;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import com.gianlu.aria2app.PKeys;
import com.gianlu.commonutils.Prefs;

import java.io.File;

public class DownloaderUtils {
    public static final String ACTION_LIST_DOWNLOADS = "com.gianlu.aria2app.dd.LIST_DOWNLOADS";
    public static final String ACTION_COUNT_CHANGED = "com.gianlu.aria2app.dd.COUNT_CHANGED";
    final static int START_DOWNLOAD = 0;
    final static int LIST_DOWNLOADS = 1;

    public static void bindService(Context context, ServiceConnection conn) {
        context.getApplicationContext().bindService(new Intent(context, DownloaderService.class), conn, 0);
    }

    public static void addDownload(@NonNull Messenger messenger, DownloadStartConfig config) throws RemoteException {
        messenger.send(Message.obtain(null, START_DOWNLOAD, 0, 0, config));
    }

    public static void listDownloads(@NonNull Messenger messenger) {
        try {
            messenger.send(Message.obtain(null, LIST_DOWNLOADS, 0, 0, null));
        } catch (RemoteException ignored) {
        }
    }

    public static File getAndValidateDownloadPath(Context context) throws InvalidPathException {
        File path = new File(Prefs.getString(context, PKeys.DD_DOWNLOAD_PATH, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()));
        if (!path.exists()) {
            if (!path.mkdirs()) throw new InvalidPathException();
        }

        if (!path.canWrite()) throw new InvalidPathException();

        return path;
    }

    public static void startService(Context context) {
        context.startService(new Intent(context, DownloaderService.class));
    }

    public static void registerReceiver(Context context, BroadcastReceiver receiver) {
        IntentFilter filter = new IntentFilter(ACTION_LIST_DOWNLOADS);
        LocalBroadcastManager.getInstance(context.getApplicationContext()).registerReceiver(receiver, filter);
    }

    public static class InvalidPathException extends Exception {
    }
}
