package com.gianlu.aria2app.Downloader;

import android.content.Context;
import android.support.annotation.Nullable;

import com.gianlu.commonutils.CommonUtils;
import com.gianlu.commonutils.Logging;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class SavedDownloadsManager {
    private static SavedDownloadsManager instance;
    private final File storeFile;
    private List<SavedState> savedStates;

    private SavedDownloadsManager(Context context) {
        storeFile = new File(context.getFilesDir(), "savedDownloads.json");

        load(context);
    }

    public static SavedDownloadsManager get(Context context) {
        if (instance == null) instance = new SavedDownloadsManager(context);
        return instance;
    }

    private void load(Context context) {
        try {
            if (!storeFile.exists()) //noinspection ResultOfMethodCallIgnored
                storeFile.createNewFile();

            try (FileInputStream in = new FileInputStream(storeFile)) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line = reader.readLine();
                if (line == null || line.isEmpty()) line = "[]";
                savedStates = CommonUtils.toTList(new JSONArray(line), SavedState.class);
            }
        } catch (IOException | JSONException ex) {
            Logging.logMe(context, ex);
            savedStates = new ArrayList<>();
        }
    }

    private void save(Context context) {
        try (FileOutputStream out = new FileOutputStream(storeFile, false)) {
            JSONArray array = new JSONArray();
            for (SavedState state : savedStates) array.put(state.toJSON());
            out.write(array.toString().getBytes());
            out.flush();
        } catch (IOException | JSONException ex) {
            Logging.logMe(context, ex);
        }
    }

    public void saveState(Context context, int id, URI uri, File tempFile, File destFile, String profileId) {
        if (savedStates == null) load(context);

        for (SavedState state : savedStates)
            if (state.id == id)
                return;

        savedStates.add(new SavedState(id, uri.getPath(), tempFile, profileId, destFile.getName()));
        save(context);
    }

    public List<SavedState> getAll() {
        return new ArrayList<>(savedStates);
    }

    @Nullable
    public SavedState getSavedState(int id) {
        for (SavedState state : savedStates)
            if (state.id == id)
                return state;

        return null;
    }

    public void removeState(Context context, int id) {
        if (savedStates == null) load(context);

        ListIterator<SavedState> iterator = savedStates.listIterator();
        while (iterator.hasNext())
            if (iterator.next().id == id)
                iterator.remove();

        save(context);
    }

    public static class SavedState {
        public final int id;
        public final String path;
        public final String profileId;
        public final File tempFile;
        public final String fileName;

        @SuppressWarnings("unused")
        public SavedState(JSONObject obj) throws JSONException, URISyntaxException {
            id = obj.getInt("id");
            path = obj.getString("path");
            tempFile = new File(obj.getString("tempFile"));
            profileId = obj.getString("profileId");
            fileName = obj.getString("fileName");
        }

        private SavedState(int id, String path, File tempFile, String profileId, String fileName) {
            this.id = id;
            this.path = path;
            this.profileId = profileId;
            this.tempFile = tempFile;
            this.fileName = fileName;
        }

        private JSONObject toJSON() throws JSONException {
            return new JSONObject()
                    .put("profileId", profileId)
                    .put("id", id)
                    .put("path", path)
                    .put("fileName", fileName)
                    .put("tempFile", tempFile.getAbsolutePath());
        }
    }
}
