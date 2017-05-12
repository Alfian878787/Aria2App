package com.gianlu.aria2app.Activities.MoreAboutDownload.PeersFragment;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gianlu.aria2app.NetIO.JTA2.JTA2;
import com.gianlu.aria2app.NetIO.JTA2.JTA2InitializingException;
import com.gianlu.aria2app.NetIO.JTA2.Peer;
import com.gianlu.aria2app.R;
import com.gianlu.aria2app.Utils;
import com.gianlu.commonutils.CommonUtils;

import java.util.List;

public class PeersPagerFragment extends Fragment {
    // private UpdateUI updateUI;

    public static PeersPagerFragment newInstance(String title, String gid) {
        PeersPagerFragment fragment = new PeersPagerFragment();

        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("gid", gid);

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        // UpdateUI.stop(updateUI);

        final Activity activity = getActivity();
        if (activity == null) return;

        try {
            JTA2.instantiate(activity).getPeers(getArguments().getString("gid"), new JTA2.IPeers() {
                @Override
                public void onPeers(List<Peer> peers) {
                    final PeerCardAdapter adapter = new PeerCardAdapter(getContext(), peers, (CardView) view.findViewById(R.id.peersFragment_noData));
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            view.findViewById(R.id.peersFragment_noData).setVisibility(View.GONE);
                            ((RecyclerView) view.findViewById(R.id.peersFragment_recyclerView)).setAdapter(adapter);
                        }
                    });

                    /*
                    updateUI = new UpdateUI(activity, getArguments().getString("gid"), adapter);
                    new Thread(updateUI).start();
                    */
                }

                @Override
                public void onException(Exception exception) {
                    CommonUtils.UIToast(activity, Utils.ToastMessages.FAILED_GATHERING_INFORMATION, exception);
                }

                @Override
                public void onNoPeerData(final Exception exception) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            PeerCardAdapter adapter = ((PeerCardAdapter) ((RecyclerView) view.findViewById(R.id.peersFragment_recyclerView)).getAdapter());
                            if (adapter != null)
                                adapter.clear();
                            view.findViewById(R.id.peersFragment_noData).setVisibility(View.VISIBLE);
                            ((TextView) view.findViewById(R.id.peersFragment_noDataLabel)).setText(getString(R.string.noPeersMessage, exception.getMessage()));
                        }
                    });
                }
            });
        } catch (JTA2InitializingException ex) {
            CommonUtils.UIToast(activity, Utils.ToastMessages.FAILED_GATHERING_INFORMATION, ex);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable final Bundle savedInstanceState) {
        final LinearLayout rootView = (LinearLayout) inflater.inflate(R.layout.peers_fragment, container, false);

        final SwipeRefreshLayout swipeLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.peersFragment_swipeLayout);
        swipeLayout.setColorSchemeResources(R.color.colorAccent, R.color.colorMetalink, R.color.colorTorrent);
        swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // TODO
            }
        });

        RecyclerView recyclerView = (RecyclerView) rootView.findViewById(R.id.peersFragment_recyclerView);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(llm);

        return rootView;
    }
}
