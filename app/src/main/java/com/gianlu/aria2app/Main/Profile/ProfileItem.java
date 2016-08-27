package com.gianlu.aria2app.Main.Profile;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProfileItem implements Parcelable {
    public static final Creator<ProfileItem> CREATOR = new Creator<ProfileItem>() {
        @Override
        public ProfileItem createFromParcel(Parcel in) {
            return new ProfileItem(in);
        }

        @Override
        public ProfileItem[] newArray(int size) {
            return new ProfileItem[size];
        }
    };
    protected String globalProfileName;
    protected boolean singleMode;
    protected STATUS status = STATUS.UNKNOWN;
    protected String statusMessage;
    protected boolean isDefault;
    private Long latency = -1L;

    protected ProfileItem(Parcel in) {
        globalProfileName = in.readString();
        singleMode = in.readByte() != 0;
        status = STATUS.valueOf(in.readString());
        statusMessage = in.readString();
        isDefault = in.readByte() != 0;
        latency = in.readLong();
    }

    public ProfileItem() {
    }

    public static boolean exists(Context context, String name) {
        return name != null && exists(context, new File(name + ".profile"));
    }

    public static boolean exists(Context context, File file) {
        if (file == null) return false;
        try {
            context.openFileInput(file.getName());
            return true;
        } catch (FileNotFoundException ex) {
            return false;
        }
    }

    public static boolean isSingleMode(Context context, File file) throws JSONException, IOException {
        FileInputStream in = context.openFileInput(file.getName());
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder builder = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }

        return new JSONObject(builder.toString()).isNull("conditions");
    }

    public static boolean isSingleMode(Context context, String name) throws JSONException, IOException {
        return isSingleMode(context, new File(name + ".profile"));
    }

    public STATUS getStatus() {
        return status;
    }

    public void setStatus(STATUS status) {
        this.status = status;
    }

    public String getGlobalProfileName() {
        return globalProfileName;
    }

    public boolean isSingleMode() {
        return singleMode;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public Long getLatency() {
        return latency;
    }

    public void setLatency(Long latency) {
        this.latency = latency;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(globalProfileName);
        parcel.writeByte((byte) (singleMode ? 1 : 0));
        parcel.writeString(status.name());
        parcel.writeString(statusMessage);
        parcel.writeByte((byte) (isDefault ? 1 : 0));
        parcel.writeLong(latency);
    }

    public enum STATUS {
        ONLINE,
        OFFLINE,
        ERROR,
        UNKNOWN
    }
}
