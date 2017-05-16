package com.gianlu.aria2app.Activities.MoreAboutDownload;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.gianlu.aria2app.Activities.MoreAboutDownload.Info.UpdateUI;
import com.gianlu.aria2app.Adapters.BitfieldVisualizer;
import com.gianlu.aria2app.NetIO.JTA2.Download;
import com.gianlu.aria2app.NetIO.JTA2.JTA2InitializingException;
import com.gianlu.aria2app.R;
import com.gianlu.aria2app.Utils;
import com.gianlu.commonutils.CommonUtils;
import com.gianlu.commonutils.Logging;
import com.gianlu.commonutils.MessageLayout;
import com.gianlu.commonutils.SuperTextView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;

import java.util.Date;
import java.util.Locale;

// TODO: download actions
public class InfoFragment extends BackPressedFragment implements UpdateUI.IUI {
    private UpdateUI updater;
    private ViewHolder holder;

    public static InfoFragment getInstance(Context context, Download download) {
        InfoFragment fragment = new InfoFragment();
        Bundle args = new Bundle();
        args.putString("title", context.getString(R.string.info));
        args.putSerializable("gid", download.gid);
        args.putBoolean("torrent", download.isTorrent());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        holder = new ViewHolder((ViewGroup) inflater.inflate(R.layout.info_fragment, container, false));
        MessageLayout.setPaddingTop(holder.rootView, 48);

        String gid = getArguments().getString("gid");
        if (gid == null) {
            holder.loading.setVisibility(View.GONE);
            MessageLayout.show(holder.rootView, R.string.failedLoading, R.drawable.ic_error_outline_black_48dp);
            return holder.rootView;
        }

        holder.setup();

        try {
            updater = new UpdateUI(getContext(), gid, this);
            updater.start();
        } catch (JTA2InitializingException ex) {
            holder.loading.setVisibility(View.GONE);
            MessageLayout.show(holder.rootView, R.string.failedLoading, R.drawable.ic_error_outline_black_48dp);
            Logging.logMe(getContext(), ex);
            return holder.rootView;
        }

        return holder.rootView;
    }

    @Override
    public void onUpdateUI(Download download) {
        if (holder != null) holder.update(download);
    }

    @Override
    public boolean canGoBack() {
        return true;
    }

    @Override
    public void onBackPressed() {
        if (updater != null) updater.stopThread(null);
    }

    public class ViewHolder {
        final ViewGroup rootView;
        final LinearLayout container;
        final ProgressBar loading;
        final SuperTextView progress;
        final SuperTextView downloadSpeed;
        final SuperTextView uploadSpeed;
        final SuperTextView remainingTime;
        final SuperTextView gid;
        final SuperTextView totalLength;
        final SuperTextView completedLength;
        final SuperTextView uploadLength;
        final SuperTextView pieceLength;
        final SuperTextView numPieces;
        final SuperTextView connections;
        final SuperTextView directory;
        final SuperTextView verifiedLength;
        final SuperTextView verifyIntegrityPending;
        final BitfieldVisualizer bitfield;
        final LinearLayout bitTorrentOnly;
        final SuperTextView btMode;
        final SuperTextView btSeeders;
        final SuperTextView btSeeder;
        final SuperTextView btComment;
        final SuperTextView btCreationDate;
        final LinearLayout btAnnounceList;
        final LineChart chart;

        ViewHolder(ViewGroup rootView) {
            this.rootView = rootView;

            container = (LinearLayout) rootView.findViewById(R.id.infoFragment_container);
            loading = (ProgressBar) rootView.findViewById(R.id.infoFragment_loading);
            progress = (SuperTextView) rootView.findViewById(R.id.infoFragment_progress);
            downloadSpeed = (SuperTextView) rootView.findViewById(R.id.infoFragment_downloadSpeed);
            uploadSpeed = (SuperTextView) rootView.findViewById(R.id.infoFragment_uploadSpeed);
            remainingTime = (SuperTextView) rootView.findViewById(R.id.infoFragment_remainingTime);
            chart = (LineChart) rootView.findViewById(R.id.infoFragment_chart);
            gid = (SuperTextView) rootView.findViewById(R.id.infoFragment_gid);
            totalLength = (SuperTextView) rootView.findViewById(R.id.infoFragment_totalLength);
            completedLength = (SuperTextView) rootView.findViewById(R.id.infoFragment_completedLength);
            uploadLength = (SuperTextView) rootView.findViewById(R.id.infoFragment_uploadLength);
            pieceLength = (SuperTextView) rootView.findViewById(R.id.infoFragment_pieceLength);
            numPieces = (SuperTextView) rootView.findViewById(R.id.infoFragment_numPieces);
            connections = (SuperTextView) rootView.findViewById(R.id.infoFragment_connections);
            directory = (SuperTextView) rootView.findViewById(R.id.infoFragment_directory);
            verifiedLength = (SuperTextView) rootView.findViewById(R.id.infoFragment_verifiedLength);
            verifyIntegrityPending = (SuperTextView) rootView.findViewById(R.id.infoFragment_verifyIntegrityPending);
            bitfield = (BitfieldVisualizer) rootView.findViewById(R.id.infoFragment_bitfield);

            bitTorrentOnly = (LinearLayout) rootView.findViewById(R.id.infoFragment_bitTorrentOnly);
            btMode = (SuperTextView) rootView.findViewById(R.id.infoFragment_btMode);
            btSeeders = (SuperTextView) rootView.findViewById(R.id.infoFragment_btSeeders);
            btSeeder = (SuperTextView) rootView.findViewById(R.id.infoFragment_btSeeder);
            btComment = (SuperTextView) rootView.findViewById(R.id.infoFragment_btComment);
            btCreationDate = (SuperTextView) rootView.findViewById(R.id.infoFragment_btCreationDate);
            btAnnounceList = (LinearLayout) rootView.findViewById(R.id.infoFragment_btAnnounceList);
        }

