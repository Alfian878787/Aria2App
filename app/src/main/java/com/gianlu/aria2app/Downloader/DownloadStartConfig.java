package com.gianlu.aria2app.Downloader;

import android.content.Context;
import android.support.annotation.Nullable;

import com.gianlu.aria2app.NetIO.JTA2.ADir;
import com.gianlu.aria2app.NetIO.JTA2.AFile;
import com.gianlu.aria2app.NetIO.JTA2.Download;
import com.gianlu.aria2app.ProfilesManager.MultiProfile;
import com.gianlu.aria2app.ProfilesManager.ProfilesManager;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cz.msebera.android.httpclient.client.utils.URIBuilder;

public class DownloadStartConfig {
    private static final Random random = new Random();
    public final List<Task> tasks;
    public final File cacheDir;
    public final String profileId;

    private DownloadStartConfig(Context context, String profileId) {
        this.profileId = profileId;
        this.tasks = new ArrayList<>();
        this.cacheDir = context.getExternalCacheDir();
    }

    public static DownloadStartConfig create(Context context, Download download, MultiProfile.UserProfile profile, AFile file) throws DownloaderUtils.InvalidPathException, URISyntaxException {
        if (profile.directDownload == null) throw new IllegalArgumentException("WTF?!");

        File downloadPath = DownloaderUtils.getAndValidateDownloadPath(context);
        File destFile = new File(downloadPath, file.getName());

        MultiProfile.DirectDownload dd = profile.directDownload;
        URIBuilder builder = new URIBuilder(dd.getURLAddress());
        builder.setPath(file.getRelativePath(download.dir));

        DownloadStartConfig config = new DownloadStartConfig(context, profile.getParent().id);
        config.addTask(builder.build(), destFile, dd.username, dd.password);
        return config;
    }

    public static DownloadStartConfig createForSavedState(Context context, SavedDownloadsManager.SavedState state) throws CannotCreateStartConfigException, DownloaderUtils.InvalidPathException, URISyntaxException {
        ProfilesManager manager = ProfilesManager.get(context);

        MultiProfile.UserProfile profile;
        if (manager.profileExists(state.profileId)) {
            try {
                profile = manager.retrieveProfile(state.profileId).getProfile(context);
            } catch (JSONException | IOException ex) {
                throw new CannotCreateStartConfigException(ex);
            }
        } else {
            throw new CannotCreateStartConfigException(CannotCreateStartConfigException.Cause.PROFILE_DOES_NOT_EXIST);
        }

        if (profile.directDownload == null)
            throw new CannotCreateStartConfigException(CannotCreateStartConfigException.Cause.DD_NOT_ENABLED);

        File downloadPath = DownloaderUtils.getAndValidateDownloadPath(context);
        File destFile = new File(downloadPath, state.fileName);

        MultiProfile.DirectDownload dd = profile.directDownload;
        URIBuilder builder = new URIBuilder(dd.getURLAddress());
        builder.setPath(state.path);

        DownloadStartConfig config = new DownloadStartConfig(context, profile.getParent().id);
        config.addResumableTask(state.id, builder.build(), destFile, dd.username, dd.password);
        return config;
    }

    public static DownloadStartConfig create(Download download, MultiProfile.UserProfile profile, ADir dir, boolean includeSubdirectories) { // TODO
        throw new UnsupportedOperationException();
    }

    private void addTask(URI uri, File destFile, String username, String password) {
        tasks.add(new Task(uri, destFile, username, password));
    }

    private void addResumableTask(int id, URI uri, File destFile, String username, String password) {
        tasks.add(new Task(id, uri, destFile, username, password, true));
    }

    public static class CannotCreateStartConfigException extends Exception {
        public final Cause cause;

        public CannotCreateStartConfigException(Exception ex) {
            super(ex);
            this.cause = Cause.INTERNAL;
        }

        public CannotCreateStartConfigException(Cause cause) {
            this.cause = cause;
        }

        public enum Cause {
            INTERNAL,
            DD_NOT_ENABLED,
            PROFILE_DOES_NOT_EXIST
        }
    }

    public class Task implements Serializable {
        public final URI uri;
        public final File destFile;
        public final int id;
        public final String username;
        public final String password;
        public final boolean resumable;

        public Task(int id, URI uri, File destFile, @Nullable String username, @Nullable String password, boolean resumable) {
            this.uri = uri;
            this.destFile = destFile;
            this.username = username;
            this.password = password;
            this.id = id;
            this.resumable = resumable;
        }

        public Task(URI uri, File destFile, @Nullable String username, @Nullable String password) {
            this(random.nextInt(), uri, destFile, username, password, false);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Task task = (Task) o;
            return id == task.id;
        }

        public String getProfileId() {
            return profileId;
        }

        public String getName() {
            return destFile.getName();
        }

        public boolean hasAuth() {
            return username != null && password != null;
        }

        public File getCacheDir() {
            return cacheDir;
        }
    }
}