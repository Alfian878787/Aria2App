package com.gianlu.aria2app.NetIO.Aria2;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;

public class BitTorrent extends DownloadChild implements Serializable {
    public final ArrayList<String> announceList;
    public final Mode mode;
    public final String comment;
    public final long creationDate;
    public final String name;

    BitTorrent(DownloadStatic download, @NonNull JSONObject obj) {
        super(download);
        comment = obj.optString("comment", null);
        creationDate = obj.optInt("creationDate", -1);
        mode = Mode.parse(obj.optString("mode"));
        announceList = new ArrayList<>();

        if (obj.has("announceList")) {
            JSONArray array = obj.optJSONArray("announceList");
            for (int i = 0; i < array.length(); i++)
                announceList.add(obj.optJSONArray("announceList").optJSONArray(i).optString(0));
        }

        if (obj.has("info")) name = obj.optJSONObject("info").optString("name");
        else name = null;
    }

    public enum Mode {
        MULTI,
        SINGLE;

        public static Mode parse(@Nullable String val) {
            if (val == null) return Mode.SINGLE;

            switch (val.toLowerCase()) {
                case "multi":
                    return Mode.MULTI;
                case "single":
                    return Mode.SINGLE;
                default:
                    return Mode.SINGLE;
            }
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }
}
