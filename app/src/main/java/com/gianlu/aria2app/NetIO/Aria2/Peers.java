package com.gianlu.aria2app.NetIO.Aria2;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

public class Peers extends ArrayList<Peer> {

    public Peers(DownloadWithUpdate download, JSONArray array) throws JSONException {
        for (int i = 0; i < array.length(); i++) add(new Peer(download, array.getJSONObject(i)));
    }

    private Peers() {
    }

    public static Peers empty() {
        return new Peers();
    }
}
