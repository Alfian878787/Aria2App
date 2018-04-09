package com.gianlu.aria2app.NetIO.Aria2;

import android.support.annotation.NonNull;
import android.util.SparseArray;

import com.gianlu.aria2app.NetIO.AbstractClient;
import com.gianlu.aria2app.NetIO.AriaRequests;
import com.gianlu.commonutils.CommonUtils;

import org.json.JSONException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DownloadWithHelper {
    private final Download baseDownload;
    private final AbstractClient client;

    public DownloadWithHelper(@NonNull Download download, AbstractClient client) {
        this.baseDownload = download;
        this.client = client;
    }

    private static ChangeSelectionResult performSelectIndexesOperation(AbstractClient client, String gid, Integer[] currIndexes, Integer[] selIndexes, boolean select) throws Exception {
        Collection<Integer> newIndexes = new HashSet<>(Arrays.asList(currIndexes));
        if (select) {
            newIndexes.addAll(Arrays.asList(selIndexes)); // Does not allow duplicates
        } else {
            newIndexes.removeAll(Arrays.asList(selIndexes));
            if (newIndexes.isEmpty()) return ChangeSelectionResult.EMPTY;
        }

        client.sendSync(AriaRequests.changeOptions(gid, Collections.singletonMap("select-file", CommonUtils.join(newIndexes, ","))));
        if (select) return ChangeSelectionResult.SELECTED;
        else return ChangeSelectionResult.DESELECTED;
    }

    @NonNull
    public Download get() {
        return baseDownload;
    }

    @NonNull
    public String gid() {
        return baseDownload.gid;
    }

    public void restart(final AbstractClient.OnSuccess listener) {
        restart(new AbstractClient.OnResult<String>() {
            @Override
            public void onResult(String result) {
                listener.onSuccess();
            }

            @Override
            public void onException(Exception ex, boolean shouldForce) {
                listener.onException(ex, shouldForce);
            }
        });
    }

    public void moveUp(AbstractClient.OnSuccess listener) {
        moveRelative(1, listener);
    }

    public void moveDown(AbstractClient.OnSuccess listener) {
        moveRelative(-1, listener);
    }

    public final void servers(AbstractClient.OnResult<SparseArray<Servers>> listener) {
        client.send(AriaRequests.getServers(baseDownload), listener);
    }

    public final void peers(AbstractClient.OnResult<Peers> listener) {
        client.send(AriaRequests.getPeers(baseDownload), listener);
    }

    public final void files(AbstractClient.OnResult<List<AriaFile>> listener) {
        client.send(AriaRequests.getFiles(baseDownload), listener);
    }

    public final void options(AbstractClient.OnResult<Map<String, String>> listener) {
        client.send(AriaRequests.getOptions(baseDownload.gid), listener);
    }

    public final void bigUpdate(AbstractClient.OnResult<DownloadWithHelper> listener) {
        client.send(AriaRequests.tellStatus(baseDownload.gid), listener);
    }

    public final void changePosition(int pos, String mode, AbstractClient.OnSuccess listener) {
        client.send(AriaRequests.changePosition(baseDownload.gid, pos, mode), listener);
    }

    public final void changeOptions(Map<String, String> options, AbstractClient.OnSuccess listener) throws JSONException {
        client.send(AriaRequests.changeOptions(baseDownload.gid, options), listener);
    }

    public final void pause(final AbstractClient.OnSuccess listener) {
        client.send(AriaRequests.pause(baseDownload.gid), new AbstractClient.OnSuccess() {
            private boolean retried = false;

            @Override
            public void onSuccess() {
                listener.onSuccess();
            }

            @Override
            public void onException(Exception ex, boolean shouldForce) {
                if (!retried && shouldForce)
                    client.send(AriaRequests.forcePause(baseDownload.gid), this);
                else listener.onException(ex, shouldForce);
                retried = true;
            }
        });
    }

    public final void unpause(AbstractClient.OnSuccess listener) {
        client.send(AriaRequests.unpause(baseDownload.gid), listener);
    }

    public final void moveRelative(int relative, AbstractClient.OnSuccess listener) {
        client.send(AriaRequests.changePosition(baseDownload.gid, relative, "POS_CUR"), listener);
    }

    public final void restart(AbstractClient.OnResult<String> listener) {
        client.batch(new AbstractClient.BatchSandbox<String>() {
            @Override
            public String sandbox(AbstractClient client, boolean shouldForce) throws Exception {
                Download old = client.sendSync(AriaRequests.tellStatus(baseDownload.gid)).get();
                Map<String, String> oldOptions = client.sendSync(AriaRequests.getOptions(baseDownload.gid));

                Set<String> newUrls = new HashSet<>();
                for (AriaFile file : old.last().files) {
                    for (String url : file.uris.keySet()) {
                        if (file.uris.get(url) == AriaFile.Status.USED)
                            newUrls.add(url);
                    }
                }

                String newGid = client.sendSync(AriaRequests.addUri(newUrls, null, oldOptions));
                client.sendSync(AriaRequests.removeDownloadResult(baseDownload.gid));
                return newGid;
            }
        }, listener);
    }

    public final void remove(final boolean removeMetadata, AbstractClient.OnResult<RemoveResult> listener) {
        client.batch(new AbstractClient.BatchSandbox<RemoveResult>() {
            @Override
            public RemoveResult sandbox(AbstractClient client, boolean shouldForce) throws Exception {
                Download.SmallUpdate last = client.sendSync(AriaRequests.tellStatus(baseDownload.gid)).get().last();
                if (last.status == Download.Status.COMPLETE || last.status == Download.Status.ERROR || last.status == Download.Status.REMOVED) {
                    client.sendSync(AriaRequests.removeDownloadResult(baseDownload.gid));
                    if (removeMetadata) {
                        client.sendSync(AriaRequests.removeDownloadResult(last.following));
                        return RemoveResult.REMOVED_RESULT_AND_METADATA;
                    } else {
                        return RemoveResult.REMOVED_RESULT;
                    }
                } else {
                    try {
                        client.sendSync(AriaRequests.remove(baseDownload.gid));
                    } catch (IOException ex) {
                        if (shouldForce)
                            client.sendSync(AriaRequests.forceRemove(baseDownload.gid));
                        else
                            throw ex;
                    }

                    return RemoveResult.REMOVED;
                }
            }
        }, listener);
    }

    public final void changeSelection(final Integer[] selIndexes, final boolean select, AbstractClient.OnResult<ChangeSelectionResult> listener) {
        client.batch(new AbstractClient.BatchSandbox<ChangeSelectionResult>() {
            @Override
            public ChangeSelectionResult sandbox(AbstractClient client, boolean shouldForce) throws Exception {
                Map<String, String> options = client.sendSync(AriaRequests.getOptions(baseDownload.gid));
                String currIndexes = options.get("select-file");
                if (currIndexes == null)
                    return performSelectIndexesOperation(client, baseDownload.gid, client.sendSync(AriaRequests.getFileIndexes(baseDownload.gid)), selIndexes, select);
                else
                    return performSelectIndexesOperation(client, baseDownload.gid, CommonUtils.toIntsList(currIndexes, ","), selIndexes, select);
            }
        }, listener);
    }

    public enum ChangeSelectionResult {
        EMPTY,
        SELECTED,
        DESELECTED
    }

    public enum RemoveResult {
        REMOVED,
        REMOVED_RESULT,
        REMOVED_RESULT_AND_METADATA
    }
}
