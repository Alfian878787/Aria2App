package com.gianlu.aria2app.Activities.MoreAboutDownload.Info;

import android.content.Context;

import com.gianlu.aria2app.NetIO.AbstractClient;
import com.gianlu.aria2app.NetIO.Aria2.Aria2Helper;
import com.gianlu.aria2app.NetIO.Aria2.Download;
import com.gianlu.aria2app.NetIO.Aria2.DownloadWithHelper;
import com.gianlu.aria2app.NetIO.Updater.BaseDownloadUpdater;
import com.gianlu.aria2app.NetIO.Updater.UpdaterFramework;

class Updater extends BaseDownloadUpdater<Download> implements AbstractClient.OnResult<DownloadWithHelper> {
    Updater(Context context, Download download, UpdaterFramework.Interface<Download> listener) throws Aria2Helper.InitializingException {
        super(context, download, listener);
    }

    @Override
    public void loop() {
        download.bigUpdate(this);
    }

    @Override
    public void onResult(DownloadWithHelper result) {
        hasResult(result.get());
    }

    @Override
    public void onException(Exception ex, boolean shouldForce) {
        errorOccurred(ex, shouldForce);
    }
}
