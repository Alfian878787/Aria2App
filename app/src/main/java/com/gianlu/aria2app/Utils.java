package com.gianlu.aria2app;

import android.content.Context;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Base64;

import com.gianlu.aria2app.Main.Profile.SingleModeProfileItem;
import com.gianlu.aria2app.NetIO.JTA2.JTA2;
import com.gianlu.commonutils.CommonUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.net.ssl.SSLContext;

public class Utils {
    public static final int CHART_DOWNLOAD_SET = 1;
    public static final int CHART_UPLOAD_SET = 0;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void renameOldProfiles(Context context) {
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("oldProfiles", true))
            return;

        for (File file : context.getFilesDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.toLowerCase().endsWith(".profile");
            }
        })) {
            if (!file.renameTo(new File(file.getParent(), new String(Base64.encode(file.getName().trim().replace(".profile", "").getBytes(), Base64.NO_WRAP)) + ".profile"))) {
                file.delete();
            }
        }

        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("oldProfiles", false).apply();
    }

    public static LineChart setupChart(LineChart chart, boolean isCardView) {
        chart.clear();

        chart.setDescription(null);
        chart.setDrawGridBackground(false);
        chart.setBackgroundColor(Color.alpha(0));
        chart.setTouchEnabled(false);
        chart.getLegend().setEnabled(true);

        LineData data = new LineData();
        data.setValueTextColor(ContextCompat.getColor(chart.getContext(), R.color.colorPrimaryDark));
        chart.setData(data);

        YAxis ya = chart.getAxisLeft();
        ya.setAxisLineColor(ContextCompat.getColor(chart.getContext(), R.color.colorPrimaryDark));
        ya.setTextColor(ContextCompat.getColor(chart.getContext(), R.color.colorPrimaryDark));
        ya.setTextSize(isCardView ? 8 : 9);
        ya.setAxisMinimum(0);
        ya.setDrawAxisLine(false);
        ya.setLabelCount(isCardView ? 4 : 8, true);
        ya.setEnabled(true);
        ya.setDrawGridLines(true);
        ya.setValueFormatter(new CustomYAxisValueFormatter());

        chart.getAxisRight().setEnabled(false);
        chart.getXAxis().setEnabled(false);

        data.addDataSet(initUploadSet(chart.getContext()));
        data.addDataSet(initDownloadSet(chart.getContext()));

        return chart;
    }

    private static LineDataSet initDownloadSet(Context context) {
        LineDataSet set = new LineDataSet(null, context.getString(R.string.downloadSpeed));
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(2f);
        set.setColor(ContextCompat.getColor(context, R.color.downloadColor));
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setDrawFilled(false);
        return set;
    }

    private static LineDataSet initUploadSet(Context context) {
        LineDataSet set = new LineDataSet(null, context.getString(R.string.uploadSpeed));
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(2f);
        set.setColor(ContextCompat.getColor(context, R.color.uploadColor));
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setDrawFilled(false);
        return set;
    }

    public static String formatConnectionError(int code, String message) {
        return "#" + code + ": " + message;
    }

    @NonNull
    public static List<Integer> bitfieldProcessor(int numPieces, String bitfield) {
        List<Integer> pieces = new ArrayList<>();

        for (char hexChar : bitfield.toLowerCase().toCharArray()) {
            switch (hexChar) {
                case '0':
                    pieces.add(0);
                    break;
                case '1':
                case '2':
                case '4':
                case '8':
                    pieces.add(1);
                    break;
                case '3':
                case '5':
                case '6':
                case '9':
                case 'a':
                case 'c':
                    pieces.add(2);
                    break;
                case '7':
                case 'b':
                case 'd':
                case 'e':
                    pieces.add(3);
                    break;
                case 'f':
                    pieces.add(4);
                    break;
            }
        }

        try {
            return pieces.subList(0, (numPieces / 4) - 1);
        } catch (Exception ex) {
            return pieces;
        }
    }

    public static int mapAlpha(int val) {
        return 255 / 4 * val;
    }

    public static WebSocket readyWebSocket(boolean isSSL, String url, @NonNull String username, @NonNull String password) throws IOException, NoSuchAlgorithmException {
        if (isSSL) {
            WebSocketFactory factory = new WebSocketFactory();
            factory.setSSLContext(SSLContext.getDefault());

            return factory.createSocket(url.replace("ws://", "wss://"), 5000)
                    .addHeader("Authorization", "Basic " + Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP));
        } else {
            return new WebSocketFactory().createSocket(url, 5000)
                    .addHeader("Authorization", "Basic " + Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP));
        }
    }

    public static WebSocket readyWebSocket(boolean isSSL, String url) throws NoSuchAlgorithmException, IOException {
        if (isSSL) {
            return new WebSocketFactory()
                    .setSSLContext(SSLContext.getDefault())
                    .setConnectionTimeout(5000)
                    .createSocket(url.replace("ws://", "wss://"), 5000);
        } else {
            return new WebSocketFactory()
                    .setConnectionTimeout(5000)
                    .createSocket(url, 5000);
        }
    }

    public static WebSocket readyWebSocket(Context context) throws IOException, NoSuchAlgorithmException {
        SingleModeProfileItem profile = CurrentProfile.getCurrentProfile(context);

        if (profile.isServerSSL()) {
            WebSocketFactory factory = new WebSocketFactory()
                    .setSSLContext(SSLContext.getDefault())
                    .setConnectionTimeout(5000);
            WebSocket socket = factory.createSocket("wss://" + profile.getServerAddr() + ":" + profile.getServerPort() + profile.getServerEndpoint(), 5000);

            if (profile.getAuthMethod() == JTA2.AUTH_METHOD.HTTP)
                socket.addHeader("Authorization", "Basic " + Base64.encodeToString((profile.getServerUsername() + ":" + profile.getServerPassword()).getBytes(), Base64.NO_WRAP));

            return socket;
        } else {
            WebSocket socket = new WebSocketFactory()
                    .setConnectionTimeout(5000)
                    .createSocket("ws://" + profile.getServerAddr() + ":" + profile.getServerPort() + profile.getServerEndpoint(), 5000);

            if (profile.getAuthMethod() == JTA2.AUTH_METHOD.HTTP)
                socket.addHeader("Authorization", "Basic " + Base64.encodeToString((profile.getServerUsername() + ":" + profile.getServerPassword()).getBytes(), Base64.NO_WRAP));

            System.out.println(socket.getURI());

            return socket;
        }
    }

    public static JSONArray readyParams(Context context) {
        JSONArray array = new JSONArray();
        if (CurrentProfile.getCurrentProfile(context).getAuthMethod() == JTA2.AUTH_METHOD.TOKEN)
            array.put("token:" + CurrentProfile.getCurrentProfile(context).getServerToken());

        return array;
    }

    public static JSONObject readyRequest() throws JSONException {
        return new JSONObject().put("jsonrpc", "2.0").put("id", String.valueOf(new Random().nextInt(9999)));
    }

    public static class ToastMessages {
        public static final CommonUtils.ToastMessage WS_EXCEPTION = new CommonUtils.ToastMessage("WebSocket exception!", true);
        public static final CommonUtils.ToastMessage FAILED_GATHERING_INFORMATION = new CommonUtils.ToastMessage("Failed on gathering information!", true);
        public static final CommonUtils.ToastMessage PAUSED = new CommonUtils.ToastMessage("Download paused.", false);
        public static final CommonUtils.ToastMessage REMOVED = new CommonUtils.ToastMessage("Download removed.", false);
        public static final CommonUtils.ToastMessage WRITE_STORAGE_DENIED = new CommonUtils.ToastMessage("Cannot download. You denied the write permission!", false);
        public static final CommonUtils.ToastMessage REMOVED_RESULT = new CommonUtils.ToastMessage("Download result removed.", false);
        public static final CommonUtils.ToastMessage MOVED = new CommonUtils.ToastMessage("Download moved.", false);
        public static final CommonUtils.ToastMessage FAILED_CONNECTION_BILLING_SERVICE = new CommonUtils.ToastMessage("Failed to connect to the billing service!", true);
        public static final CommonUtils.ToastMessage FAILED_BUYING_ITEM = new CommonUtils.ToastMessage("Failed to buy this item! Please contact me.", true);
        public static final CommonUtils.ToastMessage RESUMED = new CommonUtils.ToastMessage("Download resumed.", false);
        public static final CommonUtils.ToastMessage RESTARTED = new CommonUtils.ToastMessage("Download restarted.", false);
        public static final CommonUtils.ToastMessage CHANGED_SELECTION = new CommonUtils.ToastMessage("File selected/deselected.", false);
        public static final CommonUtils.ToastMessage SESSION_SAVED = new CommonUtils.ToastMessage("Session saved correctly.", false);
        public static final CommonUtils.ToastMessage FAILED_SAVE_SESSION = new CommonUtils.ToastMessage("Failed saving current session!", true);
        public static final CommonUtils.ToastMessage FAILED_PAUSE = new CommonUtils.ToastMessage("Failed to pause download!", true);
        public static final CommonUtils.ToastMessage MUST_CREATE_FIRST_PROFILE = new CommonUtils.ToastMessage("You must create your first profile to run the application!", false);
        public static final CommonUtils.ToastMessage CANNOT_EDIT_PROFILE = new CommonUtils.ToastMessage("Cannot edit this profile!", true);
        public static final CommonUtils.ToastMessage PROFILE_DOES_NOT_EXIST = new CommonUtils.ToastMessage("Profile doesn't exist!", true);
        public static final CommonUtils.ToastMessage FAILED_REMOVE = new CommonUtils.ToastMessage("Failed to remove download!", true);
        public static final CommonUtils.ToastMessage FAILED_UNPAUSE = new CommonUtils.ToastMessage("Failed to resume download!", true);
        public static final CommonUtils.ToastMessage FAILED_REMOVE_RESULT = new CommonUtils.ToastMessage("Failed to remove download's result!", true);
        public static final CommonUtils.ToastMessage FAILED_ADD_DOWNLOAD = new CommonUtils.ToastMessage("Failed to add new download!", true);
        public static final CommonUtils.ToastMessage FAILED_CHANGE_OPTIONS = new CommonUtils.ToastMessage("Failed to change options for download!", true);
        public static final CommonUtils.ToastMessage DOWNLOAD_OPTIONS_CHANGED = new CommonUtils.ToastMessage("Download options successfully changed!", false);
        public static final CommonUtils.ToastMessage FAILED_CHANGE_POSITION = new CommonUtils.ToastMessage("Failed changing download's queue position!", true);
        public static final CommonUtils.ToastMessage FAILED_CHANGE_FILE_SELECTION = new CommonUtils.ToastMessage("Failed selecting/deselecting file!", true);
        public static final CommonUtils.ToastMessage FAILED_CHECKING_VERSION = new CommonUtils.ToastMessage("Failed checking aria2 version!", true);
        public static final CommonUtils.ToastMessage INVALID_REQUEST = new CommonUtils.ToastMessage("Invalid request format! Please review your JSON.", false);
        public static final CommonUtils.ToastMessage INVALID_PROFILE_NAME = new CommonUtils.ToastMessage("Invalid profile name!", false);
        public static final CommonUtils.ToastMessage INVALID_SERVER_IP = new CommonUtils.ToastMessage("Invalid server address!", false);
        public static final CommonUtils.ToastMessage INVALID_SERVER_PORT = new CommonUtils.ToastMessage("Invalid server port, must be > 0 and < 65536!", false);
        public static final CommonUtils.ToastMessage INVALID_SERVER_ENDPOINT = new CommonUtils.ToastMessage("Invalid server RPC endpoint!", false);
        public static final CommonUtils.ToastMessage INVALID_SERVER_TOKEN = new CommonUtils.ToastMessage("Invalid server token!", false);
        public static final CommonUtils.ToastMessage INVALID_SERVER_USER_OR_PASSWD = new CommonUtils.ToastMessage("Invalid username or password!", false);
        public static final CommonUtils.ToastMessage INVALID_CONDITIONS_NUMBER = new CommonUtils.ToastMessage("Multi profile should contains more than one condition", false);
        public static final CommonUtils.ToastMessage FILE_NOT_FOUND = new CommonUtils.ToastMessage("File not found!", true);
        public static final CommonUtils.ToastMessage FATAL_EXCEPTION = new CommonUtils.ToastMessage("Fatal exception!", true);
        public static final CommonUtils.ToastMessage FAILED_LOADING_AUTOCOMPLETION = new CommonUtils.ToastMessage("Unable to load method's suggestions!", true);
        public static final CommonUtils.ToastMessage FAILED_EDIT_CONVERSATION_ITEM = new CommonUtils.ToastMessage("Failed editing that item!", true);
        public static final CommonUtils.ToastMessage NO_EMAIL_CLIENT = new CommonUtils.ToastMessage("There are no email clients installed.", true);
        public static final CommonUtils.ToastMessage INVALID_SSID = new CommonUtils.ToastMessage("Invalid SSID!", false);
        public static final CommonUtils.ToastMessage MUST_PICK_DEFAULT = new CommonUtils.ToastMessage("You must select one profile as default!", false);
        public static final CommonUtils.ToastMessage INVALID_DIRECTDOWNLOAD_ADDR = new CommonUtils.ToastMessage("Invalid DirectDownload's server address!", false);
        public static final CommonUtils.ToastMessage INVALID_DIRECTDOWNLOAD_USERORPASSWD = new CommonUtils.ToastMessage("Invalid DirectDownload's username or password!", false);
        public static final CommonUtils.ToastMessage CANT_REFRESH_SOURCE = new CommonUtils.ToastMessage("Can't refresh source file for options. Retry later...", true);
        public static final CommonUtils.ToastMessage ADD_QUICK_OPTIONS = new CommonUtils.ToastMessage("You have no quick options!", false);
        public static final CommonUtils.ToastMessage SOURCE_REFRESHED = new CommonUtils.ToastMessage("Source file for options refreshed!", false);
        public static final CommonUtils.ToastMessage PURCHASING_CANCELED = new CommonUtils.ToastMessage("The purchase has been canceled.", false);
        public static final CommonUtils.ToastMessage BILLING_USER_CANCELLED = new CommonUtils.ToastMessage("You cancelled the operation.", false);
        public static final CommonUtils.ToastMessage THANK_YOU = new CommonUtils.ToastMessage("Thank you!", false);
    }

    private static class CustomYAxisValueFormatter implements IAxisValueFormatter {
        @Override
        public String getFormattedValue(float value, AxisBase axis) {
            return CommonUtils.speedFormatter(value);
        }

        @Override
        public int getDecimalDigits() {
            return 1;
        }
    }
}