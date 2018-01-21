package com.gianlu.aria2app.Activities.MoreAboutDownload.Files;

import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.gianlu.aria2app.FileTypeTextView;
import com.gianlu.aria2app.NetIO.JTA2.AriaFile;
import com.gianlu.aria2app.NetIO.JTA2.Download;
import com.gianlu.aria2app.NetIO.JTA2.JTA2;
import com.gianlu.aria2app.ProfilesManager.MultiProfile;
import com.gianlu.aria2app.ProfilesManager.ProfilesManager;
import com.gianlu.aria2app.R;
import com.gianlu.aria2app.Utils;
import com.gianlu.commonutils.CommonUtils;
import com.gianlu.commonutils.Logging;
import com.gianlu.commonutils.NiceBaseBottomSheet;
import com.gianlu.commonutils.SuperTextView;
import com.gianlu.commonutils.Toaster;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class FileBottomSheet extends NiceBaseBottomSheet {
    private final ISheet listener;
    private final JTA2 jta2;
    private int currentFileIndex = -1;
    private SuperTextView completedLength;
    private SuperTextView length;
    private CheckBox selected;
    private TextView percentage;

    public FileBottomSheet(ViewGroup parent, ISheet listener) throws JTA2.InitializingException {
        super(parent, R.layout.sheet_header_file, R.layout.sheet_file, true);
        this.listener = listener;
        this.jta2 = JTA2.instantiate(getContext());
    }

    @Override
    protected void cleanUp() {
        currentFileIndex = -1;
    }

    @Override
    protected boolean onPrepareAction(@NonNull FloatingActionButton fab, Object... payloads) {
        final Download download = (Download) payloads[0];
        final AriaFile file = (AriaFile) payloads[1];

        try {
            final MultiProfile profile = ProfilesManager.get(getContext()).getCurrent(getContext());
            if (download.isMetadata() || !profile.getProfile(getContext()).isDirectDownloadEnabled()) {
                return false;
            } else {
                fab.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (listener != null) listener.onDownloadFile(profile, download, file);
                    }
                });

                return true;
            }
        } catch (ProfilesManager.NoCurrentProfileException ex) {
            Logging.log(ex);
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onUpdateViews(Object... payloads) {
        AriaFile file = findCurrent((List<AriaFile>) payloads[0]);
        if (file != null) {
            updateHeaderViews(file);
            updateContentViews(file);
        }
    }

    private void updateHeaderViews(AriaFile file) {
        percentage.setText(String.format(Locale.getDefault(), "%d%%", (int) file.getProgress()));
    }

    private void updateContentViews(AriaFile file) {
        selected.setChecked(file.selected);
        length.setHtml(R.string.total_length, CommonUtils.dimensionFormatter(file.length, false));
        completedLength.setHtml(R.string.completed_length, CommonUtils.dimensionFormatter(file.completedLength, false));
    }

    @Nullable
    private AriaFile findCurrent(List<AriaFile> files) {
        for (AriaFile file : files)
            if (file.index == currentFileIndex)
                return file;

        return null;
    }

    @Override
    protected void onCreateHeaderView(@NonNull ViewGroup parent, Object... payloads) {
        FileTypeTextView fileType = parent.findViewById(R.id.fileSheet_fileType);
        fileType.setWidth(48);
        percentage = parent.findViewById(R.id.fileSheet_percentage);
        percentage.setTypeface(Typeface.createFromAsset(getContext().getAssets(), "fonts/Roboto-Medium.ttf"));
        TextView title = parent.findViewById(R.id.fileSheet_title);

        Download download = (Download) payloads[0];
        AriaFile file = (AriaFile) payloads[1];
        currentFileIndex = file.index;

        int colorAccent = download.isTorrent() ? R.color.colorTorrent : R.color.colorAccent_light;
        parent.setBackgroundResource(colorAccent);

        fileType.setFilename(file.getName());
        title.setText(file.getName());
        updateHeaderViews(file);
    }

    @Override
    protected void onCreateContentView(@NonNull ViewGroup parent, Object... payloads) {
        SuperTextView index = parent.findViewById(R.id.fileSheet_index);
        SuperTextView path = parent.findViewById(R.id.fileSheet_path);
        length = parent.findViewById(R.id.fileSheet_length);
        completedLength = parent.findViewById(R.id.fileSheet_completedLength);
        selected = parent.findViewById(R.id.fileSheet_selected);

        final Download download = (Download) payloads[0];
        final AriaFile file = (AriaFile) payloads[1];

        index.setHtml(R.string.index, file.index);
        path.setHtml(R.string.path, file.path);
        updateContentViews(file);

        if (download.supportsDeselectingFiles()) {
            selected.setEnabled(true);
            selected.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    jta2.changeSelection(download, Collections.singletonList(file), isChecked, new JTA2.IChangeSelection() {
                        @Override
                        public void onChangedSelection(final boolean selected) {
                            file.selected = selected;

                            if (listener != null) {
                                if (selected) listener.showToast(Utils.Messages.FILE_SELECTED);
                                else listener.showToast(Utils.Messages.FILE_DESELECTED);
                            }
                        }

                        @Override
                        public void cantDeselectAll() {
                            if (listener != null)
                                listener.showToast(Utils.Messages.CANT_DESELECT_ALL_FILES);
                        }

                        @Override
                        public void onException(final Exception ex) {
                            Logging.log(ex);
                            if (listener != null)
                                listener.showToast(Utils.Messages.FAILED_CHANGE_FILE_SELECTION);
                        }
                    });
                }
            });
        } else {
            selected.setEnabled(false);
        }
    }

    public interface ISheet {
        void onDownloadFile(MultiProfile profile, Download download, AriaFile file);

        void showToast(Toaster.Message message);
    }
}
