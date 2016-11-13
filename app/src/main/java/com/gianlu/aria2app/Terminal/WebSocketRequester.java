package com.gianlu.aria2app.Terminal;

import android.content.Context;

import com.gianlu.aria2app.Utils;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class WebSocketRequester {
    private static WebSocketRequester instance;
    private WebSocket socket;

    private WebSocketRequester(Context context, WebSocketAdapter adapter) throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException, KeyManagementException {
        socket = Utils.readyWebSocket(context)
                .addListener(adapter)
                .connectAsynchronously();
    }

    public static JSONObject formatRequest(String id, String jsonrpc, String method, String params) throws JSONException {
        return new JSONObject().put("id", id)
                .put("jsonrpc", jsonrpc)
                .put("method", method)
                .put("params", new JSONArray("[" + params + "]"));
    }

    static WebSocketRequester getInstance(Context context, WebSocketAdapter adapter) throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException, KeyManagementException {
        if (instance == null)
            instance = new WebSocketRequester(context, adapter);
        return instance;
    }

    public static WebSocketRequester getInstance(Context context) throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException, KeyManagementException {
        return getInstance(context, null);
    }

    public static void destroy() {
        if (instance == null)
            return;

        instance.socket.disconnect();
        instance = null;
    }

    private String request(JSONObject obj) {
        return request(obj.toString());
    }

    public String request(String req) {
        socket.sendText(req);
        return req;
    }

    public String request(String id, String jsonrpc, String method, String params) throws JSONException {
        return request(formatRequest(id, jsonrpc, method, params));
    }
}
