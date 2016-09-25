package com.gianlu.aria2app;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.gianlu.aria2app.Google.Analytics;
import com.gianlu.aria2app.Google.UncaughtExceptionHandler;
import com.gianlu.aria2app.Main.AddTorrentActivity;
import com.gianlu.aria2app.Main.AddURIActivity;
import com.gianlu.aria2app.Main.DrawerManager;
import com.gianlu.aria2app.Main.IThread;
import com.gianlu.aria2app.Main.LoadDownloads;
import com.gianlu.aria2app.Main.MainCardAdapter;
import com.gianlu.aria2app.Main.Profile.AddProfileActivity;
import com.gianlu.aria2app.Main.Profile.MultiModeProfileItem;
import com.gianlu.aria2app.Main.Profile.ProfileItem;
import com.gianlu.aria2app.Main.Profile.SingleModeProfileItem;
import com.gianlu.aria2app.Main.UpdateUI;
import com.gianlu.aria2app.NetIO.AsyncRequest;
import com.gianlu.aria2app.NetIO.IResponse;
import com.gianlu.aria2app.NetIO.JTA2.Download;
import com.gianlu.aria2app.NetIO.JTA2.ISession;
import com.gianlu.aria2app.NetIO.JTA2.ISuccess;
import com.gianlu.aria2app.NetIO.JTA2.IVersion;
import com.gianlu.aria2app.NetIO.JTA2.JTA2;
import com.gianlu.aria2app.NetIO.WebSocketing;
import com.gianlu.aria2app.Options.OptionsDialog;
import com.gianlu.aria2app.Services.NotificationWebSocketService;
import com.google.android.gms.analytics.HitBuilders;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    private RecyclerView mainRecyclerView;
    private DrawerManager drawerManager;
    private FloatingActionsMenu fabMenu;
    private LoadDownloads.ILoading loadingHandler;
    private UpdateUI updateUI;
    private LoadDownloads loadDownloads;
    private MainCardAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler(this));

        Utils.logCleaner(this);
        Utils.renameOldProfiles(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        assert toolbar != null;
        setSupportActionBar(toolbar);

        drawerManager = new DrawerManager(this, (DrawerLayout) findViewById(R.id.main_drawer));
        drawerManager.setToolbar(toolbar)
                .buildProfiles()
                .buildMenu()
                .setDrawerListener(new DrawerManager.IDrawerListener() {
                    @Override
                    public boolean onListItemSelected(DrawerManager.DrawerListItems which) {
                        switch (which) {
                            case HOME:
                                reloadPage();
                                return true;
                            case TERMINAL:
                                startActivity(new Intent(MainActivity.this, TerminalActivity.class));
                                return false;
                            case GLOBAL_OPTIONS:
                                // TODO: Hide hearts
                                new OptionsDialog(MainActivity.this, R.array.globalOptions, false, new OptionsDialog.IDialog() {
                                    @Override
                                    public void onApply(JTA2 jta2, Map<String, String> options) {
                                        if (options.entrySet().size() == 0) return;

                                        final ProgressDialog pd = Utils.fastIndeterminateProgressDialog(MainActivity.this, R.string.gathering_information);
                                        Utils.showDialog(MainActivity.this, pd);

                                        if (Analytics.isTrackingAllowed(MainActivity.this))
                                            Analytics.getDefaultTracker(getApplication()).send(new HitBuilders.EventBuilder()
                                                    .setCategory(Analytics.CATEGORY_USER_INPUT)
                                                    .setAction(Analytics.ACTION_CHANGED_GLOBAL_OPTIONS)
                                                    .build());

                                        jta2.changeGlobalOption(options, new ISuccess() {
                                            @Override
                                            public void onSuccess() {
                                                pd.dismiss();
                                                Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.DOWNLOAD_OPTIONS_CHANGED);
                                            }

                                            @Override
                                            public void onException(Exception exception) {
                                                pd.dismiss();
                                                Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_CHANGE_OPTIONS, exception);
                                            }
                                        });
                                    }
                                }).hideHearts().showDialog();
                                return true;
                            case PREFERENCES:
                                startActivity(new Intent(MainActivity.this, PreferencesActivity.class));
                                return false;
                            case SUPPORT:
                                Intent i = new Intent(Intent.ACTION_SEND);
                                i.setType("message/rfc822");
                                i.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.email)});
                                i.putExtra(Intent.EXTRA_SUBJECT, "Aria2App");
                                i.putExtra(Intent.EXTRA_TEXT, "OS Version: " + System.getProperty("os.version") + "(" + android.os.Build.VERSION.INCREMENTAL + ")" +
                                        "\nOS API Level: " + android.os.Build.VERSION.SDK_INT +
                                        "\nDevice: " + android.os.Build.DEVICE +
                                        "\nModel (and Product): " + android.os.Build.MODEL + " (" + android.os.Build.PRODUCT + ")");
                                try {
                                    startActivity(Intent.createChooser(i, "Send mail to the developer..."));
                                } catch (android.content.ActivityNotFoundException ex) {
                                    Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.NO_EMAIL_CLIENT);
                                }
                                return true;
                            case ABOUT_ARIA2:
                                final JTA2 jta2;
                                try {
                                    jta2 = JTA2.newInstance(MainActivity.this);
                                } catch (IOException | NoSuchAlgorithmException ex) {
                                    Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_GATHERING_INFORMATION, ex);
                                    return true;
                                }

                                final ProgressDialog pd = Utils.fastIndeterminateProgressDialog(MainActivity.this, R.string.gathering_information);
                                Utils.showDialog(MainActivity.this, pd);
                                jta2.getVersion(new IVersion() {
                                    @Override
                                    public void onVersion(List<String> rawFeatures, String version) {
                                        final LinearLayout box = new LinearLayout(MainActivity.this);
                                        box.setOrientation(LinearLayout.VERTICAL);
                                        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
                                        box.setPadding(padding, padding, padding, padding);


                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                            box.addView(Utils.fastTextView(MainActivity.this, Html.fromHtml(getString(R.string.version, version), Html.FROM_HTML_MODE_COMPACT)));
                                        } else {
                                            //noinspection deprecation
                                            box.addView(Utils.fastTextView(MainActivity.this, Html.fromHtml(getString(R.string.version, version))));
                                        }

                                        String extendedList = "";
                                        boolean first = true;
                                        for (String _feature : rawFeatures) {
                                            if (!first)
                                                extendedList += ", ";
                                            extendedList += _feature;

                                            first = false;
                                        }

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                            box.addView(Utils.fastTextView(MainActivity.this, Html.fromHtml(getString(R.string.features, extendedList), Html.FROM_HTML_MODE_COMPACT)));
                                        } else {
                                            //noinspection deprecation
                                            box.addView(Utils.fastTextView(MainActivity.this, Html.fromHtml(getString(R.string.features, extendedList))));

                                        }

                                        jta2.getSessionInfo(new ISession() {
                                            @Override
                                            public void onSessionInfo(String sessionID) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                                    box.addView(Utils.fastTextView(MainActivity.this, Html.fromHtml(getString(R.string.sessionId, sessionID), Html.FROM_HTML_MODE_COMPACT)));
                                                } else {
                                                    //noinspection deprecation
                                                    box.addView(Utils.fastTextView(MainActivity.this, Html.fromHtml(getString(R.string.sessionId, sessionID))));
                                                }

                                                pd.dismiss();
                                                Utils.showDialog(MainActivity.this, new AlertDialog.Builder(MainActivity.this).setTitle(R.string.about_aria2)
                                                        .setView(box)
                                                        .setNeutralButton(R.string.saveSession, new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                                jta2.saveSession(new ISuccess() {
                                                                    @Override
                                                                    public void onSuccess() {
                                                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.SESSION_SAVED);
                                                                    }

                                                                    @Override
                                                                    public void onException(Exception exception) {
                                                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_SAVE_SESSION, exception);
                                                                    }
                                                                });
                                                            }
                                                        })
                                                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                            }
                                                        }));
                                            }

                                            @Override
                                            public void onException(Exception exception) {
                                                Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_GATHERING_INFORMATION, exception);
                                                pd.dismiss();
                                            }
                                        });
                                    }

                                    @Override
                                    public void onException(Exception exception) {
                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_GATHERING_INFORMATION, exception);
                                        pd.dismiss();
                                    }
                                });
                                return true;
                            default:
                                return true;
                        }
                    }

                    @Override
                    public void onProfileItemSelected(final SingleModeProfileItem profile, boolean fromRecent) {
                        if (!fromRecent && profile.getStatus() != ProfileItem.STATUS.ONLINE) {
                            Utils.showDialog(MainActivity.this, new AlertDialog.Builder(MainActivity.this).setMessage(R.string.serverOffline)
                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            startWithProfile(profile, true);
                                            drawerManager.setDrawerState(false, true);
                                        }
                                    })
                                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            drawerManager.setDrawerState(true, true);
                                        }
                                    }));
                        } else {
                            drawerManager.setDrawerState(false, true);
                            startWithProfile(profile, true);
                        }
                    }

                    @Override
                    public void onAddProfile() {
                        startActivity(new Intent(MainActivity.this, AddProfileActivity.class)
                                .putExtra("edit", false));
                    }
                });

        mainRecyclerView = (RecyclerView) findViewById(R.id.main_recyclerView);
        assert mainRecyclerView != null;

        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        mainRecyclerView.setLayoutManager(llm);

        final SwipeRefreshLayout swipeLayout = (SwipeRefreshLayout) findViewById(R.id.main_swipeLayout);
        assert swipeLayout != null;

        swipeLayout.setColorSchemeResources(R.color.colorAccent, R.color.colorMetalink, R.color.colorTorrent);
        swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                reloadPage();
            }
        });

        UpdateUI.stop(updateUI);

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (getIntent().getBooleanExtra("external", false)) {
            setTitle(getString(R.string.app_name) + " - Local device");

            saveExternalProfile(new SingleModeProfileItem("Local device",
                    "localhost",
                    getIntent().getIntExtra("port", 6800),
                    "/jsonrpc",
                    false,
                    getIntent().getStringExtra("token"),
                    false,
                    null)
                    .setGlobalProfileName("Local device"));
        }

        try {
            SingleModeProfileItem profile;
            if (getIntent().getBooleanExtra("external", false)) {
                profile = SingleModeProfileItem.fromString(this, "Local device");
            } else if (!drawerManager.hasProfiles()) {
                profile = null;
                startActivity(new Intent(MainActivity.this, AddProfileActivity.class)
                        .putExtra("canGoBack", false)
                        .putExtra("edit", false));
            } else {
                profile = defaultProfile();
            }

            if (profile == null) {
                drawerManager.openProfiles(true);
                return;
            }

            setTitle(getString(R.string.app_name) + " - " + profile.getGlobalProfileName());

            startWithProfile(profile, false);
        } catch (IOException | JSONException ex) {
            Utils.UIToast(this, Utils.TOAST_MESSAGES.FATAL_EXCEPTION, ex);
        }


        Integer autoReloadDownloadsListRate = Integer.parseInt(sharedPreferences.getString("a2_downloadListRate", "0")) * 1000;
        boolean enableNotifications = sharedPreferences.getBoolean("a2_enableNotifications", true);

        fabMenu = (FloatingActionsMenu) findViewById(R.id.main_fab);
        assert fabMenu != null;
        fabMenu.setOnFloatingActionsMenuUpdateListener(new FloatingActionsMenu.OnFloatingActionsMenuUpdateListener() {
            @Override
            public void onMenuExpanded() {
                final View mask = findViewById(R.id.main_mask);
                assert mask != null;
                mask.setVisibility(View.VISIBLE);
                mask.setClickable(true);
                mask.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        fabMenu.collapse();
                    }
                });
            }

            @Override
            public void onMenuCollapsed() {
                final View mask = findViewById(R.id.main_mask);
                assert mask != null;
                mask.setVisibility(View.GONE);
                mask.setClickable(false);
            }
        });

        FloatingActionButton fabAddURI = (FloatingActionButton) findViewById(R.id.mainFab_addURI);
        assert fabAddURI != null;
        fabAddURI.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, AddURIActivity.class));
            }
        });
        final FloatingActionButton fabAddTorrent = (FloatingActionButton) findViewById(R.id.mainFab_addTorrent);
        assert fabAddTorrent != null;
        fabAddTorrent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, AddTorrentActivity.class).putExtra("torrentMode", true));
            }
        });
        final FloatingActionButton fabAddMetalink = (FloatingActionButton) findViewById(R.id.mainFab_addMetalink);
        assert fabAddMetalink != null;
        fabAddMetalink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, AddTorrentActivity.class).putExtra("torrentMode", false));
            }
        });

        if (getIntent().getBooleanExtra("backFromAddProfile", false)) {
            drawerManager.buildProfiles().openProfiles(false);
        }

        final AtomicBoolean shouldReport = new AtomicBoolean(true);
        final ProgressDialog pd = Utils.fastIndeterminateProgressDialog(this, R.string.loading_downloads);
        pd.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                shouldReport.set(false);
                drawerManager.openProfiles(true);
                dialog.dismiss();
            }
        });
        loadingHandler = new LoadDownloads.ILoading() {
            @Override
            public void onStarted() {
                if (!shouldReport.get()) return;

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (MainActivity.this.isFinishing()) return;

                        pd.show();
                        pd.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE);
                    }
                });

                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (!pd.isShowing()) return;

                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pd.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }, 2000);
            }

            @Override
            public void onLoaded(JTA2 jta2, final List<Download> downloads) {
                if (!shouldReport.get()) return;

                adapter = new MainCardAdapter(MainActivity.this, downloads, new MainCardAdapter.IActionMore() {
                    @Override
                    public void onClick(Download item) {
                        Intent launchActivity = new Intent(MainActivity.this, MoreAboutDownloadActivity.class)
                                .putExtra("gid", item.gid)
                                .putExtra("name", item.getName())
                                .putExtra("isTorrent", item.isBitTorrent)
                                .putExtra("status", item.status.name());
                        if (!(item.status.equals(Download.STATUS.UNKNOWN) || item.status.equals(Download.STATUS.ERROR)))
                            MainActivity.this.startActivity(launchActivity);
                    }
                }, new MainCardAdapter.IMenuSelected() {
                    @Override
                    public void onItemSelected(Download item, DownloadAction.ACTION action) {
                        DownloadAction downloadAction;
                        try {
                            downloadAction = new DownloadAction(MainActivity.this);
                        } catch (IOException | NoSuchAlgorithmException ex) {
                            Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.WS_EXCEPTION, ex);
                            return;
                        }

                        DownloadAction.IMove iMove = new DownloadAction.IMove() {
                            @Override
                            public void onMoved(String gid) {
                                Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.MOVED, gid);
                            }

                            @Override
                            public void onException(Exception ex) {
                                Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_CHANGE_POSITION, ex);
                            }
                        };

                        switch (action) {
                            case PAUSE:
                                downloadAction.pause(MainActivity.this, item.gid, new DownloadAction.IPause() {
                                    @Override
                                    public void onPaused(String gid) {
                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.PAUSED, gid);
                                    }

                                    @Override
                                    public void onException(Exception ex) {
                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_PAUSE, ex);
                                    }
                                });
                                break;
                            case REMOVE:
                                downloadAction.remove(MainActivity.this, item.gid, item.status, new DownloadAction.IRemove() {
                                    @Override
                                    public void onRemoved(String gid) {
                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.REMOVED, gid);
                                    }

                                    @Override
                                    public void onRemovedResult(String gid) {
                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.REMOVED_RESULT, gid);
                                    }

                                    @Override
                                    public void onException(boolean b, Exception ex) {
                                        if (b)
                                            Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_REMOVE, ex);
                                        else
                                            Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_REMOVE_RESULT, ex);
                                    }
                                });
                                break;
                            case RESTART:
                                downloadAction.restart(item.gid, new DownloadAction.IRestart() {
                                    @Override
                                    public void onRestarted() {
                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.RESTARTED);
                                    }

                                    @Override
                                    public void onException(Exception ex) {
                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_ADD_DOWNLOAD, ex);
                                    }

                                    @Override
                                    public void onRemoveResultException(Exception ex) {
                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_REMOVE_RESULT, ex);
                                    }

                                    @Override
                                    public void onGatheringInformationException(Exception ex) {
                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_GATHERING_INFORMATION, ex);
                                    }
                                });
                                break;
                            case RESUME:
                                downloadAction.unpause(item.gid, new DownloadAction.IUnpause() {
                                    @Override
                                    public void onUnpaused(String gid) {
                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.RESUMED, gid);
                                    }

                                    @Override
                                    public void onException(Exception ex) {
                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_UNPAUSE, ex);
                                    }
                                });
                                break;
                            case MOVE_DOWN:
                                downloadAction.moveDown(item.gid, iMove);
                                break;
                            case MOVE_UP:
                                downloadAction.moveUp(item.gid, iMove);
                                break;
                        }
                    }
                });

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mainRecyclerView.setAdapter(adapter);
                        drawerManager.updateBadge(downloads.size());

                        updateUI = new UpdateUI(MainActivity.this, (MainCardAdapter) mainRecyclerView.getAdapter());
                        new Thread(updateUI).start();

                        try {
                            pd.dismiss();
                            swipeLayout.setRefreshing(false);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }

                        if (getIntent().getStringExtra("gid") != null) {
                            Download item = ((MainCardAdapter) mainRecyclerView.getAdapter()).getItem(getIntent().getStringExtra("gid"));
                            if (item == null) return;

                            Intent launchActivity = new Intent(MainActivity.this, MoreAboutDownloadActivity.class)
                                    .putExtra("gid", item.gid)
                                    .putExtra("isTorrent", item.isBitTorrent)
                                    .putExtra("status", item.status.name())
                                    .putExtra("name", item.getName());

                            if (item.status == Download.STATUS.UNKNOWN) return;
                            startActivity(launchActivity);
                        }
                    }
                });

                if (sharedPreferences.getBoolean("a2_runVersionCheckAtStartup", true)) {
                    jta2.getVersion(new IVersion() {
                        @Override
                        public void onVersion(List<String> rawFeatures, final String version) {
                            new Thread(new AsyncRequest(getString(R.string.versionCheckURL), new IResponse() {
                                @Override
                                public void onResponse(String response) {
                                    String latest;
                                    try {
                                        latest = new JSONArray(response).getJSONObject(0).getString("name");
                                        latest = latest.replace("aria2 ", "");
                                    } catch (JSONException ex) {
                                        Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_CHECKING_VERSION, ex);
                                        return;
                                    }

                                    if (!Objects.equals(latest, version)) {
                                        Utils.showDialog(MainActivity.this, new AlertDialog.Builder(MainActivity.this)
                                                .setTitle(R.string.dialogVersionCheck)
                                                .setMessage(R.string.dialogVersionCheckMessage)
                                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialogInterface, int i) {
                                                    }
                                                }));
                                    }
                                }

                                @Override
                                public void onException(Exception exception) {
                                    Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_CHECKING_VERSION, exception);
                                }

                                @Override
                                public void onFailed(int code, String message) {
                                    Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_CHECKING_VERSION, Utils.formatConnectionError(code, message));
                                }
                            })).start();
                        }

                        @Override
                        public void onException(Exception exception) {
                            Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_CHECKING_VERSION, exception);
                        }
                    });
                }
            }

            @Override
            public void onException(boolean queuing, final Exception ex) {
                if (!shouldReport.get()) return;

                if (queuing) {
                    WebSocketing.notifyConnection(new WebSocketing.IConnecting() {
                        @Override
                        public void onDone() {
                            loadDownloads = new LoadDownloads(MainActivity.this, loadingHandler);
                            new Thread(loadDownloads).start();
                        }
                    });
                    return;
                }

                try {
                    pd.dismiss();
                    swipeLayout.setRefreshing(false);
                } catch (Exception exx) {
                    exx.printStackTrace();
                }

                Utils.showDialog(MainActivity.this, new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.noCommunication)
                        .setCancelable(false)
                        .setMessage(getString(R.string.noCommunication_message, ex.getMessage()))
                        .setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                recreate();
                            }
                        })
                        .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                System.exit(0);
                            }
                        })
                        .setNeutralButton(R.string.changeProfile, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                drawerManager.openProfiles(true);
                            }
                        }));

                Utils.UIToast(MainActivity.this, Utils.TOAST_MESSAGES.FAILED_GATHERING_INFORMATION, ex, new Runnable() {
                    @Override
                    public void run() {
                        drawerManager.updateBadge(-1);
                    }
                });
            }
        };

        if (autoReloadDownloadsListRate != 0) {
            Timer reloadDownloadsListTimer = new Timer(false);
            reloadDownloadsListTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    UpdateUI.stop(updateUI, new IThread() {
                        @Override
                        public void stopped() {
                            loadDownloads = new LoadDownloads(MainActivity.this, loadingHandler);
                            new Thread(loadDownloads).start();
                        }
                    });
                }
            }, 1000, autoReloadDownloadsListRate);
        } else {
            loadDownloads = new LoadDownloads(this, loadingHandler);
            new Thread(loadDownloads).start();
        }

        try {
            WebSocketing.enableEventManager(this);
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        if (enableNotifications) {
            Intent startNotification = NotificationWebSocketService.createStartIntent(this, sharedPreferences.getString("lastUsedProfile", null));
            if (startNotification != null)
                startService(startNotification);
            else
                Utils.UIToast(this, Utils.TOAST_MESSAGES.FATAL_EXCEPTION, "NULL notification intent");
        } else {
            stopService(new Intent(this, NotificationWebSocketService.class));
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerManager.onTogglerConfigurationChanged(newConfig);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerManager.syncTogglerState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (drawerManager != null)
            drawerManager.syncTogglerState();
    }

    @Nullable
    private SingleModeProfileItem defaultProfile() throws IOException, JSONException {
        String lastProfile = PreferenceManager.getDefaultSharedPreferences(this).getString("lastUsedProfile", null);

        if (ProfileItem.exists(this, lastProfile)) {
            if (ProfileItem.isSingleMode(this, lastProfile))
                return SingleModeProfileItem.fromString(this, lastProfile);
            else
                return MultiModeProfileItem.fromString(this, lastProfile).getCurrentProfile(this);
        } else {
            return null;
        }
    }

    private void saveExternalProfile(@NonNull SingleModeProfileItem profile) {
        try {
            deleteFile("Local device.profile");

            FileOutputStream fOut = openFileOutput("Local device.profile", Context.MODE_PRIVATE);
            OutputStreamWriter osw = new OutputStreamWriter(fOut);

            osw.write(profile.toJSON().toString());
            osw.flush();
            osw.close();
        } catch (IOException | JSONException ex) {
            Utils.UIToast(this, Utils.TOAST_MESSAGES.FATAL_EXCEPTION, ex);
            ex.printStackTrace();
        }
    }

    private void startWithProfile(@NonNull SingleModeProfileItem profile, boolean recreate) {
        drawerManager.setCurrentProfile(profile);

        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("lastUsedProfile", profile.getFileName())
                .putString("a2_profileName", profile.getProfileName())
                .putString("a2_serverIP", profile.getFullServerAddr())
                .putString("a2_authMethod", profile.getAuthMethod().name())
                .putString("a2_serverToken", profile.getServerToken())
                .putString("a2_serverUsername", profile.getServerUsername())
                .putString("a2_serverPassword", profile.getServerPassword())
                .putBoolean("a2_serverSSL", profile.isServerSSL())
                .putBoolean("a2_directDownload", profile.isDirectDownloadEnabled());

        if (profile.isDirectDownloadEnabled()) {
            editor.putString("dd_addr", profile.getDirectDownload().getAddress())
                    .putBoolean("dd_auth", profile.getDirectDownload().isAuth())
                    .putString("dd_user", profile.getDirectDownload().getUsername())
                    .putString("dd_passwd", profile.getDirectDownload().getPassword());
        }
        editor.apply();

        if (recreate) {
            WebSocketing.destroyInstance();
            recreate();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        getMenuInflater().inflate(R.menu.main_filters, menu.findItem(R.id.a2menu_filtering).getSubMenu());
        return true;
    }

    public void reloadPage() {
        UpdateUI.stop(updateUI, new IThread() {
            @Override
            public void stopped() {
                loadDownloads = new LoadDownloads(MainActivity.this, loadingHandler);
                new Thread(loadDownloads).start();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (fabMenu == null) {
            super.onBackPressed();
            return;
        }

        if (fabMenu.isExpanded()) {
            fabMenu.collapse();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.a2menu_refreshPage:
                reloadPage();
                break;
            // Filters
            case R.id.a2menu_active:
                item.setChecked(!item.isChecked());

                if (item.isChecked())
                    adapter.removeFilter(Download.STATUS.ACTIVE);
                else
                    adapter.addFilter(Download.STATUS.ACTIVE);
            case R.id.a2menu_paused:
                item.setChecked(!item.isChecked());

                if (item.isChecked())
                    adapter.removeFilter(Download.STATUS.PAUSED);
                else
                    adapter.addFilter(Download.STATUS.PAUSED);
                break;
            case R.id.a2menu_error:
                item.setChecked(!item.isChecked());

                if (item.isChecked())
                    adapter.removeFilter(Download.STATUS.ERROR);
                else
                    adapter.addFilter(Download.STATUS.ERROR);
                break;
            case R.id.a2menu_waiting:
                item.setChecked(!item.isChecked());

                if (item.isChecked())
                    adapter.removeFilter(Download.STATUS.WAITING);
                else
                    adapter.addFilter(Download.STATUS.WAITING);
                break;
            case R.id.a2menu_complete:
                item.setChecked(!item.isChecked());

                if (item.isChecked())
                    adapter.removeFilter(Download.STATUS.COMPLETE);
                else
                    adapter.addFilter(Download.STATUS.COMPLETE);
                break;
            case R.id.a2menu_removed:
                item.setChecked(!item.isChecked());

                if (item.isChecked())
                    adapter.removeFilter(Download.STATUS.REMOVED);
                else
                    adapter.addFilter(Download.STATUS.REMOVED);
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}