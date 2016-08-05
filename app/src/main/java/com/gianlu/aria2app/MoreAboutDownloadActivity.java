package com.gianlu.aria2app;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ExpandableListView;

import com.gianlu.aria2app.Google.Analytics;
import com.gianlu.aria2app.MoreAboutDownload.CommonFragment;
import com.gianlu.aria2app.MoreAboutDownload.FilesPagerFragment;
import com.gianlu.aria2app.MoreAboutDownload.InfoFragment.InfoPagerFragment;
import com.gianlu.aria2app.MoreAboutDownload.InfoFragment.UpdateUI;
import com.gianlu.aria2app.MoreAboutDownload.PagerAdapter;
import com.gianlu.aria2app.MoreAboutDownload.PeersFragment.PeersPagerFragment;
import com.gianlu.aria2app.MoreAboutDownload.ServersFragment.ServersPagerFragment;
import com.gianlu.aria2app.NetIO.JTA2.Download;
import com.gianlu.aria2app.NetIO.JTA2.IOption;
import com.gianlu.aria2app.NetIO.JTA2.ISuccess;
import com.gianlu.aria2app.NetIO.JTA2.JTA2;
import com.gianlu.aria2app.Options.LocalParser;
import com.gianlu.aria2app.Options.OptionAdapter;
import com.gianlu.aria2app.Options.OptionChild;
import com.gianlu.aria2app.Options.OptionHeader;
import com.google.android.gms.analytics.HitBuilders;

