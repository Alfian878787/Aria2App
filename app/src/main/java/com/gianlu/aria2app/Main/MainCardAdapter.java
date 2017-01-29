package com.gianlu.aria2app.Main;

import android.app.Activity;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gianlu.aria2app.DownloadAction;
import com.gianlu.aria2app.NetIO.JTA2.Download;
import com.gianlu.aria2app.R;
import com.gianlu.aria2app.Utils;
import com.gianlu.commonutils.CommonUtils;
import com.github.lzyzsd.circleprogress.DonutProgress;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MainCardAdapter extends RecyclerView.Adapter<MainCardAdapter.ViewHolder> {
    private final Activity context;
    private final List<Download> objs;
    private final IActions handler;
    private final List<Download.STATUS> filters;

    public MainCardAdapter(Activity context, List<Download> objs, IActions handler) {
        this.context = context;
        this.objs = objs;
        this.handler = handler;
        this.filters = new ArrayList<>();

        Collections.sort(this.objs, new StatusComparator());
    }

    public void sortBy(SortBy sorting) {
        switch (sorting) {
            case STATUS:
                Collections.sort(objs, new StatusComparator());
                break;
            case PROGRESS:
                Collections.sort(objs, new ProgressComparator());
                break;
            case DOWNLOAD_SPEED:
                Collections.sort(objs, new DownloadSpeedComparator());
                break;
            case UPLOAD_SPEED:
                Collections.sort(objs, new UploadSpeedComparator());
                break;
            case COMPLETED_LENGTH:
                Collections.sort(objs, new CompletedLengthComparator());
                break;
            case LENGTH:
                Collections.sort(objs, new LengthComparator());
                break;
        }

        notifyDataSetChanged();
    }

    public void addFilter(Download.STATUS status) {
        filters.add(status);
        notifyDataSetChanged();
    }

    public void removeFilter(Download.STATUS status) {
        filters.remove(status);
        notifyDataSetChanged();
    }

    void updateItem(final int position, final Download update) {
        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyItemChanged(position, update);
            }
        });
    }

    private Download getItem(int position) {
        return objs.get(position);
    }

    public Download getItem(String gid) {
        for (Download download : objs)
            if (download.gid.equals(gid))
                return download;
        return null;
    }

    public void removeItem(String gid) {
        int index = objs.indexOf(getItem(gid));
        if (index != -1) {
            objs.remove(index);
            notifyDataSetChanged();
        }

        if (handler != null)
            handler.onItemCountUpdated(objs.size());
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.download_cardview, parent, false));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            Download item = (Download) payloads.get(0);

            if (item.status == Download.STATUS.ACTIVE) {
                holder.detailsChartRefresh.setEnabled(true);

                LineData data = holder.detailsChart.getData();
                if (data == null) {
                    holder.detailsChart = Utils.setupChart(holder.detailsChart, true);
                    data = holder.detailsChart.getData();
                }

                if (data != null) {
                    int pos = data.getEntryCount() / 2 + 1;
                    data.addEntry(new Entry(pos, item.downloadSpeed), Utils.CHART_DOWNLOAD_SET);
                    data.addEntry(new Entry(pos, item.uploadSpeed), Utils.CHART_UPLOAD_SET);
                    data.notifyDataChanged();
                    holder.detailsChart.notifyDataSetChanged();

                    holder.detailsChart.setVisibleXRangeMaximum(90);
                    holder.detailsChart.moveViewToX(pos - 91);
                }
            } else {
                holder.detailsChartRefresh.setEnabled(false);

                holder.detailsChart.clear();
                holder.detailsChart.setNoDataText(context.getString(R.string.downloadIs, item.status.getFormal(context, false)));
            }

            holder.donutProgress.setProgress((int) item.getProgress());
            holder.downloadName.setText(item.getName());
            if (item.status == Download.STATUS.ERROR)
                holder.downloadStatus.setText(String.format(Locale.getDefault(), "%s #%d: %s", item.status.getFormal(context, true), item.errorCode, item.errorMessage));
            else
                holder.downloadStatus.setText(item.status.getFormal(context, true));
            holder.downloadSpeed.setText(CommonUtils.speedFormatter(item.downloadSpeed));
            holder.downloadMissingTime.setText(CommonUtils.timeFormatter(item.getMissingTime()));

            holder.detailsCompletedLength.setText(Html.fromHtml(context.getString(R.string.completed_length, CommonUtils.dimensionFormatter(item.completedLength))));
            holder.detailsUploadLength.setText(Html.fromHtml(context.getString(R.string.uploaded_length, CommonUtils.dimensionFormatter(item.uploadLength))));

            if (item.status == Download.STATUS.UNKNOWN || item.status == Download.STATUS.ERROR)
                holder.more.setVisibility(View.INVISIBLE);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final Download item = getItem(position);

        // Static
        final int color;
        if (item.isBitTorrent)
            color = ContextCompat.getColor(context, R.color.colorTorrent_pressed);
        else
            color = ContextCompat.getColor(context, R.color.colorAccent);

        holder.detailsChart = Utils.setupChart(holder.detailsChart, true);
        holder.donutProgress.setFinishedStrokeColor(color);
        holder.donutProgress.setUnfinishedStrokeColor(Color.argb(26, Color.red(color), Color.green(color), Color.blue(color)));

        holder.detailsGid.setText(Html.fromHtml(context.getString(R.string.gid, item.gid)));
        holder.detailsTotalLength.setText(Html.fromHtml(context.getString(R.string.total_length, CommonUtils.dimensionFormatter(item.length))));

        holder.expand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CommonUtils.animateCollapsingArrowBellows((ImageButton) view, CommonUtils.isExpanded(holder.details));

                if (CommonUtils.isExpanded(holder.details)) {
                    CommonUtils.collapse(holder.details);
                    CommonUtils.collapseTitle(holder.downloadName);
                } else {
                    CommonUtils.expand(holder.details);
                    CommonUtils.expandTitle(holder.downloadName);
                }
            }
        });
        holder.detailsChartRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                holder.detailsChart = Utils.setupChart(holder.detailsChart, true);
            }
        });
        holder.more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handler.onMoreClick(item);
            }
        });
        holder.menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupMenu popupMenu = new PopupMenu(context, holder.menu, Gravity.BOTTOM);
                popupMenu.inflate(R.menu.download_cardview);
                Menu menu = popupMenu.getMenu();

                switch (item.status) {
                    case ACTIVE:
                        menu.removeItem(R.id.downloadCardViewMenu_resume);
                        menu.removeItem(R.id.downloadCardViewMenu_restart);
                        menu.removeItem(R.id.downloadCardViewMenu_moveUp);
                        menu.removeItem(R.id.downloadCardViewMenu_moveDown);
                        break;
                    case WAITING:
                        menu.removeItem(R.id.downloadCardViewMenu_pause);
                        menu.removeItem(R.id.downloadCardViewMenu_resume);
                        menu.removeItem(R.id.downloadCardViewMenu_restart);
                        break;
                    case PAUSED:
                        menu.removeItem(R.id.downloadCardViewMenu_pause);
                        menu.removeItem(R.id.downloadCardViewMenu_restart);
                        menu.removeItem(R.id.downloadCardViewMenu_moveUp);
                        menu.removeItem(R.id.downloadCardViewMenu_moveDown);
                        break;
                    case COMPLETE:
                        menu.removeItem(R.id.downloadCardViewMenu_pause);
                        menu.removeItem(R.id.downloadCardViewMenu_resume);
                        menu.removeItem(R.id.downloadCardViewMenu_restart);
                        menu.removeItem(R.id.downloadCardViewMenu_moveUp);
                        menu.removeItem(R.id.downloadCardViewMenu_moveDown);
                        break;
                    case ERROR:
                        menu.removeItem(R.id.downloadCardViewMenu_pause);
                        menu.removeItem(R.id.downloadCardViewMenu_resume);
                        menu.removeItem(R.id.downloadCardViewMenu_restart);
                        menu.removeItem(R.id.downloadCardViewMenu_moveUp);
                        menu.removeItem(R.id.downloadCardViewMenu_moveDown);
                        break;
                    case REMOVED:
                        if (item.isBitTorrent)
                            menu.removeItem(R.id.downloadCardViewMenu_restart);
                        menu.removeItem(R.id.downloadCardViewMenu_pause);
                        menu.removeItem(R.id.downloadCardViewMenu_resume);
                        menu.removeItem(R.id.downloadCardViewMenu_moveUp);
                        menu.removeItem(R.id.downloadCardViewMenu_moveDown);
                        break;
                }

                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.downloadCardViewMenu_remove:
                                handler.onMenuItemSelected(item, DownloadAction.ACTION.REMOVE);
                                break;
                            case R.id.downloadCardViewMenu_restart:
                                handler.onMenuItemSelected(item, DownloadAction.ACTION.RESTART);
                                break;
                            case R.id.downloadCardViewMenu_resume:
                                handler.onMenuItemSelected(item, DownloadAction.ACTION.RESUME);
                                break;
                            case R.id.downloadCardViewMenu_pause:
                                handler.onMenuItemSelected(item, DownloadAction.ACTION.PAUSE);
                                break;
                            case R.id.downloadCardViewMenu_moveDown:
                                handler.onMenuItemSelected(item, DownloadAction.ACTION.MOVE_DOWN);
                                break;
                            case R.id.downloadCardViewMenu_moveUp:
                                handler.onMenuItemSelected(item, DownloadAction.ACTION.MOVE_UP);
                                break;
                        }
                        return true;
                    }
                });
                popupMenu.show();
            }
        });

        if (item.status == Download.STATUS.UNKNOWN || item.status == Download.STATUS.ERROR)
            holder.more.setVisibility(View.INVISIBLE);

        if (filters.contains(item.status))
            holder.itemView.setVisibility(View.GONE);
        else
            holder.itemView.setVisibility(View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        if (handler != null)
            handler.onItemCountUpdated(objs.size());
        return objs.size();
    }

    List<Download> getItems() {
        return objs;
    }

    public enum SortBy {
        STATUS,
        PROGRESS,
        DOWNLOAD_SPEED,
        UPLOAD_SPEED,
        COMPLETED_LENGTH,
        LENGTH
    }

    public interface IActions {
        void onMoreClick(Download item);

        void onItemCountUpdated(int count);

        void onMenuItemSelected(Download download, DownloadAction.ACTION action);
    }

    private class StatusComparator implements Comparator<Download> {
        @Override
        public int compare(Download o1, Download o2) {
            if (o1.status == o2.status)
                return 0;
            else if (o1.status.ordinal() < o2.status.ordinal())
                return -1;
            else
                return 1;
        }
    }

    private class DownloadSpeedComparator implements Comparator<Download> {
        @Override
        public int compare(Download o1, Download o2) {
            if (Objects.equals(o1.downloadSpeed, o2.downloadSpeed))
                return 0;
            else if (o1.downloadSpeed > o2.downloadSpeed)
                return -1;
            else
                return 1;
        }
    }

    private class UploadSpeedComparator implements Comparator<Download> {
        @Override
        public int compare(Download o1, Download o2) {
            if (Objects.equals(o1.uploadSpeed, o2.uploadSpeed))
                return 0;
            else if (o1.uploadSpeed > o2.uploadSpeed)
                return -1;
            else
                return 1;
        }
    }

    private class LengthComparator implements Comparator<Download> {
        @Override
        public int compare(Download o1, Download o2) {
            if (Objects.equals(o1.length, o2.length))
                return 0;
            else if (o1.length > o2.length)
                return -1;
            else
                return 1;
        }
    }

    private class CompletedLengthComparator implements Comparator<Download> {
        @Override
        public int compare(Download o1, Download o2) {
            if (Objects.equals(o1.completedLength, o2.completedLength))
                return 0;
            else if (o1.completedLength > o2.completedLength)
                return -1;
            else
                return 1;
        }
    }

    private class ProgressComparator implements Comparator<Download> {
        @Override
        public int compare(Download o1, Download o2) {
            if (Objects.equals(o1.getProgress(), o2.getProgress()))
                return 0;
            else if (o1.getProgress() > o2.getProgress())
                return 1;
            else
                return -1;
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        final DonutProgress donutProgress;
        final TextView downloadName;
        final TextView downloadStatus;
        final TextView downloadSpeed;
        final TextView downloadMissingTime;
        final LinearLayout details;
        final ImageButton detailsChartRefresh;
        final TextView detailsGid;
        final TextView detailsTotalLength;
        final TextView detailsCompletedLength;
        final TextView detailsUploadLength;
        final ImageButton expand;
        final Button more;
        final ImageButton menu;
        LineChart detailsChart;

        ViewHolder(View itemView) {
            super(itemView);

            donutProgress = (DonutProgress) itemView.findViewById(R.id.downloadCardView_donutProgress);
            downloadName = (TextView) itemView.findViewById(R.id.downloadCardView_name);
            downloadStatus = (TextView) itemView.findViewById(R.id.downloadCardView_status);
            downloadSpeed = (TextView) itemView.findViewById(R.id.downloadCardView_downloadSpeed);
            downloadMissingTime = (TextView) itemView.findViewById(R.id.downloadCardView_missingTime);
            details = (LinearLayout) itemView.findViewById(R.id.downloadCardView_details);
            expand = (ImageButton) itemView.findViewById(R.id.downloadCardView_expand);
            more = (Button) itemView.findViewById(R.id.downloadCardView_actionMore);
            menu = (ImageButton) itemView.findViewById(R.id.downloadCardView_actionMenu);

            detailsChart = (LineChart) itemView.findViewById(R.id.downloadCardViewDetails_chart);
            detailsChartRefresh = (ImageButton) itemView.findViewById(R.id.downloadCardViewDetails_chartRefresh);
            detailsGid = (TextView) itemView.findViewById(R.id.downloadCardViewDetails_gid);
            detailsTotalLength = (TextView) itemView.findViewById(R.id.downloadCardViewDetails_totalLength);
            detailsCompletedLength = (TextView) itemView.findViewById(R.id.downloadCardViewDetails_completedLength);
            detailsUploadLength = (TextView) itemView.findViewById(R.id.downloadCardViewDetails_uploadLength);
        }
    }

}
