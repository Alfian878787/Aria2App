package com.gianlu.aria2app.Activities.MoreAboutDownload;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.gianlu.aria2app.NetIO.Aria2.DownloadWithUpdate;
import com.gianlu.aria2app.NetIO.OnRefresh;
import com.gianlu.aria2app.NetIO.Updater.UpdaterFragment;
import com.gianlu.aria2app.NetIO.Updater.Wants;
import com.gianlu.aria2app.R;
import com.gianlu.commonutils.Logging;
import com.gianlu.commonutils.NiceBaseBottomSheet;
import com.gianlu.commonutils.RecyclerViewLayout;
import com.gianlu.commonutils.SuppressingLinearLayoutManager;

public abstract class PeersServersFragment<A extends RecyclerView.Adapter<?>, S extends NiceBaseBottomSheet> extends UpdaterFragment<DownloadWithUpdate.BigUpdate> implements OnBackPressed {
    protected TopCountriesView topDownloadCountries;
    protected TopCountriesView topUploadCountries;
    protected RecyclerViewLayout recyclerViewLayout;
    protected S sheet;
    protected A adapter;

    protected abstract boolean showUpload();

    protected abstract A getAdapter(@NonNull Context context);

    protected abstract S getSheet(@NonNull CoordinatorLayout layout);

    @Override
    public final boolean canGoBack(int code) {
        if (code == CODE_CLOSE_SHEET) {
            if (sheet != null) sheet.collapse();
            return true;
        }

        if (sheet != null && sheet.isExpanded()) {
            sheet.collapse();
            return false;
        } else {
            return true;
        }
    }

    @Override
    public final void onCouldntLoad(@NonNull Exception ex) {
        recyclerViewLayout.showMessage(R.string.failedLoading, true);
        Logging.log(ex);
    }

    @NonNull
    @Override
    public final Wants<DownloadWithUpdate.BigUpdate> wants(@NonNull Bundle args) {
        return Wants.bigUpdate(args.getString("gid")); // TODO: Should be peers or servers
    }

    @Nullable
    @Override
    public final View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getContext() == null) return null;

        CoordinatorLayout layout = (CoordinatorLayout) inflater.inflate(R.layout.fragment_peers_and_servers, container, false);
        topDownloadCountries = layout.findViewById(R.id.peersServersFragment_topDownloadCountries);
        topUploadCountries = layout.findViewById(R.id.peersServersFragment_topUploadCountries);
        recyclerViewLayout = layout.findViewById(R.id.peersServersFragment_recyclerViewLayout);
        recyclerViewLayout.enableSwipeRefresh(R.color.colorAccent, R.color.colorMetalink, R.color.colorTorrent);
        recyclerViewLayout.setLayoutManager(new SuppressingLinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        adapter = getAdapter(getContext());
        recyclerViewLayout.loadListData(adapter);
        recyclerViewLayout.startLoading();

        sheet = getSheet(layout);
        recyclerViewLayout.setRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh(new OnRefresh() {
                    @Override
                    public void refreshed() {
                        adapter = getAdapter(getContext());
                        recyclerViewLayout.loadListData(adapter);
                        recyclerViewLayout.startLoading();
                    }
                });
            }
        });

        layout.findViewById(R.id.peersServersFragment_topUploadCountriesContainer).setVisibility(showUpload() ? View.VISIBLE : View.GONE);

        return layout;
    }
}