        void setup() {
            Utils.setupChart(chart, false);
            int colorRes = getArguments().getBoolean("torrent", false) ? R.color.colorTorrent : R.color.colorAccent;
            chart.setNoDataTextColor(ContextCompat.getColor(getContext(), colorRes));
            bitfield.setColor(colorRes);
            progress.setTypeface("fonts/Roboto-Light.ttf");
            downloadSpeed.setTypeface("fonts/Roboto-Light.ttf");
            uploadSpeed.setTypeface("fonts/Roboto-Light.ttf");
            remainingTime.setTypeface("fonts/Roboto-Light.ttf");
        }

        boolean setChartState(Download download) {
            switch (download.status) {
                case ACTIVE:
                    return true;
                default:
                case PAUSED:
                case WAITING:
                case ERROR:
                case REMOVED:
                case COMPLETE:
                case UNKNOWN:
                    chart.clear();
                    chart.setNoDataText(getString(R.string.downloadIs, download.status.getFormal(getContext(), false)));
                    return false;
            }
        }

        void update(Download download) {
            if (!isAdded()) return;

            MessageLayout.hide(rootView);
            loading.setVisibility(View.GONE);
            container.setVisibility(View.VISIBLE);

            if (setChartState(download)) {
                LineData data = chart.getLineData();
                int pos = data.getEntryCount();
                data.addEntry(new Entry(pos, download.downloadSpeed), Utils.CHART_DOWNLOAD_SET);
                data.addEntry(new Entry(pos, download.uploadSpeed), Utils.CHART_UPLOAD_SET);
                data.notifyDataChanged();
                chart.notifyDataSetChanged();
                chart.setVisibleXRangeMaximum(90);
                chart.moveViewToX(data.getEntryCount());
            }

            progress.setText(String.format(Locale.getDefault(), "%.1f %%", download.getProgress()));
            downloadSpeed.setText(CommonUtils.speedFormatter(download.downloadSpeed, false));
            uploadSpeed.setText(CommonUtils.speedFormatter(download.uploadSpeed, false));
            remainingTime.setText(CommonUtils.timeFormatter(download.getMissingTime()));
            bitfield.update(download);

            gid.setHtml(R.string.gid, download.gid);
            totalLength.setHtml(R.string.total_length, CommonUtils.dimensionFormatter(download.length, false));
            completedLength.setHtml(R.string.completed_length, CommonUtils.dimensionFormatter(download.completedLength, false));
            uploadLength.setHtml(R.string.uploaded_length, CommonUtils.dimensionFormatter(download.uploadLength, false));
            pieceLength.setHtml(R.string.pieces_length, CommonUtils.dimensionFormatter(download.pieceLength, false));
            numPieces.setHtml(R.string.pieces, download.numPieces);
            connections.setHtml(R.string.connections, download.connections);
            directory.setHtml(R.string.directory, download.dir);
            verifiedLength.setHtml(R.string.verifiedLength, CommonUtils.dimensionFormatter(download.verifiedLength, false));
            verifyIntegrityPending.setHtml(R.string.verifyIntegrityPending, String.valueOf(download.verifyIntegrityPending));

            bitTorrentOnly.setVisibility(download.isTorrent() ? View.VISIBLE : View.GONE);
            if (download.isTorrent()) {
                btMode.setHtml(R.string.mode, download.torrent.mode.toString());
                btSeeders.setHtml(R.string.numSeeder, download.numSeeders);
                btSeeder.setHtml(R.string.seeder, String.valueOf(download.seeder));

                if (download.torrent.comment == null) {
                    btComment.setVisibility(View.GONE);
                } else {
                    btComment.setVisibility(View.VISIBLE);
                    btComment.setHtml(R.string.comment, download.torrent.comment);
                }

                if (download.torrent.creationDate == -1) {
                    btCreationDate.setVisibility(View.GONE);
                } else {
                    btCreationDate.setVisibility(View.VISIBLE);
                    btCreationDate.setHtml(R.string.creation_date, CommonUtils.getFullDateFormatter().format(new Date(download.torrent.creationDate)));
                }

                if (download.torrent.announceList.isEmpty()) {
                    btAnnounceList.setVisibility(View.GONE);
                } else {
                    btAnnounceList.setVisibility(View.VISIBLE);
                    btAnnounceList.removeAllViews();
                    for (String url : download.torrent.announceList)
                        btAnnounceList.addView(new SuperTextView(getContext(), url));
                }
            }
        }
    }
}
