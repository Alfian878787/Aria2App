package com.gianlu.aria2app.Activities.MoreAboutDownload.Files;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.gianlu.aria2app.Activities.DirectDownloadActivity;
import com.gianlu.aria2app.Activities.MoreAboutDownload.BigUpdateProvider;
import com.gianlu.aria2app.Activities.MoreAboutDownload.OnBackPressed;
import com.gianlu.aria2app.Adapters.BreadcrumbSegment;
import com.gianlu.aria2app.Adapters.FilesAdapter;
import com.gianlu.aria2app.Downloader.DownloadStartConfig;
import com.gianlu.aria2app.Downloader.DownloaderUtils;
import com.gianlu.aria2app.NetIO.AbstractClient;
import com.gianlu.aria2app.NetIO.Aria2.Aria2Helper;
import com.gianlu.aria2app.NetIO.Aria2.AriaDirectory;
import com.gianlu.aria2app.NetIO.Aria2.AriaFile;
import com.gianlu.aria2app.NetIO.Aria2.AriaFiles;
import com.gianlu.aria2app.NetIO.Aria2.Download;
import com.gianlu.aria2app.NetIO.Aria2.DownloadWithUpdate;
import com.gianlu.aria2app.NetIO.OnRefresh;
import com.gianlu.aria2app.NetIO.Updater.PayloadProvider;
import com.gianlu.aria2app.NetIO.Updater.UpdaterFragment;
import com.gianlu.aria2app.NetIO.Updater.Wants;
import com.gianlu.aria2app.ProfilesManager.MultiProfile;
import com.gianlu.aria2app.R;
import com.gianlu.aria2app.TutorialManager;
import com.gianlu.aria2app.Utils;
import com.gianlu.commonutils.Analytics.AnalyticsApplication;
import com.gianlu.commonutils.Dialogs.DialogUtils;
import com.gianlu.commonutils.Logging;
import com.gianlu.commonutils.RecyclerViewLayout;
import com.gianlu.commonutils.Toaster;

import java.util.Collection;

public class FilesFragment extends UpdaterFragment<DownloadWithUpdate.BigUpdate> implements FilesAdapter.Listener, BreadcrumbSegment.Listener, ServiceConnection, OnBackPressed, FileSheet.Listener, DirectorySheet.Listener {
    private FilesAdapter adapter;
    private FileSheet fileSheet;
    private DirectorySheet dirSheet;
    private LinearLayout breadcrumbsContainer;
    private HorizontalScrollView breadcrumbs;
    private boolean isShowingHint;
    private RecyclerViewLayout recyclerViewLayout;
    private Messenger downloaderMessenger = null;
    private OnWaitBinder boundWaiter;
    private ActionMode actionMode = null;
    private DownloadWithUpdate download;

    @NonNull
    public static FilesFragment getInstance(Context context, String gid) {
        FilesFragment fragment = new FilesFragment();
        Bundle args = new Bundle();
        args.putString("title", context.getString(R.string.files));
        args.putString("gid", gid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public boolean canGoBack(int code) {
        if (code == CODE_CLOSE_SHEET) {
            if (actionMode != null) actionMode.finish();
            if (fileSheet != null) {
                fileSheet.dismiss();
                fileSheet = null;
                dismissDialog();
            }
            return true;
        }

        if (actionMode != null) { // Unluckily ActionMode intercepts the event (useless condition)
            actionMode.finish();
            return false;
        } else if (hasVisibleDialog()) {
            dismissDialog();
            fileSheet = null;
            dirSheet = null;
            return false;
        } else if (adapter != null && adapter.canGoUp()) {
            adapter.navigateUp();
            return false;
        } else {
            return true;
        }
    }

    @NonNull
    @Override
    protected PayloadProvider<DownloadWithUpdate.BigUpdate> requireProvider(@NonNull Context context, @NonNull Bundle args) throws Aria2Helper.InitializingException {
        return new BigUpdateProvider(context, args.getString("gid"));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle savedInstanceState) {
        LinearLayout layout = (LinearLayout) inflater.inflate(R.layout.fragment_files, parent, false);
        breadcrumbsContainer = layout.findViewById(R.id.filesFragment_breadcrumbsContainer);
        breadcrumbs = layout.findViewById(R.id.filesFragment_breadcrumbs);
        recyclerViewLayout = layout.findViewById(R.id.filesFragment_recyclerViewLayout);
        recyclerViewLayout.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        recyclerViewLayout.getList().addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));

        recyclerViewLayout.startLoading();

        return layout;
    }

    @Override
    public void onFileSelected(@NonNull AriaFile file) {
        fileSheet = FileSheet.get();
        fileSheet.show(getActivity(), download, file, this);
    }

