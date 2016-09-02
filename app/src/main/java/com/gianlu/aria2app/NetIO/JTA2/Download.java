package com.gianlu.aria2app.NetIO.JTA2;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.gianlu.aria2app.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Download {
    // General
    public String bitfield;
    public boolean isBitTorrent;
    public Long completedLength;
    public Long length;
    public Long uploadedLength;
    public String dir;
    public Integer connections;
    public String gid;
    public Integer numPieces;
    public Long pieceLength;
    public STATUS status;
    public Integer downloadSpeed;
    public Integer uploadSpeed;
    public List<File> files;
    public Integer errorCode;
    public String errorMessage;
    public String followedBy;
    public String following;
    public String belongsTo;
    public Long verifiedLength;
    public boolean verifyIntegrityPending;

    // BitTorrent only
    public boolean seeder;
    public Integer numSeeders;
    public String infoHash;
    public BitTorrent bitTorrent;

    private Download() {
    }

    @Nullable
    private static Integer parseInt(String val) {
        try {
            return Integer.parseInt(val);
        } catch (Exception ex) {
            return 0;
        }
    }

    @Nullable
    private static Long parseLong(String val) {
        try {
            return Long.parseLong(val);
        } catch (Exception ex) {
            return 0L;
        }
    }

    @NonNull
    private static Boolean parseBoolean(String val) {
        try {
            return Boolean.parseBoolean(val);
        } catch (Exception ex) {
            return false;
        }
    }

    public static Download fromJSON(JSONObject jResult) {
        Download download = new Download();
        download.gid = jResult.optString("gid");
        download.status = statusFromString(jResult.optString("status"));
        download.length = parseLong(jResult.optString("totalLength"));
        download.completedLength = parseLong(jResult.optString("completedLength"));
        download.uploadedLength = parseLong(jResult.optString("uploadLength"));
        download.bitfield = jResult.optString("bitfield");
        download.downloadSpeed = parseInt(jResult.optString("downloadSpeed"));
        download.uploadSpeed = parseInt(jResult.optString("uploadSpeed"));
        download.pieceLength = parseLong(jResult.optString("pieceLength"));
        download.numPieces = parseInt(jResult.optString("numPieces"));
        download.connections = parseInt(jResult.optString("connections"));
        download.followedBy = jResult.optString("followedBy");
        download.following = jResult.optString("following");
        download.belongsTo = jResult.optString("belongsTo");
        download.dir = jResult.optString("dir");
        download.verifiedLength = parseLong(jResult.optString("verifiedLength"));
        download.verifyIntegrityPending = parseBoolean(jResult.optString("verifyIntegrityPending"));
        download.files = new ArrayList<>();

        if (!jResult.isNull("files")) {
            JSONArray array = jResult.optJSONArray("files");

            for (int i = 0; i < array.length(); i++)
                download.files.add(File.fromJSON(array.optJSONObject(i)));
        }

        download.isBitTorrent = !jResult.isNull("bittorrent");
        if (!jResult.isNull("bittorrent")) {
            download.infoHash = jResult.optString("infoHash");
            download.numSeeders = parseInt(jResult.optString("numSeeders"));
            download.seeder = parseBoolean(jResult.optString("seeder"));
            download.bitTorrent = BitTorrent.fromJSON(jResult.optJSONObject("bittorrent"));
        }

        if (!jResult.isNull("errorCode")) {
            download.errorCode = parseInt(jResult.optString("errorCode"));
            download.errorMessage = jResult.optString("errorMessage");
        }

        return download;
    }

    public static STATUS statusFromString(String status) {
        if (status == null) return STATUS.UNKNOWN;
        switch (status.toLowerCase()) {
            case "active":
                return STATUS.ACTIVE;
            case "paused":
                return STATUS.PAUSED;
            case "waiting":
                return STATUS.WAITING;
            case "complete":
                return STATUS.COMPLETE;
            case "error":
                return STATUS.ERROR;
            case "removed":
                return STATUS.REMOVED;
            default:
                return STATUS.UNKNOWN;
        }
    }

    public String getName() {
        try {
            if (isBitTorrent) {
                if (bitTorrent != null && bitTorrent.name != null) {
                    return bitTorrent.name;
                } else {
                    String[] splitted = files.get(0).path.split("/");
                    return splitted[splitted.length - 1];
                }
            } else {
                String[] splitted = files.get(0).path.split("/");
                if (splitted.length == 1) {
                    if (files.get(0).uris.get(File.URI_STATUS.USED) != null) {
                        return files.get(0).uris.get(File.URI_STATUS.USED);
                    } else if (files.get(0).uris.get(File.URI_STATUS.WAITING) != null) {
                        return files.get(0).uris.get(File.URI_STATUS.WAITING);
                    } else {
                        return "Unknown";
                    }
                } else {
                    return splitted[splitted.length - 1];
                }
            }
        } catch (Exception ex) {
            return "Unknown";
        }
    }

    public Float getProgress() {
        return completedLength.floatValue() / length.floatValue() * 100;
    }

    public Long getMissingTime() {
        if (downloadSpeed == 0) return null;
        return (length - completedLength) / downloadSpeed;
    }

    public enum STATUS {
        ACTIVE,
        PAUSED,
        REMOVED,
        WAITING,
        ERROR,
        COMPLETE,
        UNKNOWN;

        public String getFormal(Context context, boolean firstCapital) {
            String val;
            switch (this) {
                case ACTIVE:
                    val = context.getString(R.string.downloadStatus_active);
                    break;
                case PAUSED:
                    val = context.getString(R.string.downloadStatus_paused);
                    break;
                case REMOVED:
                    val = context.getString(R.string.downloadStatus_removed);
                    break;
                case WAITING:
                    val = context.getString(R.string.downloadStatus_waiting);
                    break;
                case ERROR:
                    val = context.getString(R.string.downloadStatus_error);
                    break;
                case COMPLETE:
                    val = context.getString(R.string.downloadStatus_complete);
                    break;
                case UNKNOWN:
                default:
                    val = context.getString(R.string.downloadStatus_unknown);
                    break;
            }

            if (firstCapital)
                return val;
            else
                return val.substring(0, 1).toLowerCase() + val.substring(1);
        }
    }
}
