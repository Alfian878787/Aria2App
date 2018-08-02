package com.gianlu.aria2app.NetIO.Search;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.Base64;

import com.gianlu.aria2app.NetIO.StatusCodeException;
import com.gianlu.aria2app.PK;
import com.gianlu.commonutils.CommonUtils;
import com.gianlu.commonutils.Logging;
import com.gianlu.commonutils.Preferences.Prefs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class SearchApi {
    public static final int RESULTS_PER_REQUEST = 20;
    private static final int TIMEOUT = 15;
    private static final HttpUrl BASE_URL = HttpUrl.parse("http://api.tse.gianlu.xyz/");
    private static SearchApi instance;
    private final OkHttpClient client;
    private final ExecutorService executorService;
    private final Handler handler;
    private final SharedPreferences preferences;
    private List<SearchEngine> cachedEngines = null;

    private SearchApi(Context context) {
        client = new OkHttpClient.Builder().connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .build();
        executorService = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @NonNull
    public static SearchApi get(@NonNull Context context) {
        if (instance == null) instance = new SearchApi(context);
        return instance;
    }

    @NonNull
    private String request(@NonNull Request get) throws IOException, StatusCodeException {
        try (Response resp = client.newCall(get).execute()) {
            if (resp.code() != 200) throw new StatusCodeException(resp);

            ResponseBody body = resp.body();
            if (body == null) throw new IOException("Empty body!");

            return body.string();
        }
    }

    public void search(final String token, final int maxResults, final OnSearch listener) {
        search(null, token, maxResults, null, listener);
    }

    private void search(@Nullable String query, @Nullable String token, int maxResults, @Nullable Collection<String> engines, final OnSearch listener) {
        final HttpUrl.Builder builder = BASE_URL.newBuilder().
                addPathSegment("search")
                .addQueryParameter("m", String.valueOf(maxResults));

        if (token != null) {
            builder.addQueryParameter("t", token);
        } else {
            builder.addQueryParameter("q", query);
            if (engines != null)
                for (String engineId : engines)
                    builder.addQueryParameter("e", engineId);
        }

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject obj = new JSONObject(request(new Request.Builder().get().url(builder.build()).build()));
                    final List<SearchResult> results = SearchResult.list(obj.getJSONArray("result"));

                    cacheEnginesBlocking();
                    JSONArray missingEnginesArray = obj.getJSONArray("missing");
                    final List<MissingSearchEngine> missingEngines = new ArrayList<>();
                    for (int i = 0; i < missingEnginesArray.length(); i++)
                        missingEngines.add(new MissingSearchEngine(SearchApi.this, missingEnginesArray.getJSONObject(i)));

                    final String token = obj.optString("token", null);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onResult(results, missingEngines, token);
                        }
                    });
                } catch (IOException | StatusCodeException | JSONException ex) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onException(ex);
                        }
                    });
                }
            }
        });
    }

    public void search(String query, int maxResults, @Nullable Collection<String> engines, OnSearch listener) {
        search(query, null, maxResults, engines, listener);
    }

    public void getTorrent(SearchResult result, final OnResult<Torrent> listener) {
        final HttpUrl.Builder builder = BASE_URL.newBuilder();
        builder.addPathSegment("getTorrent")
                .addQueryParameter("e", result.engineId)
                .addQueryParameter("url", Base64.encodeToString(result.url.getBytes(), Base64.NO_WRAP | Base64.URL_SAFE));

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject obj = new JSONObject(request(new Request.Builder().get().url(builder.build()).build()));
                    final Torrent torrent = new Torrent(obj);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onResult(torrent);
                        }
                    });
                } catch (IOException | StatusCodeException | JSONException ex) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onException(ex);
                        }
                    });
                }
            }
        });
    }

    @Nullable
    public SearchEngine findEngine(String id) {
        if (cachedEngines == null) return null;
        for (SearchEngine engine : cachedEngines)
            if (Objects.equals(engine.id, id))
                return engine;

        return null;
    }

    public void listSearchEngines(final OnResult<List<SearchEngine>> listener) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<SearchEngine> engines = listSearchEnginesSync();

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onResult(engines);
                        }
                    });
                } catch (IOException | StatusCodeException | JSONException ex) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onException(ex);
                        }
                    });
                }
            }
        });
    }

    @NonNull
    private List<SearchEngine> listSearchEnginesSync() throws JSONException, IOException, StatusCodeException {
        if (Prefs.has(preferences, PK.SEARCH_ENGINES_CACHE) && !CommonUtils.isDebug()) {
            long age = Prefs.getLong(preferences, PK.SEARCH_ENGINES_CACHE_AGE, 0);
            if (System.currentTimeMillis() - age < TimeUnit.HOURS.toMillis(6)) {
                JSONArray array = Prefs.getJSONArray(preferences, PK.SEARCH_ENGINES_CACHE, new JSONArray());
                if (array.length() > 0) return cachedEngines = SearchEngine.list(array);
            }
        }

        JSONArray array = new JSONArray(request(new Request.Builder().get().url(BASE_URL.newBuilder().addPathSegment("listEngines").build()).build()));
        Prefs.putLong(preferences, PK.SEARCH_ENGINES_CACHE_AGE, System.currentTimeMillis());
        Prefs.putJSONArray(preferences, PK.SEARCH_ENGINES_CACHE, array);
        return cachedEngines = SearchEngine.list(array);
    }

    private void cacheEnginesBlocking() throws IOException, StatusCodeException, JSONException {
        if (cachedEngines == null) listSearchEnginesSync();
    }

    public void cacheSearchEngines() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    listSearchEnginesSync();
                } catch (IOException | StatusCodeException | JSONException ex) {
                    Logging.log(ex);
                }
            }
        });
    }

    @UiThread
    public interface OnSearch {
        void onResult(List<SearchResult> results, List<MissingSearchEngine> missingEngines, @Nullable String nextPageToken);

        void onException(@NonNull Exception ex);
    }

    @UiThread
    public interface OnResult<E> {
        void onResult(@NonNull E result);

        void onException(@NonNull Exception ex);
    }
}
