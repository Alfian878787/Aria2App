package com.gianlu.aria2app.Main;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.gianlu.aria2app.DownloadAction;
import com.gianlu.aria2app.NetIO.JTA2.Download;
import com.gianlu.aria2app.R;
import com.gianlu.aria2app.Utils;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class MainCardAdapter extends RecyclerView.Adapter<CardViewHolder> {
    private final Activity context;
    private final List<Download> objs;
    private final IActionMore actionMore;
    private final IMenuSelected actionMenu;
    private final List<Download.STATUS> filters;

    public MainCardAdapter(Activity context, List<Download> objs, IActionMore actionMore, IMenuSelected actionMenu) {
        this.context = context;
        this.objs = objs;
        this.actionMore = actionMore;
        this.actionMenu = actionMenu;
        this.filters = new ArrayList<>();
    }

    private static boolean isExpanded(View v) {
        return v.getVisibility() == View.VISIBLE;
    }

    private static void expand(final View v) {
        v.measure(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        final int targetHeight = v.getMeasuredHeight();

        v.getLayoutParams().height = 0;
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                v.getLayoutParams().height = interpolatedTime == 1
                        ? RelativeLayout.LayoutParams.WRAP_CONTENT
                        : (int) (targetHeight * interpolatedTime);
                v.requestLayout();
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        a.setDuration((int) (targetHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }

    private static void expandTitle(TextView v) {
        v.setSingleLine(false);
        v.setEllipsize(null);
    }

    private static void collapse(final View v) {
        final int initialHeight = v.getMeasuredHeight();

        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    v.setVisibility(View.GONE);
                } else {
                    v.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        a.setDuration((int) (initialHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }

    private static void collapseTitle(TextView v) {
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.MARQUEE);
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
        for (Download download : objs) {
            if (download.gid.equals(gid)) return download;
        }

        return null;
    }

    @Override
    public CardViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new CardViewHolder(LayoutInflater.from(context).inflate(R.layout.download_cardview, parent, false));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBindViewHolder(CardViewHolder holder, int position, List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            Download item = (Download) payloads.get(0);

            if (item.status == Download.STATUS.ACTIVE) {
                holder.detailsChartRefresh.setEnabled(true);

                LineData data = holder.detailsChart.getData();
                if (data == null) holder.detailsChart = Utils.setupChart(holder.detailsChart, true);

                if (data != null) {
                    data.addXValue(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
                    data.addEntry(new Entry(item.downloadSpeed, data.getDataSetByIndex(Utils.CHART_DOWNLOAD_SET).getEntryCount()), Utils.CHART_DOWNLOAD_SET);
                    data.addEntry(new Entry(item.uploadSpeed, data.getDataSetByIndex(Utils.CHART_UPLOAD_SET).getEntryCount()), Utils.CHART_UPLOAD_SET);

                    holder.detailsChart.notifyDataSetChanged();
                    holder.detailsChart.setVisibleXRangeMaximum(90);
                    holder.detailsChart.moveViewToX(data.getXValCount() - 91);
                }
            } else {
                holder.detailsChartRefresh.setEnabled(false);

                holder.detailsChart.clear();
                holder.detailsChart.setNoDataText(context.getString(R.string.downloadIs, item.status.getFormal(context, false)));
            }

            holder.donutProgress.setProgress(item.getProgress().intValue());
            holder.downloadName.setText(item.getName());
            if (item.status == Download.STATUS.ERROR)
                holder.downloadStatus.setText(String.format(Locale.getDefault(), "%s #%d: %s", item.status.getFormal(context, true), item.errorCode, item.errorMessage));
            else
                holder.downloadStatus.setText(item.status.getFormal(context, true));
            holder.downloadSpeed.setText(Utils.speedFormatter(item.downloadSpeed));
            holder.downloadMissingTime.setText(Utils.timeFormatter(item.getMissingTime()));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                holder.detailsCompletedLength.setText(Html.fromHtml(context.getString(R.string.completed_length, Utils.dimensionFormatter(item.completedLength)), Html.FROM_HTML_MODE_COMPACT));
                holder.detailsUploadLength.setText(Html.fromHtml(context.getString(R.string.uploaded_length, Utils.dimensionFormatter(item.uploadedLength)), Html.FROM_HTML_MODE_COMPACT));
            } else {
                holder.detailsCompletedLength.setText(Html.fromHtml(context.getString(R.string.completed_length, Utils.dimensionFormatter(item.completedLength))));
                holder.detailsUploadLength.setText(Html.fromHtml(context.getString(R.string.uploaded_length, Utils.dimensionFormatter(item.uploadedLength))));
            }


            if (item.status == Download.STATUS.UNKNOWN || item.status == Download.STATUS.ERROR)
                holder.more.setVisibility(View.INVISIBLE);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBindViewHolder(final CardViewHolder holder, int position) {
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


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            holder.detailsGid.setText(Html.fromHtml(context.getString(R.string.gid, item.gid), Html.FROM_HTML_MODE_COMPACT));
            holder.detailsTotalLength.setText(Html.fromHtml(context.getString(R.string.total_length, Utils.dimensionFormatter(item.length)), Html.FROM_HTML_MODE_COMPACT));
        } else {
            holder.detailsGid.setText(Html.fromHtml(context.getString(R.string.gid, item.gid)));
            holder.detailsTotalLength.setText(Html.fromHtml(context.getString(R.string.total_length, Utils.dimensionFormatter(item.length))));
        }


        holder.expand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Utils.animateCollapsingArrowBellows((ImageButton) view, isExpanded(holder.details));

                if (isExpanded(holder.details)) {
                    collapse(holder.details);
                    collapseTitle(holder.downloadName);
                } else {
                    expand(holder.details);
                    expandTitle(holder.downloadName);
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
                actionMore.onClick(item);
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
                                actionMenu.onItemSelected(item, DownloadAction.ACTION.REMOVE);
                                break;
                            case R.id.downloadCardViewMenu_restart:
                                actionMenu.onItemSelected(item, DownloadAction.ACTION.RESTART);
                                break;
                            case R.id.downloadCardViewMenu_resume:
                                actionMenu.onItemSelected(item, DownloadAction.ACTION.RESUME);
                                break;
                            case R.id.downloadCardViewMenu_pause:
                                actionMenu.onItemSelected(item, DownloadAction.ACTION.PAUSE);
                                break;
                            case R.id.downloadCardViewMenu_moveDown:
                                actionMenu.onItemSelected(item, DownloadAction.ACTION.MOVE_DOWN);
                                break;
                            case R.id.downloadCardViewMenu_moveUp:
                                actionMenu.onItemSelected(item, DownloadAction.ACTION.MOVE_UP);
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
        return objs.size();
    }

    List<Download> getItems() {
        return objs;
    }

    public interface IActionMore {
        void onClick(Download item);
    }
    public interface IMenuSelected {
        void onItemSelected(Download download, DownloadAction.ACTION action);
    }
}
