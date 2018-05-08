package com.gianlu.aria2app.Activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.gianlu.aria2app.Activities.AddDownload.OptionsFragment;
import com.gianlu.aria2app.Activities.AddDownload.UrisFragment;
import com.gianlu.aria2app.Adapters.PagerAdapter;
import com.gianlu.aria2app.NetIO.AbstractClient;
import com.gianlu.aria2app.NetIO.Aria2.Aria2Helper;
import com.gianlu.aria2app.NetIO.AriaRequests;
import com.gianlu.aria2app.R;
import com.gianlu.aria2app.Utils;
import com.gianlu.commonutils.Analytics.AnalyticsApplication;
import com.gianlu.commonutils.Dialogs.ActivityWithDialog;
import com.gianlu.commonutils.Dialogs.DialogUtils;
import com.gianlu.commonutils.Toaster;

import org.json.JSONException;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class AddUriActivity extends ActivityWithDialog {
    private UrisFragment urisFragment;
    private OptionsFragment optionsFragment;
    private ViewPager pager;

    public static void startAndAdd(Context context, URI uri) {
        context.startActivity(new Intent(context, AddUriActivity.class).putExtra("uri", uri));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_download);
        setTitle(R.string.addUri);

        Toolbar toolbar = findViewById(R.id.addDownload_toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        pager = findViewById(R.id.addDownload_pager);
        final TabLayout tabLayout = findViewById(R.id.addDownload_tabs);

        urisFragment = UrisFragment.getInstance(this, true, (URI) getIntent().getSerializableExtra("uri"));
        optionsFragment = OptionsFragment.getInstance(this, true);

        pager.setAdapter(new PagerAdapter<>(getSupportFragmentManager(), urisFragment, optionsFragment));
        pager.setOffscreenPageLimit(2);

        tabLayout.setupWithViewPager(pager);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                pager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.add_download, menu);
        return true;
    }

    private void done() {
        List<String> uris = urisFragment.getUris();
        if (uris == null || uris.isEmpty()) {
            Toaster.show(this, Utils.Messages.NO_URIS);
            pager.setCurrentItem(0, true);
            return;
        }

        Map<String, String> options = optionsFragment.getOptions();
        Integer position = optionsFragment.getPosition();
        String filename = optionsFragment.getFilename();

        if (filename != null) options.put("out", filename);

        try {
            showDialog(DialogUtils.progressDialog(this, R.string.gathering_information));
            Aria2Helper.instantiate(this).request(AriaRequests.addUri(uris, position, options), new AbstractClient.OnResult<String>() {
                @Override
                public void onResult(@NonNull String result) {
                    dismissDialog();
                    Toaster.show(AddUriActivity.this, Utils.Messages.DOWNLOAD_ADDED, result, new Runnable() {
                        @Override
                        public void run() {
                            onBackPressed();
                        }
                    });
                }

                @Override
                public void onException(Exception ex, boolean shouldForce) {
                    dismissDialog();
                    Toaster.show(AddUriActivity.this, Utils.Messages.FAILED_ADD_DOWNLOAD, ex);
                }
            });
        } catch (Aria2Helper.InitializingException | JSONException ex) {
            Toaster.show(this, Utils.Messages.FAILED_ADD_DOWNLOAD, ex);
        }

        AnalyticsApplication.sendAnalytics(AddUriActivity.this, Utils.ACTION_NEW_URI);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.addDownload_done:
                done();
                break;
        }

        return true;
    }

    @Override
    public void onBackPressed() {
        try {
            super.onBackPressed();
        } catch (IllegalStateException ignored) {
        }
    }
}