import org.json.JSONException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoreAboutDownloadActivity extends AppCompatActivity {
    private Menu menu;
    private PagerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(getIntent().getBooleanExtra("isTorrent", false) ? R.style.AppTheme_NoActionBar_Torrent : R.style.AppTheme_NoActionBar);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more_about_download);
        setTitle(getIntent().getStringExtra("name"));

        final String gid = getIntent().getStringExtra("gid");

        Toolbar toolbar = (Toolbar) findViewById(R.id.moreAboutDownload_toolbar);
        assert toolbar != null;
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayHomeAsUpEnabled(true);

        ViewPager pager = (ViewPager) findViewById(R.id.moreAboutDownload_pager);
        assert pager != null;

        final TabLayout tabLayout = (TabLayout) findViewById(R.id.moreAboutDownload_tabs);
        assert tabLayout != null;

        final List<CommonFragment> fragments = new ArrayList<>();
        fragments.add(InfoPagerFragment.newInstance(getString(R.string.info), gid).setStatusObserver(new UpdateUI.IDownloadStatusObserver() {
            @Override
            public void onDownloadStatusChanged(Download.STATUS newStatus) {
                if (menu == null) return;

                // TODO
                switch (newStatus) {
                    case ACTIVE:
                        switch (tabLayout.getSelectedTabPosition()) {
                            case 0:
                                // TAB: INFO, STATUS: ACTIVE
                                break;
                            case 1:
                                if (getIntent().getBooleanExtra("isTorrent", false)) {
                                    // TAB: PEERS, STATUS: ACTIVE
                                } else {
                                    // TAB: SERVERS, STATUS: ACTIVE
                                }
                                break;
                            case 2:
                                // TAB: FILES, STATUS: ACTIVE
                                break;
                        }
                        break;
                    case PAUSED:
                        switch (tabLayout.getSelectedTabPosition()) {
                            case 0:
                                // TAB: INFO, STATUS: PAUSED
                                break;
                            case 1:
                                if (getIntent().getBooleanExtra("isTorrent", false)) {
                                    // TAB: PEERS, STATUS: PAUSED
                                } else {
                                    // TAB: SERVERS, STATUS: PAUSED
                                }
                                break;
                            case 2:
                                // TAB: FILES, STATUS: PAUSED
                                break;
                        }
                        break;
                    case WAITING:
                        switch (tabLayout.getSelectedTabPosition()) {
                            case 0:
                                // TAB: INFO, STATUS: WAITING
                                break;
                            case 1:
                                if (getIntent().getBooleanExtra("isTorrent", false)) {
                                    // TAB: PEERS, STATUS: WAITING
                                } else {
                                    // TAB: SERVERS, STATUS: WAITING
                                }
                                break;
                            case 2:
                                // TAB: FILES, STATUS: WAITING
                                break;
                        }
                        break;
                    case REMOVED:
                    case ERROR:
                    case COMPLETE:
                        switch (tabLayout.getSelectedTabPosition()) {
                            case 0:
                                // TAB: INFO, STATUS: FROZEN
                                break;
                            case 1:
                                if (getIntent().getBooleanExtra("isTorrent", false)) {
                                    // TAB: PEERS, STATUS: FROZEN
                                } else {
                                    // TAB: SERVERS, STATUS: FROZEN
                                }
                                break;
                            case 2:
                                // TAB: FILES, STATUS: FROZEN
                                break;
                        }
                        break;
                    case UNKNOWN:
                    default:
                        menu.clear();
                        break;
                }
            }
        }));

        if (getIntent().getBooleanExtra("isTorrent", false))
            fragments.add(PeersPagerFragment.newInstance(getString(R.string.peers), gid));
        else
            fragments.add(ServersPagerFragment.newInstance(getString(R.string.servers), gid));

        fragments.add(FilesPagerFragment.newInstance(getString(R.string.files), gid));

        adapter = new PagerAdapter(getSupportFragmentManager(), fragments);
        pager.setAdapter(adapter);

        tabLayout.setupWithViewPager(pager);

        /* TODO: Move this to download button click
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            canWrite = true;
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.externalStorage_box)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(MoreAboutDownloadActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 45);
                            }
                        });
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 45);
            }
        }
        */
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.more_about_download, menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.moreAboutDownloadMenu_options:
                showOptionsDialog();
                break;
            case android.R.id.home:
                onBackPressed();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        adapter.stopAllUpdater();
        finishActivity(0);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        startActivity(new Intent(this, MainActivity.class));
        finishActivity(0);
        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        adapter.stopAllUpdater();
        finishActivity(0);
        super.onStop();
    }

    private void showOptionsDialog() {
        final List<OptionHeader> headers = new ArrayList<>();
        final Map<OptionHeader, OptionChild> children = new HashMap<>();

        final JTA2 jta2;
        try {
            jta2 = Utils.readyJTA2(this);
        } catch (IOException | NoSuchAlgorithmException ex) {
            Utils.UIToast(this, Utils.TOAST_MESSAGES.WS_EXCEPTION, ex);
            return;
        }
        final ProgressDialog pd = Utils.fastProgressDialog(this, R.string.gathering_information, true, false);
        pd.show();

        jta2.getOption("", new IOption() {
            @Override
            public void onOptions(Map<String, String> options) {
                LocalParser localOptions;
                try {
                    localOptions = new LocalParser(MoreAboutDownloadActivity.this, false);
                } catch (IOException | JSONException ex) {
                    pd.dismiss();
                    Utils.UIToast(MoreAboutDownloadActivity.this, Utils.TOAST_MESSAGES.FAILED_GATHERING_INFORMATION, ex);
                    return;
                }

                for (String resOption : getResources().getStringArray(R.array.downloadOptions)) {
                    try {
                        OptionHeader header = new OptionHeader(resOption, localOptions.getCommandLine(resOption), options.get(resOption), false);
                        headers.add(header);

                        children.put(header, new OptionChild(
                                localOptions.getDefinition(resOption),
                                String.valueOf(localOptions.getDefaultValue(resOption)),
                                String.valueOf(options.get(resOption))));
                    } catch (JSONException ex) {
                        pd.dismiss();
                        Utils.UIToast(MoreAboutDownloadActivity.this, Utils.TOAST_MESSAGES.FAILED_GATHERING_INFORMATION, ex);
                    }
                }

                pd.dismiss();

                final AlertDialog.Builder builder = new AlertDialog.Builder(MoreAboutDownloadActivity.this);

                @SuppressLint("InflateParams") final View view = getLayoutInflater().inflate(R.layout.options_dialog, null);
                ExpandableListView listView = (ExpandableListView) view.findViewById(R.id.moreAboutDownload_dialog_expandableListView);
                listView.setAdapter(new OptionAdapter(MoreAboutDownloadActivity.this, headers, children));

                builder.setView(view)
                        .setTitle(R.string.options)
                        .setPositiveButton(R.string.change, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Map<String, String> map = new HashMap<>();

                                for (Map.Entry<OptionHeader, OptionChild> item : children.entrySet()) {
                                    if (!item.getValue().isChanged()) continue;
                                    map.put(item.getKey().getOptionName(), item.getValue().getValue());
                                }

                                if (map.entrySet().size() == 0) return;

                                pd.show();

                                if (Analytics.isTrackingAllowed(MoreAboutDownloadActivity.this))
                                    Analytics.getDefaultTracker(MoreAboutDownloadActivity.this.getApplication()).send(new HitBuilders.EventBuilder()
                                            .setCategory(Analytics.CATEGORY_USER_INPUT)
                                            .setAction(Analytics.ACTION_CHANGED_DOWNLOAD_OPTIONS)
                                            .build());

                                jta2.changeOption("", map, new ISuccess() {
                                    @Override
                                    public void onSuccess() {
                                        pd.dismiss();
                                        Utils.UIToast(MoreAboutDownloadActivity.this, Utils.TOAST_MESSAGES.DOWNLOAD_OPTIONS_CHANGED);

                                        MoreAboutDownloadActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                /*
                                                UpdateUI.stop(updateUI, new IThread() {
                                                    @Override
                                                    public void stopped() {
                                                        updateUI = new UpdateUI(MoreAboutDownloadActivity.this, null, canWrite, gid, holder);
                                                        new Thread(updateUI).start();
                                                    }
                                                });
                                                */
                                            }
                                        });
                                    }

                                    @Override
                                    public void onException(Exception exception) {
                                        pd.dismiss();
                                        Utils.UIToast(MoreAboutDownloadActivity.this, Utils.TOAST_MESSAGES.FAILED_CHANGE_OPTIONS, exception);
                                    }
                                });
                            }
                        });

                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        MoreAboutDownloadActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                /*
                                UpdateUI.stop(updateUI, new IThread() {
                                    @Override
                                    public void stopped() {
                                        updateUI = new UpdateUI(MoreAboutDownloadActivity.this, null, canWrite, gid, holder);
                                        new Thread(updateUI).start();
                                    }
                                });
                                */
                            }
                        });
                    }
                });

                MoreAboutDownloadActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final AlertDialog dialog = builder.create();
                        dialog.show();
                        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

                        ViewTreeObserver vto = view.getViewTreeObserver();
                        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                dialog.getWindow().setLayout(dialog.getWindow().getDecorView().getWidth(), dialog.getWindow().getDecorView().getHeight());
                                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            }
                        });
                    }
                });
            }

            @Override
            public void onException(Exception exception) {
                pd.dismiss();
                Utils.UIToast(MoreAboutDownloadActivity.this, Utils.TOAST_MESSAGES.FAILED_GATHERING_INFORMATION, exception);
            }
        });
    }
}