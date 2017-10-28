package com.gianlu.aria2app.Activities.AddDownload;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gianlu.aria2app.Activities.EditProfile.InvalidFieldException;
import com.gianlu.aria2app.R;
import com.gianlu.aria2app.Utils;
import com.gianlu.commonutils.SuperTextView;
import com.gianlu.commonutils.Toaster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Base64Fragment extends Fragment {
    private final int FILE_SELECT_CODE = 7;
    private TextView path;
    private Uri data;

    public static Base64Fragment getInstance(Context context, boolean torrent, @Nullable Uri uri) {
        Base64Fragment fragment = new Base64Fragment();
        Bundle args = new Bundle();
        args.putBoolean("torrent", torrent);
        args.putString("title", context.getString(R.string.file));
        if (uri != null) args.putParcelable("uri", uri);
        fragment.setArguments(args);
        return fragment;
    }

    private void showFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if (getArguments().getBoolean("torrent", true)) intent.setType("application/x-bittorrent");
        else intent.setType("application/metalink4+xml,application/metalink+xml");

        try {
            startActivityForResult(Intent.createChooser(intent, "Select a file"), FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toaster.show(getContext(), Utils.Messages.NO_FILE_MANAGER, ex);
            return;
        }

        path.setText(null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    Base64Fragment.this.data = data.getData();
                    if (Base64Fragment.this.data != null) setFilename(Base64Fragment.this.data);
                }
                break;
        }
    }

    private void setFilename(@NonNull Uri uri) {
        try (Cursor cursor = getContext().getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.getColumnCount() > 0)
                path.setText(cursor.getString(0));
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        Uri uri = getArguments().getParcelable("uri");
        if (uri != null) {
            this.data = uri;
            setFilename(uri);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout layout = (LinearLayout) inflater.inflate(R.layout.base64_fragment, container, false);
        path = layout.findViewById(R.id.base64Fragment_path);
        Button pick = layout.findViewById(R.id.base64Fragment_pick);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Utils.requestReadPermission(getActivity(), R.string.readExternalStorageRequest_base64Message, 12);
                } else {
                    showFilePicker();
                }
            }
        });

        SuperTextView help = layout.findViewById(R.id.base64Fragment_help);
        if (getArguments().getBoolean("torrent", true)) help.setHtml(R.string.pickTorrent_help);
        else help.setHtml(R.string.pickMetalink_help);

        return layout;
    }

    @Nullable
    public String getBase64() throws InvalidFieldException {
        if (data == null)
            throw new InvalidFieldException(Base64Fragment.class, R.id.base64Fragment_pick, getString(R.string.base64NotSelected));

        try (InputStream in = getContext().getContentResolver().openInputStream(data); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) return null;

            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (IOException ex) {
            Toaster.show(getContext(), Utils.Messages.INVALID_FILE, ex);
            return null;
        }
    }
}