    @Override
    public boolean onFileLongClick(@NonNull AriaFile file) {
        if (getActivity() == null || download.update().files.size() == 1) return false;

        adapter.enteredActionMode(file);
        actionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.files_action_mode, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.filesActionMode_select:
                        changeSelectionForBatch(adapter.getSelectedFiles(), true);
                        return true;
                    case R.id.filesActionMode_deselect:
                        changeSelectionForBatch(adapter.getSelectedFiles(), false);
                        return true;
                    case R.id.filesActionMode_selectAll:
                        adapter.selectAllInDirectory();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                actionMode = null;
                adapter.exitedActionMode();
            }
        });
        return true;
    }

    private void changeSelectionForBatch(Collection<AriaFile> files, boolean select) {
        download.changeSelection(AriaFile.allIndexes(files), select, new AbstractClient.OnResult<Download.ChangeSelectionResult>() {
            @Override
            public void onResult(@NonNull Download.ChangeSelectionResult result) {
                Toaster toaster = Toaster.build();
                toaster.extra(result);
                switch (result) {
                    case EMPTY:
                        toaster.message(R.string.cannotDeselectAllFiles);
                        break;
                    case SELECTED:
                        toaster.message(R.string.fileSelected);
                        break;
                    case DESELECTED:
                        toaster.message(R.string.fileDeselected);
                        break;
                    default:
                        toaster.message(R.string.failedAction);
                        break;
                }

                DialogUtils.showToast(getContext(), toaster);
                exitActionMode();
            }

            @Override
            public void onException(Exception ex, boolean shouldForce) {
                showToast(Toaster.build().message(R.string.failedFileChangeSelection).ex(ex));
            }
        });
    }

    @Override
    public void exitActionMode() {
        if (actionMode != null) actionMode.finish();
    }

    @Override
    public boolean onDirectoryLongClick(@NonNull AriaDirectory dir) {
        dirSheet = DirectorySheet.get();
        dirSheet.show(getActivity(), download, dir, this);
        return true;
    }

    private void showTutorial(@NonNull AriaDirectory dir) {
        if (isVisible() && !isShowingHint && dir.files.size() >= 1 && TutorialManager.shouldShowHintFor(getContext(), TutorialManager.Discovery.FILES)) {
            RecyclerView.ViewHolder holder = recyclerViewLayout.getList().findViewHolderForLayoutPosition(dir.dirs.size());
            if (holder != null && getActivity() != null) {
                isShowingHint = true;

                recyclerViewLayout.getList().scrollToPosition(dir.dirs.size());

                Rect rect = new Rect();
                holder.itemView.getGlobalVisibleRect(rect);
                rect.offset((int) -(holder.itemView.getWidth() * 0.3), 0);

                TapTargetView.showFor(getActivity(), TapTarget.forBounds(rect, getString(R.string.fileDetails), getString(R.string.fileDetails_desc))
                                .tintTarget(false)
                                .transparentTarget(true),
                        new TapTargetView.Listener() {
                            @Override
                            public void onTargetDismissed(TapTargetView view, boolean userInitiated) {
                                TutorialManager.setHintShown(getContext(), TutorialManager.Discovery.FILES);
                                isShowingHint = false;
                            }
                        });
            }
        }

        if (isVisible() && !isShowingHint && dir.dirs.size() >= 1 && TutorialManager.shouldShowHintFor(getContext(), TutorialManager.Discovery.FOLDERS)) {
            RecyclerView.ViewHolder holder = recyclerViewLayout.getList().findViewHolderForLayoutPosition(0);
            if (holder != null) {
                isShowingHint = true;

                recyclerViewLayout.getList().scrollToPosition(0);

                Rect rect = new Rect();
                holder.itemView.getGlobalVisibleRect(rect);
                rect.offset((int) -(holder.itemView.getWidth() * 0.3), 0);

                TapTargetView.showFor(getActivity(), TapTarget.forBounds(rect, getString(R.string.folderDetails), getString(R.string.folderDetails_desc))
                                .tintTarget(false)
                                .transparentTarget(true),
                        new TapTargetView.Listener() {
                            @Override
                            public void onTargetDismissed(TapTargetView view, boolean userInitiated) {
                                TutorialManager.setHintShown(getContext(), TutorialManager.Discovery.FOLDERS);
                                isShowingHint = false;
                            }
                        });
            }
        }
    }

    @Override
    public void onDirectoryChanged(@NonNull AriaDirectory dir) {
        breadcrumbsContainer.removeAllViews();

        AriaDirectory node = dir;
        do {
            addPathToBreadcrumbs(node);
            node = node.parent;
        } while (node != null);

        breadcrumbs.post(new Runnable() {
            @Override
            public void run() {
                breadcrumbs.fullScroll(HorizontalScrollView.FOCUS_RIGHT);
            }
        });

        showTutorial(dir);
    }

    private void addPathToBreadcrumbs(AriaDirectory dir) {
        Context context = getContext();
        if (context != null)
            breadcrumbsContainer.addView(new BreadcrumbSegment(context, dir, this), 0);
    }

    @Override
    public void onDirSelected(@NonNull AriaDirectory node) {
        if (adapter != null) adapter.changeDir(node);
    }

    private void startDownloadInternal(final MultiProfile profile, @Nullable final AriaFile file, @Nullable final AriaDirectory dir) {
        try {
            if (getContext() == null)
                throw new DownloadStartConfig.CannotCreateStartConfigException(new NullPointerException("Context is null!"));

            DownloaderUtils.startDownload(downloaderMessenger,
                    file == null ?
                            DownloadStartConfig.create(getContext(), download, profile.getProfile(getContext()), dir) :
                            DownloadStartConfig.create(getContext(), download, profile.getProfile(getContext()), file));
        } catch (DownloaderUtils.InvalidPathException | DownloadStartConfig.CannotCreateStartConfigException ex) {
            showToast(Toaster.build().message(R.string.failedDownloadingDir).ex(ex));
            return;
        }

        Snackbar.make(recyclerViewLayout, R.string.downloadAdded, Snackbar.LENGTH_LONG)
                .setAction(R.string.show, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(getContext(), DirectDownloadActivity.class));
                    }
                }).show();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        downloaderMessenger = new Messenger(service);
        if (boundWaiter != null) boundWaiter.onBound();
        boundWaiter = null;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        downloaderMessenger = null;
    }

    @Override
    public void onDownloadFile(@NonNull final MultiProfile profile, @NonNull final AriaFile file) {
        if (fileSheet != null) {
            fileSheet.dismiss();
            fileSheet = null;
            dismissDialog();
        }

        String mime = file.getMimeType();
        if (mime != null) {
            if (Utils.isStreamable(mime) && getContext() != null) {

                final Intent intent = Utils.getStreamIntent(download, profile.getProfile(getContext()), file);
                if (intent != null && Utils.canHandleIntent(getContext(), intent)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(R.string.couldStreamVideo)
                            .setMessage(R.string.couldStreamVideo_message)
                            .setNeutralButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.stream, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(intent);
                                    AnalyticsApplication.sendAnalytics(getContext(), Utils.ACTION_PLAY_VIDEO);
                                }
                            })
                            .setNegativeButton(R.string.download, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    shouldDownload(profile, file);
                                }
                            });

                    showDialog(builder);
                    return;
                }
            }
        }

        shouldDownload(profile, file);
    }

    private void shouldDownload(final MultiProfile profile, final AriaFile file) {
        if (downloaderMessenger != null) {
            startDownloadInternal(profile, file, null);
        } else {
            boundWaiter = new OnWaitBinder() {
                @Override
                public void onBound() {
                    startDownloadInternal(profile, file, null);
                }
            };
        }

        AnalyticsApplication.sendAnalytics(getContext(), Utils.ACTION_DOWNLOAD_FILE);
    }

    @Override
    public void onDownloadDirectory(@NonNull final MultiProfile profile, @NonNull final AriaDirectory dir) {
        if (dirSheet != null) {
            dirSheet.dismiss();
            dirSheet = null;
            dismissDialog();
        }

        if (downloaderMessenger != null) {
            startDownloadInternal(profile, null, dir);
        } else {
            boundWaiter = new OnWaitBinder() {
                @Override
                public void onBound() {
                    startDownloadInternal(profile, null, dir);
                }
            };
        }

        AnalyticsApplication.sendAnalytics(getContext(), Utils.ACTION_DOWNLOAD_DIRECTORY);
    }

    @Override
    public void onUpdateUi(@NonNull DownloadWithUpdate.BigUpdate payload) {
        AriaFiles files = payload.files;
        if (files.isEmpty() || files.get(0).path.isEmpty()) {
            recyclerViewLayout.showMessage(R.string.noFiles, false);
        } else {
            recyclerViewLayout.showList();
            if (adapter != null) adapter.update(payload.download(), files);
            if (fileSheet != null) fileSheet.update(files);
            if (dirSheet != null) dirSheet.update(payload.download(), files);
            if (adapter != null && adapter.getCurrentDir() != null)
                showTutorial(adapter.getCurrentDir());
        }
    }

    @Override
    public void onLoadUi(@NonNull DownloadWithUpdate.BigUpdate payload) {
        this.download = payload.download();

        if (getContext() == null) return;

        adapter = new FilesAdapter(getContext(), FilesFragment.this);
        recyclerViewLayout.loadListData(adapter);
        recyclerViewLayout.startLoading();

        recyclerViewLayout.enableSwipeRefresh(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                canGoBack(CODE_CLOSE_SHEET);

                refresh(new OnRefresh() {
                    @Override
                    public void refreshed() {
                        adapter = new FilesAdapter(getContext(), FilesFragment.this);
                        recyclerViewLayout.loadListData(adapter);
                        recyclerViewLayout.startLoading();
                    }
                });
            }
        }, R.color.colorAccent, R.color.colorMetalink, R.color.colorTorrent);

        DownloaderUtils.bindService(getContext(), FilesFragment.this);
    }

    @Override
    public boolean onCouldntLoad(@NonNull Exception ex) {
        recyclerViewLayout.showMessage(R.string.failedLoading, true);
        Logging.log(ex);
        return false;
    }

    @NonNull
    @Override
    public Wants<DownloadWithUpdate.BigUpdate> wants(@NonNull Bundle args) {
        return Wants.bigUpdate(args.getString("gid"));
    }

    private interface OnWaitBinder {
        void onBound();
    }
}
