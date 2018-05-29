package com.gianlu.aria2app.Activities.MoreAboutDownload.Files;

import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.gianlu.aria2app.NetIO.AbstractClient;
import com.gianlu.aria2app.NetIO.Aria2.AriaDirectory;
import com.gianlu.aria2app.NetIO.Aria2.AriaFiles;
import com.gianlu.aria2app.NetIO.Aria2.Download;
import com.gianlu.aria2app.NetIO.Aria2.DownloadWithUpdate;
import com.gianlu.aria2app.NetIO.Aria2.TreeNode;
import com.gianlu.aria2app.ProfilesManager.MultiProfile;
import com.gianlu.aria2app.ProfilesManager.ProfilesManager;
import com.gianlu.aria2app.R;
import com.gianlu.aria2app.Utils;
import com.gianlu.commonutils.BottomSheet.ThemedModalBottomSheet;
import com.gianlu.commonutils.CommonUtils;
import com.gianlu.commonutils.FontsManager;
import com.gianlu.commonutils.Logging;
import com.gianlu.commonutils.SuperTextView;
import com.gianlu.commonutils.Toaster;

import java.util.Locale;

// FIXME: Update not working (should also rewrite AriaDirectory)
public class DirectorySheet extends ThemedModalBottomSheet<DirectorySheet.SetupPayload, DirectorySheet.UpdatePayload> {
    private SuperTextView length;
    private CheckBox selected;
    private SuperTextView completedLength;
    private TextView percentage;
    private AriaDirectory currentDir;

    @NonNull
    public static DirectorySheet get() {
        return new DirectorySheet();
    }

    @Override
    protected boolean onCreateHeader(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent, @NonNull SetupPayload payload) {
        inflater.inflate(R.layout.sheet_header_dir, parent, true);
        parent.setBackgroundResource(payload.download.update().getBackgroundColor());

        percentage = parent.findViewById(R.id.dirSheet_percentage);
        percentage.setTypeface(FontsManager.get().get(inflater.getContext(), FontsManager.ROBOTO_MEDIUM));

        TextView title = parent.findViewById(R.id.dirSheet_title);
        title.setText(payload.dir.name);

        return true;
    }

    public void update(@NonNull DownloadWithUpdate download, @NonNull AriaFiles files) {
        update(new UpdatePayload(download, files));
    }

    @Override
    protected void onCreateBody(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent, @NonNull SetupPayload payload) {
        inflater.inflate(R.layout.sheet_dir, parent, true);
        currentDir = new AriaDirectory(payload.dir, payload.download);

        final DownloadWithUpdate download = payload.download;
        final TreeNode dir = payload.dir;

        SuperTextView indexes = parent.findViewById(R.id.dirSheet_indexes);
        indexes.setHtml(R.string.indexes, CommonUtils.join(currentDir.indexes, ", "));

        SuperTextView path = parent.findViewById(R.id.dirSheet_path);
        path.setHtml(R.string.path, currentDir.fullPath);

        length = parent.findViewById(R.id.dirSheet_length);
        selected = parent.findViewById(R.id.dirSheet_selected);
        completedLength = parent.findViewById(R.id.dirSheet_completedLength);

        update(currentDir);

        if (download.update().canDeselectFiles()) {
            selected.setEnabled(true);
            selected.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    download.changeSelection(dir.allIndexes(), isChecked, new AbstractClient.OnResult<Download.ChangeSelectionResult>() {
                        @Override
                        public void onResult(@NonNull Download.ChangeSelectionResult result) {
                            // FIXME: Behaving badly
                            Toaster.Message msg;
                            switch (result) {
                                case EMPTY:
                                    msg = Utils.Messages.CANT_DESELECT_ALL_FILES;
                                    break;
                                case SELECTED:
                                    msg = Utils.Messages.DIR_SELECTED;
                                    break;
                                case DESELECTED:
                                    msg = Utils.Messages.DIR_DESELECTED;
                                    break;
                                default:
                                    msg = Utils.Messages.FAILED_LOADING;
                                    break;
                            }

                            Toaster.show(getActivity(), msg, result.toString());
                        }

                        @Override
                        public void onException(Exception ex, boolean shouldForce) {
                            Toaster.show(getActivity(), Utils.Messages.FAILED_CHANGE_FILE_SELECTION, ex);
                        }
                    });
                }
            });
        } else {
            selected.setEnabled(false);
        }

        isLoading(false);
    }

    @Override
    protected void onCustomizeToolbar(@NonNull Toolbar toolbar, @NonNull SetupPayload payload) {
        toolbar.setBackgroundResource(payload.download.update().getBackgroundColor());
        toolbar.setTitle(payload.dir.name);
    }

    @Override
    protected boolean onCustomizeAction(@NonNull FloatingActionButton action, @NonNull final SetupPayload payload) {
        try {
            final MultiProfile profile = ProfilesManager.get(getContext()).getCurrent();
            if (payload.download.update().isMetadata() || profile.getProfile(getContext()).directDownload == null) {
                return false;
            } else {
                action.setImageResource(R.drawable.ic_file_download_white_48dp);
                CommonUtils.setBackgroundColor(action, payload.download.update().getColorAccent());
                action.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        payload.listener.onDownloadDirectory(profile, currentDir);
                    }
                });

                return true;
            }
        } catch (ProfilesManager.NoCurrentProfileException ex) {
            Logging.log(ex);
            return false;
        }
    }

    private void update(AriaDirectory dir) {
        percentage.setText(String.format(Locale.getDefault(), "%d%%", (int) dir.getProgress()));
        length.setHtml(R.string.total_length, CommonUtils.dimensionFormatter(dir.totalLength, false));
        completedLength.setHtml(R.string.completed_length, CommonUtils.dimensionFormatter(dir.completedLength, false));
        selected.setChecked(dir.allSelected());
    }

    @Override
    protected void onRequestedUpdate(@NonNull UpdatePayload payload) {
        currentDir = currentDir.update(payload.download, payload.files);
    }

    public void show(FragmentActivity activity, DownloadWithUpdate download, TreeNode dir, Listener listener) {
        show(activity, new SetupPayload(download, dir, listener));
    }

    @Override
    protected int getCustomTheme(@NonNull SetupPayload payload) {
        return payload.download.update().getThemeResource();
    }

    public interface Listener {
        void onDownloadDirectory(@NonNull MultiProfile profile, @NonNull AriaDirectory dir);
    }

    protected static class SetupPayload {
        private final DownloadWithUpdate download;
        private final TreeNode dir;
        private final DirectorySheet.Listener listener;

        SetupPayload(@NonNull DownloadWithUpdate download, @NonNull TreeNode dir, @NonNull DirectorySheet.Listener listener) {
            this.download = download;
            this.dir = dir;
            this.listener = listener;
        }
    }

    protected static class UpdatePayload {
        private final DownloadWithUpdate download;
        private final AriaFiles files;

        UpdatePayload(@NonNull DownloadWithUpdate download, @NonNull AriaFiles files) {
            this.download = download;
            this.files = files;
        }
    }
}
