package com.gianlu.aria2app.NetIO;

import android.content.Context;
import android.util.Pair;

import com.gianlu.aria2app.NetIO.JTA2.Aria2Exception;
import com.gianlu.aria2app.ProfilesManager.MultiProfile;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketState;

import org.json.JSONObject;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketing extends AbstractClient {
    private static WebSocketing webSocketing;
    private static boolean locked = false;
    private final Map<Integer, IReceived> requests = new ConcurrentHashMap<>();
    private final List<Pair<JSONObject, IReceived>> connectionQueue = new ArrayList<>();
    private IConnect connectionListener;
    private WebSocket socket;

    private WebSocketing(Context context) throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException, KeyManagementException {
        socket = NetUtils.readyWebSocket(context).addListener(new Adapter()).connectAsynchronously();
    }

    public WebSocketing(Context context, IConnect listener) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        this(context);
        this.connectionListener = listener;
    }

    public WebSocketing(Context context, MultiProfile.UserProfile profile, IConnect listener) throws CertificateException, IOException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
        socket = NetUtils.readyWebSocket(profile.buildWebSocketUrl(), profile.serverUsername, profile.serverPassword, NetUtils.readyCertificate(context, profile));
        socket.addListener(new Adapter()).connectAsynchronously();
        this.connectionListener = listener;
    }

    public static WebSocketing instantiate(Context context) throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException, KeyManagementException {
        if (webSocketing == null) webSocketing = new WebSocketing(context);
        return webSocketing;
    }

    public static void instantiate(Context context, IConnect listener) {
        try {
            webSocketing = new WebSocketing(context, listener);
        } catch (CertificateException | NoSuchAlgorithmException | KeyManagementException | KeyStoreException | IOException ex) {
            listener.onFailedConnecting(ex);
        }
    }

    public static void clear() {
        locked = true;
        if (webSocketing == null) return;
        if (webSocketing.socket != null) webSocketing.socket.disconnect();
        if (webSocketing.requests != null) webSocketing.requests.clear();
        if (webSocketing.connectionQueue != null) webSocketing.connectionQueue.clear();
        webSocketing = null;
    }

    public static void unlock() {
        locked = false;
    }

    @Override
    public void send(JSONObject request, IReceived handler) {
        if (locked) return;

        if (requests.size() > 10) {
            synchronized (requests) {
                requests.clear();
            }

            System.gc();
        }

        if (connectionQueue.size() > 10) {
            connectionQueue.clear();
            System.gc();
        }

        if (socket.getState() != WebSocketState.OPEN) {
            connectionQueue.add(new Pair<>(request, handler));
            return;
        }

        try {
            requests.put(request.getInt("id"), handler);
            socket.sendText(request.toString());
        } catch (OutOfMemoryError ex) {
            System.gc();
        } catch (Exception ex) {
            handler.onException(ex);
        }
    }

    private void processQueue() {
        for (Pair<JSONObject, IReceived> pair : connectionQueue) send(pair.first, pair.second);
    }

    private class Adapter extends WebSocketAdapter {
        @Override
        public void onTextMessage(WebSocket websocket, String text) throws Exception {
            if (locked) return;

            JSONObject response = new JSONObject(text);

            String method = response.optString("method", null);
            if (method != null && method.startsWith("aria2.on")) return;

            IReceived handler = requests.remove(response.getInt("id"));
            if (handler == null) return;
            if (response.isNull("error")) {
                handler.onResponse(response);
            } else {
                handler.onException(new Aria2Exception(response.getJSONObject("error").getString("message"), response.getJSONObject("error").getInt("code")));
            }
        }

        @Override
        public void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {
            if (locked) return;
            ErrorHandler.get().notifyException(cause, false);
        }

        @Override
        public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
            unlock();
            if (connectionListener != null) {
                connectionListener.onConnected(WebSocketing.this);
                connectionListener = null;
            }
        }

        @Override
        public void onConnectError(WebSocket websocket, WebSocketException exception) throws Exception {
            if (connectionListener != null) {
                connectionListener.onFailedConnecting(exception);
                connectionListener = null;
            }
        }

        @Override
        public void onStateChanged(WebSocket websocket, WebSocketState newState) throws Exception {
            if (locked) return;
            if (newState.equals(WebSocketState.OPEN) && connectionQueue.size() > 0) processQueue();
        }
    }
}
