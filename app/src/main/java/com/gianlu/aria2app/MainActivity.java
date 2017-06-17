package com.gianlu.aria2app;

import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SearchView;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.gianlu.aria2app.Activities.AddMetalinkActivity;
import com.gianlu.aria2app.Activities.AddTorrentActivity;
import com.gianlu.aria2app.Activities.AddUriActivity;
import com.gianlu.aria2app.Activities.DirectDownloadActivity;
import com.gianlu.aria2app.Activities.EditProfileActivity;
import com.gianlu.aria2app.Activities.MoreAboutDownloadActivity;
import com.gianlu.aria2app.Activities.SearchActivity;
import com.gianlu.aria2app.Adapters.DownloadCardsAdapter;
import com.gianlu.aria2app.Main.DrawerConst;
import com.gianlu.aria2app.Main.UpdateUI;
import com.gianlu.aria2app.NetIO.BaseUpdater;
import com.gianlu.aria2app.NetIO.DownloadsManager.DownloadsManager;
import com.gianlu.aria2app.NetIO.ErrorHandler;
import com.gianlu.aria2app.NetIO.GitHubApi;
import com.gianlu.aria2app.NetIO.JTA2.Download;
import com.gianlu.aria2app.NetIO.JTA2.JTA2;
import com.gianlu.aria2app.NetIO.JTA2.JTA2InitializingException;
import com.gianlu.aria2app.NetIO.WebSocketing;
import com.gianlu.aria2app.Options.OptionsUtils;
import com.gianlu.aria2app.ProfilesManager.CustomProfilesAdapter;
import com.gianlu.aria2app.ProfilesManager.MultiProfile;
import com.gianlu.aria2app.ProfilesManager.ProfilesManager;
import com.gianlu.aria2app.Services.NotificationService;
import com.gianlu.commonutils.CommonUtils;
import com.gianlu.commonutils.Drawer.BaseDrawerItem;
import com.gianlu.commonutils.Drawer.DrawerManager;
import com.gianlu.commonutils.Drawer.Initializer;
import com.gianlu.commonutils.Drawer.ProfilesAdapter;
import com.gianlu.commonutils.Logging;
import com.gianlu.commonutils.MessageLayout;
import com.gianlu.commonutils.SuperTextView;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements FloatingActionsMenu.OnFloatingActionsMenuUpdateListener, JTA2.IUnpause, JTA2.IRemove, JTA2.IPause, DrawerManager.IDrawerListener<MultiProfile>, DrawerManager.ISetup<MultiProfile>, UpdateUI.IUI, DownloadCardsAdapter.IAdapter, JTA2.IRestart, JTA2.IMove, DownloadsManager.IListener, SearchView.OnQueryTextListener, SearchView.OnCloseListener, MenuItemCompat.OnActionExpandListener {
    private DrawerManager<MultiProfile> drawerManager;
    private FloatingActionsMenu fabMenu;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView list;
    private DownloadCardsAdapter adapter;
    private UpdateUI updater;
    private SearchView searchView;

    private void refresh() {
        updater.stopThread(new BaseUpdater.IThread() {
            @Override
            public void onStopped() {
                adapter = new DownloadCardsAdapter(MainActivity.this, new ArrayList<Download>(), MainActivity.this);
                list.setAdapter(adapter);
                setupAdapterFiltersAndSorting();

                try {
                    updater = new UpdateUI(MainActivity.this, MainActivity.this);
                    updater.start();
                } catch (JTA2InitializingException ex) {
                    ErrorHandler.get().notifyException(ex, true);
                    MessageLayout.show((ViewGroup) findViewById(R.id.main_drawer), R.string.failedLoadingDownloads, R.drawable.ic_error_black_48dp);
                }

                swipeRefresh.setRefreshing(false);
            }
        });
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        if (drawerManager != null && drawerManager.isOpen())
            drawerManager.refreshProfiles(ProfilesManager.get(this).getProfiles());
    }

    @Override
    public boolean onMenuItemSelected(BaseDrawerItem which) {
        switch (which.id) {
            case DrawerConst.HOME:
                refresh();
                return true;
            case DrawerConst.DIRECT_DOWNLOAD:
                startActivity(new Intent(MainActivity.this, DirectDownloadActivity.class));
                return false;
            case DrawerConst.QUICK_OPTIONS:
                OptionsUtils.showGlobalDialog(this, true);
                return true;
            case DrawerConst.GLOBAL_OPTIONS:
                OptionsUtils.showGlobalDialog(this, false);
                return true;
            case DrawerConst.PREFERENCES:
                startActivity(new Intent(MainActivity.this, PreferencesActivity.class));
                return false;
            case DrawerConst.SUPPORT:
                CommonUtils.sendEmail(MainActivity.this, getString(R.string.app_name));
                return true;
            case DrawerConst.ABOUT_ARIA2:
                showAboutDialog();
                return true;
            default:
                return true;
        }
    }

    @Override
    public void onProfileSelected(final MultiProfile profile) {
        ProfilesManager.get(this).setLastProfile(this, profile);
        startActivity(new Intent(this, LoadingActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override
    public void addProfile() {
        EditProfileActivity.start(this, false);
    }

    @Override
    public void editProfile(final List<MultiProfile> items) {
        CommonUtils.showDialog(this, new AlertDialog.Builder(this)
                .setTitle(R.string.editProfile)
                .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        EditProfileActivity.start(MainActivity.this, items.get(which));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null));
    }

    private void showAboutDialog() {
        final JTA2 jta2;
        try {
            jta2 = JTA2.instantiate(MainActivity.this);
        } catch (JTA2InitializingException ex) {
            CommonUtils.UIToast(MainActivity.this, Utils.ToastMessages.FAILED_GATHERING_INFORMATION, ex);
            return;
        }

        final ProgressDialog pd = CommonUtils.fastIndeterminateProgressDialog(MainActivity.this, R.string.gathering_information);
        CommonUtils.showDialog(MainActivity.this, pd);
        jta2.getVersion(new JTA2.IVersion() {
            @Override
            public void onVersion(List<String> rawFeatures, String version) {
                final LinearLayout layout = new LinearLayout(MainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
                layout.setPadding(padding, padding, padding, padding);
                layout.addView(new SuperTextView(MainActivity.this, R.string.version, version));
                layout.addView(new SuperTextView(MainActivity.this, R.string.features, CommonUtils.join(rawFeatures, ",")));

                jta2.getSessionInfo(new JTA2.ISession() {
                    @Override
                    public void onSessionInfo(String sessionID) {
                        layout.addView(new SuperTextView(MainActivity.this, R.string.sessionId, sessionID));
                        pd.dismiss();

                        CommonUtils.showDialog(MainActivity.this, new AlertDialog.Builder(MainActivity.this)
                                .setTitle(R.string.about_aria2)
                                .setView(layout)
                                .setNeutralButton(R.string.saveSession, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        jta2.saveSession(new JTA2.ISuccess() {
                                            @Override
                                            public void onSuccess() {
                                                CommonUtils.UIToast(MainActivity.this, Utils.ToastMessages.SESSION_SAVED);
                                            }

                                            @Override
                                            public void onException(Exception exception) {
                                                CommonUtils.UIToast(MainActivity.this, Utils.ToastMessages.FAILED_SAVE_SESSION, exception);
                                            }
                                        });
                                    }
                                })
                                .setPositiveButton(android.R.string.ok, null));
                    }

                    @Override
                    public void onException(Exception ex) {
                        CommonUtils.UIToast(MainActivity.this, Utils.ToastMessages.FAILED_GATHERING_INFORMATION, ex);
                        pd.dismiss();
                    }
                });
            }

            @Override
            public void onException(Exception ex) {
                CommonUtils.UIToast(MainActivity.this, Utils.ToastMessages.FAILED_GATHERING_INFORMATION, ex);
                pd.dismiss();
            }
        });
    }

    private void setupAdapterFiltersAndSorting() {
        List<Download.Status> filters = new ArrayList<>(Arrays.asList(Download.Status.values()));
        Set<String> checkedFiltersSet = Prefs.getSet(this, Prefs.Keys.A2_MAIN_FILTERS, new HashSet<>(Download.Status.stringValues()));
        for (String filter : checkedFiltersSet) filters.remove(Download.Status.valueOf(filter));
        adapter.setFilters(filters);

        adapter.sort(DownloadCardsAdapter.SortBy.valueOf(Prefs.getString(this, Prefs.Keys.A2_MAIN_SORTING, DownloadCardsAdapter.SortBy.STATUS.name())));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler(this));

        if (Prefs.getString(this, Prefs.Keys.DD_DOWNLOAD_PATH, null) == null)
            Prefs.putString(this, Prefs.Keys.DD_DOWNLOAD_PATH, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());

        Logging.clearLogs(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        drawerManager = new DrawerManager<>(new Initializer<>(this, (DrawerLayout) findViewById(R.id.main_drawer), toolbar, this)
                .addMenuItem(new BaseDrawerItem(DrawerConst.HOME, R.drawable.ic_home_black_48dp, getString(R.string.home)))
                .addMenuItem(new BaseDrawerItem(DrawerConst.DIRECT_DOWNLOAD, R.drawable.ic_cloud_download_black_48dp, getString(R.string.directDownload)))
                .addMenuItem(new BaseDrawerItem(DrawerConst.QUICK_OPTIONS, R.drawable.ic_favorite_black_48dp, getString(R.string.quickGlobalOptions)))
                .addMenuItem(new BaseDrawerItem(DrawerConst.GLOBAL_OPTIONS, R.drawable.ic_list_black_48dp, getString(R.string.globalOptions)))
                .addMenuItem(new BaseDrawerItem(DrawerConst.ABOUT_ARIA2, R.drawable.ic_cloud_black_48dp, getString(R.string.about_aria2)))
                .addMenuItemSeparator()
                .addMenuItem(new BaseDrawerItem(DrawerConst.PREFERENCES, R.drawable.ic_settings_black_48dp, getString(R.string.menu_preferences)))
                .addMenuItem(new BaseDrawerItem(DrawerConst.SUPPORT, R.drawable.ic_report_problem_black_48dp, getString(R.string.support)))
                .addProfiles(ProfilesManager.get(this).getProfiles()));

        ProfilesManager manager = ProfilesManager.get(this);
        MultiProfile currentProfile = manager.getCurrent();
        if (currentProfile == null) {
            startActivity(new Intent(this, LoadingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finishActivity(0);
            return;
        }

        drawerManager.setCurrentProfile(currentProfile).setDrawerListener(this);

        setTitle(currentProfile.getProfileName(this) + " - " + getString(R.string.app_name));

        list = (RecyclerView) findViewById(R.id.main_list);
        list.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        swipeRefresh = (SwipeRefreshLayout) findViewById(R.id.main_swipeLayout);
        swipeRefresh.setColorSchemeResources(R.color.colorAccent, R.color.colorMetalink, R.color.colorTorrent);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });

        fabMenu = (FloatingActionsMenu) findViewById(R.id.main_fab);
        fabMenu.setOnFloatingActionsMenuUpdateListener(this);

        FloatingActionButton fabSearch = (FloatingActionButton) findViewById(R.id.mainFab_search);
        fabSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SearchActivity.class));
            }
        });
        FloatingActionButton fabAddURI = (FloatingActionButton) findViewById(R.id.mainFab_addURI);
        fabAddURI.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, AddUriActivity.class));
            }
        });
        final FloatingActionButton fabAddTorrent = (FloatingActionButton) findViewById(R.id.mainFab_addTorrent);
        fabAddTorrent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, AddTorrentActivity.class));
            }
        });
        final FloatingActionButton fabAddMetalink = (FloatingActionButton) findViewById(R.id.mainFab_addMetalink);
        fabAddMetalink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, AddMetalinkActivity.class));
            }
        });

        DownloadsManager.get(this).setListener(this);

        if (Prefs.getBoolean(this, Prefs.Keys.A2_ENABLE_NOTIFS, true))
            NotificationService.start(this);
        else NotificationService.stop(this);

        adapter = new DownloadCardsAdapter(this, new ArrayList<Download>(), this);
        list.setAdapter(adapter);
        setupAdapterFiltersAndSorting();

        try {
            updater = new UpdateUI(this, this);
            updater.start();
        } catch (JTA2InitializingException ex) {
            ErrorHandler.get().notifyException(ex, true);
            MessageLayout.show((ViewGroup) findViewById(R.id.main_drawer), R.string.failedLoadingDownloads, R.drawable.ic_error_black_48dp);
        }

        Uri shareData = getIntent().getParcelableExtra("shareData");
        if (shareData != null) {
            String path = Utils.resolveUri(this, shareData);
            if (path != null) {
                File file = new File(path);
                if (file.exists() && file.canRead()) {
                    String ext = MimeTypeMap.getFileExtensionFromUrl(path);
                    if (ext == null) {
                        CommonUtils.UIToast(this, Utils.ToastMessages.INVALID_FILE, new Exception("Cannot determine file type!"));
                    } else {
                        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                        if (Objects.equals(mime, "application/x-bittorrent")) {
                            AddTorrentActivity.startAndAdd(this, file);
                        } else if (Objects.equals(mime, "application/metalink4+xml") || Objects.equals(mime, "application/metalink+xml")) {
                            AddMetalinkActivity.startAndAdd(this, file);
                        } else {
                            CommonUtils.UIToast(this, Utils.ToastMessages.INVALID_FILE, new Exception("Cannot determine file type!"));
                        }
                    }
                } else {
                    CommonUtils.UIToast(this, Utils.ToastMessages.INVALID_FILE, new Exception("Shared file doesn't exist or cannot be read!"));
                }
            } else {
                URI uri;
                try {
                    uri = new URI(shareData.toString());
                } catch (URISyntaxException ex) {
                    CommonUtils.UIToast(this, Utils.ToastMessages.INVALID_FILE, new Exception("Cannot identify shared file/url!", ex));
                    return;
                }

                AddUriActivity.startAndAdd(this, uri);
            }
        }

        if (!((ThisApplication) getApplication()).hasCheckedVersion() && Prefs.getBoolean(this, Prefs.Keys.A2_CHECK_VERSION, true)) {
            GitHubApi.getLatestVersion(new GitHubApi.IRelease() {
                @Override
                public void onRelease(final String latestVersion) {
                    JTA2 jta2;
                    try {
                        jta2 = JTA2.instantiate(MainActivity.this);
                    } catch (JTA2InitializingException ex) {
                        Logging.logMe(MainActivity.this, ex);
                        return;
                    }

                    jta2.getVersion(new JTA2.IVersion() {
                        @Override
                        public void onVersion(List<String> rawFeatures, String version) {
                            ((ThisApplication) getApplication()).setHasCheckedVersion(true);
                            if (!Objects.equals(version, latestVersion))
                                showOutdatedDialog(latestVersion, version);
                        }

                        @Override
                        public void onException(Exception ex) {
                            Logging.logMe(MainActivity.this, ex);
                        }
                    });
                }

                @Override
                public void onException(Exception ex) {
                    Logging.logMe(MainActivity.this, ex);
                }
            });
        }
    }

    private void showOutdatedDialog(String latest, String current) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.outdated_aria2)
                .setMessage(getString(R.string.outdated_aria2_message, latest, current))
                .setPositiveButton(android.R.string.ok, null);

        CommonUtils.showDialog(this, builder);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (drawerManager != null) drawerManager.onTogglerConfigurationChanged(newConfig);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (drawerManager != null) drawerManager.syncTogglerState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (drawerManager != null) drawerManager.syncTogglerState();
        if (fabMenu != null) fabMenu.collapseImmediately();

        try {
            ProfilesManager.get(this).reloadCurrentProfile(this);
        } catch (IOException | JSONException | NullPointerException ex) {
            Logging.logMe(this, ex);
            WebSocketing.destroy();
            startActivity(new Intent(this, LoadingActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("showPicker", true));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        getMenuInflater().inflate(R.menu.main_sorting, menu.findItem(R.id.main_sort).getSubMenu());

        SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
        MenuItem searchItem = menu.findItem(R.id.main_search);
        MenuItemCompat.setOnActionExpandListener(searchItem, this);
        searchView = (SearchView) searchItem.getActionView();

        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(false);
        searchView.setOnCloseListener(this);
        searchView.setOnQueryTextListener(this);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        SubMenu sortingMenu = menu.findItem(R.id.main_sort).getSubMenu();
        if (sortingMenu != null) {
            sortingMenu.setGroupCheckable(0, true, true);

            DownloadCardsAdapter.SortBy sorting = DownloadCardsAdapter.SortBy.valueOf(Prefs.getString(this, Prefs.Keys.A2_MAIN_SORTING, DownloadCardsAdapter.SortBy.STATUS.name()));
            MenuItem item;
            switch (sorting) {
                case NAME:
                    item = sortingMenu.findItem(R.id.mainSort_name);
                    break;
                default:
                case STATUS:
                    item = sortingMenu.findItem(R.id.mainSort_status);
                    break;
                case PROGRESS:
                    item = sortingMenu.findItem(R.id.mainSort_progress);
                    break;
                case DOWNLOAD_SPEED:
                    item = sortingMenu.findItem(R.id.mainSort_downloadSpeed);
                    break;
                case UPLOAD_SPEED:
                    item = sortingMenu.findItem(R.id.mainSort_uploadSpeed);
                    break;
                case COMPLETED_LENGTH:
                    item = sortingMenu.findItem(R.id.mainSort_completedLength);
                    break;
                case LENGTH:
                    item = sortingMenu.findItem(R.id.mainSort_length);
                    break;
            }

            item.setChecked(true);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (fabMenu != null && fabMenu.isExpanded()) fabMenu.collapse();
        else super.onBackPressed();
    }

    private void showFilteringDialog() {
        final Download.Status[] filters = new Download.Status[]{Download.Status.ACTIVE, Download.Status.PAUSED, Download.Status.WAITING, Download.Status.ERROR, Download.Status.REMOVED, Download.Status.COMPLETE};
        CharSequence[] stringFilters = new CharSequence[filters.length];

        for (int i = 0; i < filters.length; i++)
            stringFilters[i] = filters[i].getFormal(this, true);

        final boolean[] checkedFilters = new boolean[filters.length];
        Set<String> checkedFiltersSet = Prefs.getSet(this, Prefs.Keys.A2_MAIN_FILTERS, null);

        if (checkedFiltersSet == null) {
            for (int i = 0; i < checkedFilters.length; i++) checkedFilters[i] = true;
        } else {
            for (String checkedFilter : checkedFiltersSet) {
                Download.Status filter = Download.Status.valueOf(checkedFilter);
                int pos = Utils.indexOf(filters, filter);
                if (pos != -1) checkedFilters[pos] = true;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.filters)
                .setMultiChoiceItems(stringFilters, checkedFilters, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checkedFilters[which] = isChecked;
                    }
                })
                .setPositiveButton(R.string.apply, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        List<Download.Status> toApplyFilters = new ArrayList<>();
                        for (int i = 0; i < checkedFilters.length; i++)
                            if (!checkedFilters[i]) toApplyFilters.add(filters[i]);

                        if (adapter != null) adapter.setFilters(toApplyFilters);
                        Set<String> set = new HashSet<>();
                        for (int i = 0; i < checkedFilters.length; i++)
                            if (checkedFilters[i]) set.add(filters[i].name());

                        Prefs.putSet(MainActivity.this, Prefs.Keys.A2_MAIN_FILTERS, set);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);

        CommonUtils.showDialog(this, builder);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.main_refreshPage:
                refresh();
                break;
            // Filters
            case R.id.main_filter:
                showFilteringDialog();
                break;
            // Sorting
            default:
                return handleSorting(item);
        }

        return true;
    }

    private boolean handleSorting(MenuItem clicked) {
        clicked.setChecked(true);
        switch (clicked.getItemId()) {
            case R.id.mainSort_name:
                handleSortingReal(DownloadCardsAdapter.SortBy.NAME);
                break;
            case R.id.mainSort_status:
                handleSortingReal(DownloadCardsAdapter.SortBy.STATUS);
                break;
            case R.id.mainSort_progress:
                handleSortingReal(DownloadCardsAdapter.SortBy.PROGRESS);
                break;
            case R.id.mainSort_downloadSpeed:
                handleSortingReal(DownloadCardsAdapter.SortBy.DOWNLOAD_SPEED);
                break;
            case R.id.mainSort_uploadSpeed:
                handleSortingReal(DownloadCardsAdapter.SortBy.UPLOAD_SPEED);
                break;
            case R.id.mainSort_length:
                handleSortingReal(DownloadCardsAdapter.SortBy.LENGTH);
                break;
            case R.id.mainSort_completedLength:
                handleSortingReal(DownloadCardsAdapter.SortBy.COMPLETED_LENGTH);
                break;
        }

        return true;
    }

    private void handleSortingReal(DownloadCardsAdapter.SortBy sorting) {
        if (adapter != null) adapter.sort(sorting);
        Prefs.putString(this, Prefs.Keys.A2_MAIN_SORTING, sorting.name());
    }

    @Override
    public void onMenuExpanded() {
        final View mask = findViewById(R.id.main_mask);
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
        mask.setVisibility(View.GONE);
        mask.setClickable(false);
    }

    @Override
    public int getColorAccent() {
        return R.color.colorAccent;
    }

    @Override
    public int getHeaderBackground() {
        return R.drawable.drawer_background;
    }

    @Override
    public int getOpenDrawerDesc() {
        return R.string.openDrawer;
    }

    @Override
    public int getCloseDrawerDesc() {
        return R.string.closeDrawer;
    }

    @Override
    public int getRippleDark() {
        return R.drawable.ripple_effect_dark;
    }

    @Override
    public int getDrawerBadge() {
        return R.drawable.drawer_badge;
    }

    @Override
    public int getColorPrimary() {
        return R.color.colorPrimary;
    }

    @Nullable
    @Override
    public ProfilesAdapter<MultiProfile> getProfilesAdapter(Context context, List<MultiProfile> profiles, final DrawerManager.IDrawerListener<MultiProfile> listener) {
        return new CustomProfilesAdapter(context, profiles, new ProfilesAdapter.IAdapter<MultiProfile>() {
            @Override
            public void onProfileSelected(MultiProfile profile) {
                if (listener != null) listener.onProfileSelected(profile);
                if (drawerManager != null) drawerManager.performUnlock();
            }
        }, true, null);
    }

    @Override
    public int getColorPrimaryShadow() {
        return R.color.colorPrimary_shadow;
    }

    @Override
    public void onUpdateAdapter(List<Download> downloads) {
        if (adapter != null) adapter.notifyItemsChanged(downloads);

        String gid = getIntent().getStringExtra("gid");
        if (gid != null) {
            for (Download download : downloads)
                if (Objects.equals(download.gid, gid))
                    onMoreClick(download);

            getIntent().removeExtra("gid");
        }
    }

    @Override
    public void onMoreClick(Download item) {
        MoreAboutDownloadActivity.start(this, item);
    }

    @Override
    public void onItemCountUpdated(int count) {
        if (drawerManager != null) drawerManager.updateBadge(DrawerConst.HOME, count);

        if (count == 0) {
            MessageLayout.show((ViewGroup) findViewById(R.id.main_drawer), R.string.noDownloads, R.drawable.ic_info_outline_black_48dp);
            list.setVisibility(View.GONE);
        } else {
            MessageLayout.hide((ViewGroup) findViewById(R.id.main_drawer));
            list.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onMenuItemSelected(final Download download, JTA2.DownloadActions action) {
        final JTA2 jta2;
        try {
            jta2 = JTA2.instantiate(this);
        } catch (JTA2InitializingException ex) {
            onException(ex);
            return;
        }

        switch (action) {
            case MOVE_UP:
                jta2.moveUp(download.gid, this);
                break;
            case MOVE_DOWN:
                jta2.moveDown(download.gid, this);
                break;
            case PAUSE:
                jta2.pause(download.gid, this);
                break;
            case REMOVE:
                if (download.status == Download.Status.ACTIVE || download.status == Download.Status.PAUSED) {
                    CommonUtils.showDialog(this, new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.removeName, download.getName()))
                            .setMessage(R.string.removeDownloadAlert)
                            .setNegativeButton(android.R.string.no, null)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    jta2.remove(download.gid, download.status, MainActivity.this);
                                }
                            }));
                } else {
                    jta2.remove(download.gid, download.status, MainActivity.this);
                }
                break;
            case RESTART:
                jta2.restart(download.gid, this);
                break;
            case RESUME:
                jta2.unpause(download.gid, this);
                break;
        }
    }

    @Nullable
    @Override
    public RecyclerView getRecyclerView() {
        return list;
    }

    @Override
    public void onPaused(String gid) {
        CommonUtils.UIToast(this, Utils.ToastMessages.PAUSED, gid);
    }

    @Override
    public void onRestarted(String gid) {
        CommonUtils.UIToast(this, Utils.ToastMessages.RESTARTED, gid);
    }

    @Override
    public void onUnpaused(String gid) {
        CommonUtils.UIToast(this, Utils.ToastMessages.RESUMED, gid);
    }

    @Override
    public void onMoved(String gid) {
        CommonUtils.UIToast(this, Utils.ToastMessages.MOVED, gid);
    }

    @Override
    public void onException(Exception ex) {
        CommonUtils.UIToast(this, Utils.ToastMessages.FAILED_PERFORMING_ACTION, ex);
    }

    @Override
    public void onRemoved(String gid) {
        CommonUtils.UIToast(this, Utils.ToastMessages.REMOVED, gid);
    }

    @Override
    public void onRemovedResult(String gid) {
        CommonUtils.UIToast(this, Utils.ToastMessages.RESULT_REMOVED, gid);
    }

    @Override
    public void onDownloadsCountChanged(int count) {
        if (drawerManager != null) drawerManager.updateBadge(DrawerConst.DIRECT_DOWNLOAD, count);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        adapter.filterWithQuery(query);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return onQueryTextSubmit(newText);
    }

    @Override
    public boolean onClose() {
        searchView.setQuery(null, true);
        return false;
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        onClose();
        return true;
    }
}