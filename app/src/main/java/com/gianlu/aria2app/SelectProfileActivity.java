package com.gianlu.aria2app;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ExpandableListView;

import com.gianlu.aria2app.Google.Analytics;
import com.gianlu.aria2app.Google.CheckerCallback;
import com.gianlu.aria2app.NetIO.JTA2.JTA2;
import com.gianlu.aria2app.SelectProfile.AddProfileActivity;
import com.gianlu.aria2app.SelectProfile.MultiModeProfileItem;
import com.gianlu.aria2app.SelectProfile.ProfileItem;
import com.gianlu.aria2app.SelectProfile.ProfilesCustomAdapter;
import com.gianlu.aria2app.SelectProfile.SingleModeProfileItem;
import com.google.android.gms.analytics.HitBuilders;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SelectProfileActivity extends AppCompatActivity {
    private ExpandableListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_profile);
        setTitle(R.string.title_activity_select_profile);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            CheckerCallback.check(this, getPackageName(), ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId());
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.READ_PHONE_STATE)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.imei_box)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(SelectProfileActivity.this, new String[]{android.Manifest.permission.READ_PHONE_STATE}, 45);
                            }
                        });
            } else {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_PHONE_STATE}, 45);
            }
        }

        listView = (ExpandableListView) findViewById(R.id.selectProfile_listView);


        List<ProfileItem> profiles = new ArrayList<>();
        File files[] = getFilesDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.toLowerCase().endsWith(".profile");
            }
        });

        for (File profile : files) {
            try {
                if (ProfileItem.isSingleMode(this, profile)) {
                    profiles.add(SingleModeProfileItem.fromFile(this, profile));
                } else {
                    profiles.add(MultiModeProfileItem.fromFile(this, profile));
                }
            } catch (FileNotFoundException ex) {
                Utils.UIToast(this, Utils.TOAST_MESSAGES.FILE_NOT_FOUND, ex);
            } catch (JSONException | IOException ex) {
                Utils.UIToast(this, Utils.TOAST_MESSAGES.FATAL_EXCEPTION, ex);
                ex.printStackTrace();
            }
        }

        listView.setAdapter(new ProfilesCustomAdapter(this, listView, profiles, new ProfilesCustomAdapter.OnItemSelected() {
            @Override
            public void onSelected(final String profileName, final ProfileItem item) {
                if (item.getStatus() != ProfileItem.STATUS.ONLINE) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(SelectProfileActivity.this);
                    builder.setMessage(R.string.serverOffline)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    SingleModeProfileItem profile = item.isSingleMode() ? ((SingleModeProfileItem) item) : ((MultiModeProfileItem) item).getCurrentProfile(SelectProfileActivity.this);
                                    Intent intent = new Intent(SelectProfileActivity.this, MainActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                            .putExtra("profileName", profile.getGlobalProfileName())
                                            .putExtra("profile", profile);
                                    startActivity(intent);
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                }
                            }).create().show();
                } else {
                    SingleModeProfileItem profile = item.isSingleMode() ? ((SingleModeProfileItem) item) : ((MultiModeProfileItem) item).getCurrentProfile(SelectProfileActivity.this);
                    Intent intent = new Intent(SelectProfileActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .putExtra("profileName", profile.getGlobalProfileName())
                            .putExtra("profile", profile);
                    startActivity(intent);
                }
            }
        },
                new ProfilesCustomAdapter.OnItemEdit() {
                    @Override
                    public void onEdit(ProfileItem item) {
                        startActivity(new Intent(SelectProfileActivity.this, AddProfileActivity.class)
                                .putExtra("edit", true)
                                .putExtra("isSingleMode", item.isSingleMode())
                                .putExtra("name", item.getGlobalProfileName()));
                    }
                }));

        new Thread(new LoadProfileStatus(this, listView)).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 45:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    CheckerCallback.check(this, getPackageName(), ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId());
                } else {
                    Utils.UIToast(this, Utils.TOAST_MESSAGES.CANT_VERIFY_LICENSE);
                }
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.select_profile, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.selectProfileMenu_add:
                startActivity(new Intent(this, AddProfileActivity.class).putExtra("edit", false));
                break;
            case R.id.selectProfileMenu_refresh:
                new Thread(new LoadProfileStatus(this, listView)).start();

                if (Analytics.isTrackingAllowed(this))
                    Analytics.getDefaultTracker(this.getApplication()).send(new HitBuilders.EventBuilder()
                            .setCategory(Analytics.CATEGORY_USER_INPUT)
                            .setAction(Analytics.ACTION_REFRESH)
                            .build());
                break;
            case R.id.selectProfileMenu_preferences:
                startActivity(new Intent(this, MainSettingsActivity.class));
                finishActivity(0);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    class StatusWebSocketHandler extends WebSocketAdapter {
        private ProfileItem item;
        private Activity context;
        private long startTime;

        StatusWebSocketHandler(Activity context, ProfileItem item) {
            this.context = context;
            this.item = item;
        }

        @Override
        public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
            item.setStatus(SingleModeProfileItem.STATUS.ONLINE);
            item.setStatusMessage("Online");

            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listView.invalidateViews();
                }
            });

            startTime = System.currentTimeMillis();
            websocket.sendPing();
        }

        @Override
        public void onPongFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
            item.setLatency(System.currentTimeMillis() - startTime);
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listView.invalidateViews();
                }
            });
        }

        @Override
        public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
            item.setStatus(SingleModeProfileItem.STATUS.OFFLINE);
            item.setStatusMessage(cause.getMessage());
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listView.invalidateViews();
                }
            });
        }

        @Override
        public void onUnexpectedError(WebSocket websocket, WebSocketException cause) throws Exception {
            Utils.UIToast(context, Utils.TOAST_MESSAGES.WS_EXCEPTION, cause);
            item.setStatus(SingleModeProfileItem.STATUS.ERROR);
            item.setStatusMessage(cause.getMessage());
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listView.invalidateViews();
                }
            });
        }

        @Override
        public void onConnectError(WebSocket websocket, WebSocketException exception) throws Exception {
            if (exception.getCause() instanceof ConnectException)
                item.setStatus(ProfileItem.STATUS.OFFLINE);
            else if (exception.getCause() instanceof SocketTimeoutException)
                item.setStatus(ProfileItem.STATUS.OFFLINE);
            else
                item.setStatus(ProfileItem.STATUS.ERROR);

            item.setStatusMessage(exception.getMessage());
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listView.invalidateViews();
                }
            });
        }

        @Override
        public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
            if (closedByServer) {
                item.setStatus(SingleModeProfileItem.STATUS.ERROR);
                item.setStatusMessage(serverCloseFrame.getCloseReason());
            } else {
                item.setStatusMessage(clientCloseFrame.getCloseReason());
                item.setStatus(SingleModeProfileItem.STATUS.OFFLINE);
            }

            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listView.invalidateViews();
                }
            });
        }
    }

    class LoadProfileStatus implements Runnable {
        private Activity context;
        private ExpandableListView listView;

        public LoadProfileStatus(Activity context, ExpandableListView listView) {
            this.context = context;
            this.listView = listView;
        }

        @Override
        public void run() {
            for (int c = 0; c < listView.getExpandableListAdapter().getGroupCount(); c++) {
                check(((ProfilesCustomAdapter) listView.getExpandableListAdapter()).getGroup(c));

                for (int cc = 0; cc < listView.getExpandableListAdapter().getChildrenCount(c); cc++) {
                    check(((ProfilesCustomAdapter) listView.getExpandableListAdapter()).getChild(c, cc).second);
                }
            }
        }

        private void check(ProfileItem item) {
            try {
                SingleModeProfileItem curr;
                if (item.isSingleMode())
                    curr = (SingleModeProfileItem) item;
                else
                    curr = ((MultiModeProfileItem) item).getCurrentProfile(context);

                WebSocket webSocket;
                if (curr.getAuthMethod().equals(JTA2.AUTH_METHOD.HTTP))
                    webSocket = Utils.readyWebSocket(curr.isServerSSL(), curr.getFullServerAddr(), curr.getServerUsername(), curr.getServerPassword());
                else
                    webSocket = Utils.readyWebSocket(curr.isServerSSL(), curr.getFullServerAddr());

                webSocket.addListener(new StatusWebSocketHandler(context, item))
                        .connectAsynchronously();
            } catch (IOException | NoSuchAlgorithmException ex) {
                item.setStatus(SingleModeProfileItem.STATUS.ERROR);
                item.setStatusMessage(ex.getMessage());

                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listView.invalidateViews();
                    }
                });
            }
        }
    }
}
