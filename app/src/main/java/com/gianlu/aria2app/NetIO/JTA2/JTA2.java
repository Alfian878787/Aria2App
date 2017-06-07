package com.gianlu.aria2app.NetIO.JTA2;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.SparseArray;

import com.gianlu.aria2app.NetIO.AbstractClient;
import com.gianlu.aria2app.NetIO.HTTPing;
import com.gianlu.aria2app.NetIO.IReceived;
import com.gianlu.aria2app.NetIO.WebSocketing;
import com.gianlu.aria2app.Prefs;
import com.gianlu.aria2app.ProfilesManager.ProfilesManager;
import com.gianlu.aria2app.ProfilesManager.UserProfile;
import com.gianlu.aria2app.Utils;
import com.gianlu.commonutils.CommonUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JTA2 {
    private final AbstractClient client;
    private final Context context;
    private final boolean forceAction;

    private JTA2(Context context, WebSocketing client) {
        this.context = context;
        this.client = client;
        this.forceAction = Prefs.getBoolean(context, Prefs.Keys.A2_FORCE_ACTION, true);
    }

    private JTA2(Context context, HTTPing client) {
        this.context = context;
        this.client = client;
        this.forceAction = Prefs.getBoolean(context, Prefs.Keys.A2_FORCE_ACTION, true);
    }

    public static JTA2 instantiate(Context context) throws JTA2InitializingException {
        try {
            if (ProfilesManager.get(context).getCurrentAssert().connectionMethod == UserProfile.ConnectionMethod.WEBSOCKET)
                return new JTA2(context, WebSocketing.instantiate(context));
            else
                return new JTA2(context, HTTPing.newInstance(context));
        } catch (IOException | NoSuchAlgorithmException | KeyStoreException | CertificateException | KeyManagementException ex) {
            throw new JTA2InitializingException(ex);
        }
    }

    @NonNull
    private static List<String> parseRawFeatures(JSONArray raw) throws JSONException {
        if (raw == null) return new ArrayList<>();
        List<String> features = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) features.add(raw.getString(i));
        return features;
    }

    @NonNull
    private static Map<String, String> parseOptions(JSONObject jResult) {
        if (jResult == null) return new HashMap<>();
        Iterator<String> keys = jResult.keys();
        Map<String, String> options = new HashMap<>();
        while (keys.hasNext()) {
            String key = keys.next();
            options.put(key, jResult.optString(key));
        }
        return options;
    }

    @NonNull
    private static List<String> parseMethods(JSONArray array) throws JSONException {
        if (array == null) return new ArrayList<>();
        List<String> methods = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) methods.add(array.getString(i));
        return methods;
    }

    @NonNull
    private static List<Peer> parsePeers(JSONArray array) throws JSONException {
        if (array == null) return new ArrayList<>();
        List<Peer> peers = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) peers.add(new Peer(array.getJSONObject(i)));
        return peers;
    }

    @NonNull
    private static List<AFile> parseAFiles(JSONArray array) throws JSONException {
        if (array == null) return new ArrayList<>();
        List<AFile> files = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) files.add(new AFile(array.getJSONObject(i)));
        return files;
    }

    @NonNull
    private static SparseArray<List<Server>> parseServers(JSONArray array) throws JSONException {
        if (array == null) return new SparseArray<>();
        SparseArray<List<Server>> list = new SparseArray<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject server = array.getJSONObject(i);
            int index = server.getInt("index");
            JSONArray serversArray = server.getJSONArray("servers");
            List<Server> servers = new ArrayList<>();
            for (int j = 0; j < serversArray.length(); j++)
                servers.add(new Server(serversArray.getJSONObject(j)));
            list.put(index, servers);
        }
        return list;
    }

    // Utils
    public void pause(final String gid, final IPause handler) {
        pause(gid, new JTA2.IGID() {
            @Override
            public void onGID(String gid) {
                handler.onPaused(gid);
            }

            @Override
            public void onException(Exception ex) {
                if (forceAction) forcePause(gid, handler);
                else handler.onException(ex);
            }
        });
    }

    private void forcePause(String gid, final IPause handler) {
        forcePause(gid, new JTA2.IGID() {
            @Override
            public void onGID(String gid) {
                handler.onPaused(gid);
            }

            @Override
            public void onException(Exception ex) {
                handler.onException(ex);
            }
        });
    }

    public void moveUp(final String gid, final IMove handler) {
        changePosition(gid, -1, new JTA2.ISuccess() {
            @Override
            public void onSuccess() {
                handler.onMoved(gid);
            }

            @Override
            public void onException(Exception ex) {
                handler.onException(ex);
            }
        });
    }

    public void moveDown(final String gid, final IMove handler) {
        changePosition(gid, 1, new JTA2.ISuccess() {
            @Override
            public void onSuccess() {
                handler.onMoved(gid);
            }

            @Override
            public void onException(Exception ex) {
                handler.onException(ex);
            }
        });
    }

    public void unpause(String gid, final IUnpause handler) {
        unpause(gid, new JTA2.IGID() {
            @Override
            public void onGID(String gid) {
                handler.onUnpaused(gid);
            }

            @Override
            public void onException(Exception ex) {
                handler.onException(ex);
            }
        });
    }

    public boolean remove(final String gid, Download.Status status, final IRemove handler) {
        if (status == Download.Status.COMPLETE || status == Download.Status.ERROR || status == Download.Status.REMOVED) {
            removeDownloadResult(gid, new JTA2.ISuccess() {
                @Override
                public void onSuccess() {
                    handler.onRemovedResult(gid);
                }

                @Override
                public void onException(Exception ex) {
                    handler.onException(ex);
                }
            });
            return true;
        } else {
            remove(gid, new JTA2.IGID() {
                @Override
                public void onGID(String gid) {
                    handler.onRemoved(gid);
                }

                @Override
                public void onException(Exception ex) {
                    if (forceAction) forceRemove(gid, handler);
                    else handler.onException(ex);
                }
            });
            return false;
        }
    }

    private void forceRemove(String gid, final IRemove handler) {
        forceRemove(gid, new JTA2.IGID() {
            @Override
            public void onGID(String gid) {
                handler.onRemoved(gid);
            }

            @Override
            public void onException(Exception ex) {
                handler.onException(ex);
            }
        });
    }

    public void restart(final String gid, final IRestart handler) {
        tellStatus(gid, new JTA2.IDownload() {
            @Override
            public void onDownload(final Download download) {
                getOption(gid, new JTA2.IOption() {
                    @Override
                    public void onOptions(Map<String, String> options) {
                        String url = download.files.get(0).uris.get(AFile.Status.USED);

                        addUri(Collections.singletonList(url), null, options, new JTA2.IGID() {
                            @Override
                            public void onGID(final String newGid) {
                                removeDownloadResult(gid, new JTA2.ISuccess() {
                                    @Override
                                    public void onSuccess() {
                                        handler.onRestarted(newGid);
                                    }

                                    @Override
                                    public void onException(Exception ex) {
                                        handler.onException(ex);
                                    }
                                });
                            }

                            @Override
                            public void onException(Exception ex) {
                                handler.onException(ex);
                            }
                        });
                    }

                    @Override
                    public void onException(Exception ex) {
                        handler.onException(ex);
                    }
                });
            }

            @Override
            public void onException(final Exception ex) {
                handler.onException(ex);
            }
        });
    }

    private void performSelectIndexesOperation(Download download, String[] indexes, final List<AFile> files, boolean select, final IChangeSelection handler) {
        if (select) {
            List<String> newIndexes = new ArrayList<>(Arrays.asList(indexes));
            for (AFile file : files)
                if (Utils.indexOf(indexes, String.valueOf(file.index)) == -1)
                    newIndexes.add(String.valueOf(file.index));

            Map<String, String> map = new HashMap<>();
            map.put("select-file", CommonUtils.join(newIndexes, ","));

            changeOption(download.gid, map, new ISuccess() {
                @Override
                public void onSuccess() {
                    handler.onChangedSelection(true);
                }

                @Override
                public void onException(Exception ex) {
                    handler.onException(ex);
                }
            });
        } else {
            List<String> newIndexes = new ArrayList<>(Arrays.asList(indexes));
            for (AFile file : files)
                if (Utils.indexOf(indexes, String.valueOf(file.index)) != -1)
                    newIndexes.remove(String.valueOf(file.index));

            if (newIndexes.isEmpty()) {
                handler.cantDeselectAll();
                return;
            }

            Map<String, String> map = new HashMap<>();
            map.put("select-file", CommonUtils.join(newIndexes, ","));

            changeOption(download.gid, map, new ISuccess() {
                @Override
                public void onSuccess() {
                    handler.onChangedSelection(false);
                }

                @Override
                public void onException(Exception ex) {
                    handler.onException(ex);
                }
            });
        }
    }

    public void changeSelection(final Download download, final List<AFile> files, final boolean select, final IChangeSelection handler) {
        getOption(download.gid, new IOption() {
            @Override
            public void onOptions(Map<String, String> options) {
                String indexes = options.get("select-file");
                if (indexes == null) {
                    getFiles(download.gid, new IFiles() {
                        @Override
                        public void onFiles(List<AFile> files) {
                            String[] indexes = new String[files.size()];
                            for (int i = 0; i < files.size(); i++)
                                indexes[i] = String.valueOf(files.get(i).index);

                            performSelectIndexesOperation(download, indexes, files, select, handler);
                        }

                        @Override
                        public void onException(Exception ex) {
                            handler.onException(ex);
                        }
                    });
                } else {
                    performSelectIndexesOperation(download, indexes.replace(" ", "").split(","), files, select, handler);
                }
            }

            @Override
            public void onException(Exception ex) {
                handler.onException(ex);
            }
        });
    }

    // Requests
    //aria2.getVersion
    public void getVersion(final IVersion handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.getVersion");
            JSONArray params = Utils.readyParams(context);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onVersion(parseRawFeatures(response.getJSONObject("result").optJSONArray("enabledFeatures")), response.getJSONObject("result").optString("version"));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.saveSession
    public void saveSession(final ISuccess handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.saveSession");
            JSONArray params = Utils.readyParams(context);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                if (Objects.equals(response.optString("result"), "OK"))
                    handler.onSuccess();
                else
                    handler.onException(new Aria2Exception(response.toString(), -1));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.getSessionInfo
    public void getSessionInfo(final ISession handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.getSessionInfo");
            JSONArray params = Utils.readyParams(context);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onSessionInfo(response.getJSONObject("result").optString("sessionId"));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.addUri
    public void addUri(List<String> uris, @Nullable Integer position, @Nullable Map<String, String> options, final IGID handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.addUri");
            JSONArray params = Utils.readyParams(context);

            JSONArray jUris = new JSONArray();
            for (String uri : uris) {
                if (uri == null) continue;
                jUris.put(uri);
            }
            params.put(jUris);

            JSONObject jOptions = new JSONObject();
            if (options != null)
                for (String key : options.keySet())
                    jOptions.put(key, options.get(key));

            params.put(jOptions);

            if (position != null) params.put(position);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onGID(response.getString("result"));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.addTorrent
    public void addTorrent(String base64, @Nullable List<String> uris, @Nullable Map<String, String> options, @Nullable Integer position, final IGID handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.addTorrent");
            JSONArray params = Utils.readyParams(context);
            params.put(base64);

            JSONArray jUris = new JSONArray();
            if (uris != null)
                for (String uri : uris)
                    jUris.put(uri);

            params.put(jUris);

            JSONObject jOptions = new JSONObject();
            if (options != null)
                for (String key : options.keySet())
                    jOptions.put(key, options.get(key));

            params.put(jOptions);

            if (position != null) params.put(position);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onGID(response.getString("result"));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.addMetalink
    public void addMetalink(String base64, @Nullable Map<String, String> options, @Nullable Integer position, final IGID handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.addMetalink");
            JSONArray params = Utils.readyParams(context);
            params.put(base64);

            JSONObject jOptions = new JSONObject();
            if (options != null)
                for (String key : options.keySet())
                    jOptions.put(key, options.get(key));

            params.put(jOptions);

            if (position != null) params.put(position);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onGID(response.getString("result"));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.tellStatus
    public void tellStatus(String gid, final IDownload handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.tellStatus");
            JSONArray params = Utils.readyParams(context);
            params.put(gid);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onDownload(new Download(response.getJSONObject("result")));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.getGlobalStat
    public void getGlobalStat(final IStats handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.getGlobalStat");
            request.put("params", Utils.readyParams(context));
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onStats(new GlobalStats(response.getJSONObject("result")));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.tellActive
    public void tellActive(final IDownloadList handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.tellActive");
            request.put("params", Utils.readyParams(context));
        } catch (JSONException ex) {
            handler.onException(false, ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                List<Download> downloads = new ArrayList<>();
                JSONArray jResult = response.getJSONArray("result");

                for (int c = 0; c < jResult.length(); c++) {
                    downloads.add(new Download(jResult.getJSONObject(c)));
                }

                handler.onDownloads(downloads);
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(q, ex);
            }
        });
    }

    //aria2.tellWaiting
    public void tellWaiting(final IDownloadList handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.tellWaiting");
            JSONArray params = Utils.readyParams(context);
            params.put(0);
            params.put(100);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(false, ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                List<Download> downloads = new ArrayList<>();
                JSONArray jResult = response.getJSONArray("result");

                Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                        e.printStackTrace();
                    }
                });

                for (int c = 0; c < jResult.length(); c++)
                    downloads.add(new Download(jResult.getJSONObject(c)));

                handler.onDownloads(downloads);
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(q, ex);
            }
        });
    }

    //aria2.tellStopped
    public void tellStopped(final IDownloadList handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.tellStopped");
            JSONArray params = Utils.readyParams(context);
            params.put(0);
            params.put(100);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(false, ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                List<Download> downloads = new ArrayList<>();
                JSONArray jResult = response.getJSONArray("result");

                for (int c = 0; c < jResult.length(); c++) {
                    downloads.add(new Download(jResult.getJSONObject(c)));
                }

                handler.onDownloads(downloads);
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(q, ex);
            }
        });
    }

    //not an aria2 RPC method
    public void tellAll(final IDownloadList handler) {
        final List<Download> allDownloads = new ArrayList<>();
        tellActive(new IDownloadList() {
            @Override
            public void onDownloads(List<Download> downloads) {
                allDownloads.addAll(downloads);
                tellWaiting(new IDownloadList() {
                    @Override
                    public void onDownloads(List<Download> downloads) {
                        allDownloads.addAll(downloads);
                        tellStopped(new IDownloadList() {
                            @Override
                            public void onDownloads(List<Download> downloads) {
                                allDownloads.addAll(downloads);
                                handler.onDownloads(allDownloads);
                            }

                            @Override
                            public void onException(boolean queuing, Exception ex) {
                                handler.onException(queuing, ex);
                            }
                        });
                    }

                    @Override
                    public void onException(boolean queuing, Exception ex) {
                        handler.onException(queuing, ex);
                    }
                });
            }

            @Override
            public void onException(boolean queuing, Exception ex) {
                handler.onException(queuing, ex);
            }
        });
    }

    //aria2.pause
    public void pause(String gid, final IGID handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.pause");
            JSONArray params = Utils.readyParams(context);
            params.put(gid);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onGID(response.getString("result"));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.unpause
    public void unpause(String gid, final IGID handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.unpause");
            JSONArray params = Utils.readyParams(context);
            params.put(gid);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onGID(response.getString("result"));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.remove
    public void remove(final String gid, final IGID handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.remove");
            JSONArray params = Utils.readyParams(context);
            params.put(gid);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onGID(response.getString("result"));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.removeDownloadResult
    public void removeDownloadResult(String gid, final ISuccess handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.removeDownloadResult");
            JSONArray params = Utils.readyParams(context);
            params.put(gid);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                if (Objects.equals(response.optString("result"), "OK"))
                    handler.onSuccess();
                else
                    handler.onException(new Aria2Exception(response.toString(), -1));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.forcePause
    public void forcePause(String gid, final IGID handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.forcePause");
            JSONArray params = Utils.readyParams(context);
            params.put(gid);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onGID(response.getString("result"));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.forceRemove
    public void forceRemove(String gid, final IGID handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.forceRemove");
            JSONArray params = Utils.readyParams(context);
            params.put(gid);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onGID(response.getString("result"));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.getOption
    public void getOption(String gid, final IOption handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.getOption");
            JSONArray params = Utils.readyParams(context);
            params.put(gid);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onOptions(parseOptions(response.getJSONObject("result")));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.getGlobalOption
    public void getGlobalOption(final IOption handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.getGlobalOption");
            request.put("params", Utils.readyParams(context));
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onOptions(parseOptions(response.getJSONObject("result")));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.changeOption
    public void changeOption(String gid, Map<String, String> options, final ISuccess handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.changeOption");
            JSONArray params = Utils.readyParams(context);
            params.put(gid);
            JSONObject jOptions = new JSONObject();
            for (Map.Entry<String, String> entry : options.entrySet())
                jOptions.put(entry.getKey(), entry.getValue());
            params.put(jOptions);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                if (Objects.equals(response.optString("result"), "OK"))
                    handler.onSuccess();
                else
                    handler.onException(new Aria2Exception(response.toString(), -1));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.changePosition
    public void changePosition(String gid, int pos, final ISuccess handler) {
        final JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.changePosition");
            JSONArray params = Utils.readyParams(context);
            params.put(gid).put(pos).put("POS_CUR");
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                try {
                    response.getInt("result");
                    handler.onSuccess();
                } catch (Exception ex) {
                    handler.onException(new Aria2Exception(response.toString(), -1));
                }
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    //aria2.changeGlobalOption
    public void changeGlobalOption(Map<String, String> options, final ISuccess handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.changeGlobalOption");
            JSONArray params = Utils.readyParams(context);
            JSONObject jOptions = new JSONObject();
            for (Map.Entry<String, String> entry : options.entrySet())
                jOptions.put(entry.getKey(), entry.getValue());
            params.put(jOptions);
            request.put("params", params);
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                if (Objects.equals(response.optString("result"), "OK"))
                    handler.onSuccess();
                else
                    handler.onException(new Aria2Exception(response.toString(), -1));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    // aria2.getServers
    public void getServers(String gid, final IServers handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.getServers");
            request.put("params", Utils.readyParams(context).put(gid));
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onServers(parseServers(response.optJSONArray("result")));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                if (ex instanceof Aria2Exception) {
                    Aria2Exception exx = (Aria2Exception) ex;
                    if (exx.code == 1 && exx.reason.startsWith("No active download")) {
                        handler.onDownloadNotActive(ex);
                        return;
                    }
                }

                handler.onException(ex);
            }
        });
    }

    // aria2.getPeers
    public void getPeers(String gid, final IPeers handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.getPeers");
            request.put("params", Utils.readyParams(context).put(gid));
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onPeers(parsePeers(response.optJSONArray("result")));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                if (ex instanceof Aria2Exception) {
                    Aria2Exception exx = (Aria2Exception) ex;
                    if (exx.code == 1 && exx.reason.startsWith("No peer data")) {
                        handler.onNoPeerData(ex);
                        return;
                    }
                }

                handler.onException(ex);
            }
        });
    }

    // aria2.getFiles
    public void getFiles(String gid, final IFiles handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "aria2.getFiles");
            request.put("params", Utils.readyParams(context).put(gid));
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onFiles(parseAFiles(response.optJSONArray("result")));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    // system.listMethods
    public void listMethods(final IMethod handler) {
        JSONObject request;
        try {
            request = Utils.readyRequest();
            request.put("method", "system.listMethods");
        } catch (JSONException ex) {
            handler.onException(ex);
            return;
        }

        client.send(request, new IReceived() {
            @Override
            public void onResponse(JSONObject response) throws JSONException {
                handler.onMethods(parseMethods(response.getJSONArray("result")));
            }

            @Override
            public void onException(boolean q, Exception ex) {
                handler.onException(ex);
            }
        });
    }

    public enum DownloadActions {
        PAUSE,
        MOVE_UP,
        MOVE_DOWN,
        REMOVE,
        RESTART,
        RESUME
    }

    public enum AuthMethod {
        NONE,
        HTTP,
        TOKEN
    }

    public interface IChangeSelection {
        void onChangedSelection(boolean selected);

        void cantDeselectAll();

        void onException(Exception ex);
    }

    public interface IPause {
        void onPaused(String gid);

        void onException(Exception ex);
    }

    public interface IMove {
        void onMoved(String gid);

        void onException(Exception ex);
    }

    public interface IUnpause {
        void onUnpaused(String gid);

        void onException(Exception ex);
    }

    public interface IRemove {
        void onRemoved(String gid);

        void onRemovedResult(String gid);

        void onException(Exception ex);
    }

    public interface IRestart {
        void onRestarted(String gid);

        void onException(Exception ex);
    }

    public interface IDownload {
        void onDownload(Download download);

        void onException(Exception ex);
    }

    public interface IDownloadList {
        void onDownloads(List<Download> downloads);

        void onException(boolean queuing, Exception ex);
    }

    public interface IFiles {
        void onFiles(List<AFile> files);

        void onException(Exception ex);
    }

    public interface IGID {
        void onGID(String gid);

        void onException(Exception ex);
    }

    public interface IMethod {
        void onMethods(List<String> methods);

        void onException(Exception ex);
    }

    public interface IOption {
        void onOptions(Map<String, String> options);

        void onException(Exception ex);
    }

    public interface IPeers {
        void onPeers(List<Peer> peers);

        void onException(Exception ex);

        void onNoPeerData(Exception ex);
    }

    public interface ISession {
        void onSessionInfo(String sessionID);

        void onException(Exception ex);
    }

    public interface IServers {
        void onServers(SparseArray<List<Server>> servers);

        void onException(Exception ex);

        void onDownloadNotActive(Exception ex);
    }

    public interface IStats {
        void onStats(GlobalStats stats);

        void onException(Exception ex);
    }

    public interface ISuccess {
        void onSuccess();

        void onException(Exception ex);
    }

    public interface IVersion {
        void onVersion(List<String> rawFeatures, String version);

        void onException(Exception ex);
    }
}