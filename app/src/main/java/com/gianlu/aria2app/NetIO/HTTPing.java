package com.gianlu.aria2app.NetIO;

import android.content.Context;
import android.support.annotation.NonNull;

import com.gianlu.aria2app.NetIO.JTA2.AriaException;
import com.gianlu.aria2app.ProfilesManager.MultiProfile;
import com.gianlu.aria2app.ProfilesManager.ProfilesManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public class HTTPing extends AbstractClient {
    private static HTTPing httping;
    private final ExecutorService executorService;
    private final OkHttpClient client;
    private final HttpUrl defaultUri;
    private boolean shouldIgnoreRequests = false;

    private HTTPing(Context context) throws CertificateException, IOException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException, NetUtils.InvalidUrlException, ProfilesManager.NoCurrentProfileException {
        this(context, ProfilesManager.get(context).getCurrent(context).getProfile(context));
    }

    private HTTPing(Context context, MultiProfile.UserProfile profile) throws CertificateException, IOException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException, NetUtils.InvalidUrlException {
        super(context, profile);
        ErrorHandler.get().unlock();
        this.executorService = Executors.newCachedThreadPool();
        this.client = NetUtils.buildHttpClient(profile, sslContext);
        this.defaultUri = NetUtils.createBaseHttpURI(profile);
    }

    public static HTTPing instantiate(Context context) throws InitializationException {
        if (httping == null) {
            try {
                httping = new HTTPing(context);
            } catch (CertificateException | IOException | NoSuchAlgorithmException | KeyManagementException | NetUtils.InvalidUrlException | KeyStoreException | ProfilesManager.NoCurrentProfileException ex) {
                throw new InitializationException(ex);
            }
        }

        return httping;
    }

    public static void instantiate(Context context, MultiProfile.UserProfile profile, @NonNull final IConnect listener) {
        try {
            httping = new HTTPing(context, profile);
            httping.sendConnectionTest(new IReceived() {
                @Override
                public void onResponse(JSONObject response) throws JSONException {
                    listener.onConnected(httping);
                }

                @Override
                public void onException(Exception ex) {
                    listener.onFailedConnecting(ex);
                }
            });
        } catch (CertificateException | IOException | KeyManagementException | NoSuchAlgorithmException | NetUtils.InvalidUrlException | KeyStoreException ex) {
            listener.onFailedConnecting(ex);
        }
    }

    public static void clear() {
        clearConnectivityListener();
        if (httping != null) httping.clearInternal();
    }

    @Override
    protected void clearInternal() {
        shouldIgnoreRequests = true;
        executorService.shutdownNow();
    }

    @Override
    public void send(@NonNull JSONObject request, IReceived handler) {
        if (!shouldIgnoreRequests && !executorService.isShutdown() && !executorService.isTerminated())
            executorService.execute(new RequestProcessor(request, handler));
    }

    private void sendConnectionTest(IReceived handler) {
        if (!shouldIgnoreRequests && !executorService.isShutdown() && !executorService.isTerminated())
            executorService.execute(new RequestProcessor(null, handler));
    }

    @Override
    public void connectivityChanged(@NonNull Context context, @NonNull MultiProfile.UserProfile profile) throws Exception {
        httping = new HTTPing(context, profile);
    }

    private class RequestProcessor implements Runnable {
        private final JSONObject request;
        private final IReceived listener;

        RequestProcessor(JSONObject request, IReceived listener) {
            this.request = request;
            this.listener = listener;
        }

        @Override
        public void run() {
            try {
                Request req;
                if (request == null) req = NetUtils.createGetRequest(profile, defaultUri, null);
                else req = NetUtils.createPostRequest(profile, defaultUri, request);
                try (Response resp = client.newCall(req).execute()) {
                    if (request == null) { // Connection test
                        if (resp.code() == 400)
                            listener.onResponse(null);
                        else
                            listener.onException(new StatusCodeException(resp));
                    } else {
                        ResponseBody body = resp.body();
                        if (body != null) {
                            String json = body.string();
                            if (json == null || json.isEmpty()) {
                                listener.onException(new NullPointerException("Empty response"));
                            } else {
                                JSONObject obj = new JSONObject(json);
                                if (obj.has("error")) {
                                    listener.onException(new AriaException(obj.getJSONObject("error")));
                                } else {
                                    listener.onResponse(obj);
                                }
                            }
                        } else {
                            listener.onException(new StatusCodeException(resp));
                        }
                    }
                }
            } catch (IllegalArgumentException | JSONException | IOException | NetUtils.InvalidUrlException | IllegalStateException ex) {
                listener.onException(ex);
            }
        }
    }
}
