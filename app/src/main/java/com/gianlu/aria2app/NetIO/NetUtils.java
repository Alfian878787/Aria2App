package com.gianlu.aria2app.NetIO;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;

import com.gianlu.aria2app.NetIO.JTA2.JTA2;
import com.gianlu.aria2app.ProfilesManager.MultiProfile;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

import cz.msebera.android.httpclient.client.config.RequestConfig;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.utils.URIBuilder;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.HttpClients;

public class NetUtils {

    public static void validateConnection(MultiProfile.ConnectionMethod connectionMethod, String address, int port, String endpoint, boolean encryption) throws URISyntaxException {
        URIBuilder builder = new URIBuilder();
        builder.setHost(address)
                .setPort(port)
                .setPath(endpoint);

        if (connectionMethod == MultiProfile.ConnectionMethod.HTTP) {
            builder.setScheme(encryption ? "https" : "http");
        } else {
            builder.setScheme(encryption ? "wss" : "ws");
        }

        builder.build();
    }

    @NonNull
    static SSLContext createSSLContext(@Nullable Certificate ca) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, KeyManagementException {
        if (ca == null) return SSLContext.getDefault();

        String keyStoreType = KeyStore.getDefaultType();
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", ca);

        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
        tmf.init(keyStore);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);

        return context;
    }

    public static WebSocket readyWebSocket(MultiProfile.UserProfile profile) throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException, KeyManagementException, URISyntaxException {
        WebSocketFactory factory = new WebSocketFactory();
        factory.setConnectionTimeout(5000);
        factory.setVerifyHostname(profile.hostnameVerifier);
        if (profile.serverSSL)
            factory.setSSLContext(createSSLContext(profile.certificate));

        try {
            WebSocket socket = factory.createSocket(createBaseWsURI(profile), 5000);
            socket.setFrameQueueSize(15);

            if (profile.authMethod == JTA2.AuthMethod.HTTP)
                socket.addHeader("Authorization", "Basic " + Base64.encodeToString((profile.serverUsername + ":" + profile.serverPassword).getBytes(), Base64.NO_WRAP));

            return socket;
        } catch (IllegalArgumentException ex) {
            throw new IOException("Just a wrapper", ex);
        }
    }

    public static CloseableHttpClient buildHttpClient(MultiProfile.UserProfile profile) throws CertificateException, IOException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
        return buildHttpClient(profile, createSSLContext(profile.certificate));
    }

    static CloseableHttpClient buildHttpClient(MultiProfile.UserProfile profile, SSLContext sslContext) {
        HttpClientBuilder builder = HttpClients.custom()
                .setUserAgent("Aria2App")
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(5000)
                        .setSocketTimeout(5000)
                        .setConnectionRequestTimeout(5000)
                        .build())
                .setSslcontext(sslContext);

        if (!profile.hostnameVerifier) {
            builder.setSSLHostnameVerifier(new HostnameVerifier() {
                @SuppressLint("BadHostnameVerifier")
                @Override
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            });
        }

        return builder.build();
    }

    @NonNull
    public static URI createBaseHttpURI(MultiProfile.UserProfile profile) throws URISyntaxException {
        return new URI(profile.serverSSL ? "https" : "http", null, profile.serverAddr, profile.serverPort, profile.serverEndpoint, null, null);
    }

    @NonNull
    public static URI createBaseWsURI(MultiProfile.UserProfile profile) throws URISyntaxException {
        return new URI(profile.serverSSL ? "wss" : "ws", null, profile.serverAddr, profile.serverPort, profile.serverEndpoint, null, null);
    }

    public static HttpGet createGetRequest(MultiProfile.UserProfile profile, @Nullable URI defaultUri, @Nullable JSONObject request) throws URISyntaxException, JSONException {
        if (defaultUri == null) defaultUri = createBaseHttpURI(profile);
        URIBuilder builder = new URIBuilder(defaultUri);
        if (request != null) {
            builder.addParameter("method", request.getString("method"))
                    .addParameter("id", request.getString("id"));

            if (request.has("params"))
                builder.addParameter("params", Base64.encodeToString(request.get("params").toString().getBytes(), Base64.NO_WRAP));
        }

        HttpGet get = new HttpGet(builder.build());
        if (profile.authMethod == JTA2.AuthMethod.HTTP)
            get.addHeader("Authorization", "Basic " + profile.getEncodedCredentials());

        return get;
    }

    static HttpPost createPostRequest(MultiProfile.UserProfile profile, @Nullable URI defaultUri, @Nullable JSONObject request) throws URISyntaxException {
        if (defaultUri == null) defaultUri = createBaseHttpURI(profile);
        HttpPost post = new HttpPost(defaultUri);

        if (request != null)
            post.setEntity(new StringEntity(request.toString(), Charset.forName("UTF-8")));

        if (profile.authMethod == JTA2.AuthMethod.HTTP)
            post.addHeader("Authorization", "Basic " + profile.getEncodedCredentials());

        return post;
    }
}
