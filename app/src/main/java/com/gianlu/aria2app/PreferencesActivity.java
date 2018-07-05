package com.gianlu.aria2app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NavUtils;

import com.gianlu.aria2app.Services.NotificationService;
import com.gianlu.commonutils.LogsActivity;
import com.gianlu.commonutils.Preferences.AppCompatPreferenceActivity;
import com.gianlu.commonutils.Preferences.AppCompatPreferenceFragment;
import com.gianlu.commonutils.Preferences.BaseAboutFragment;
import com.gianlu.commonutils.Preferences.BaseThirdPartProjectsFragment;
import com.gianlu.commonutils.Preferences.Prefs;
import com.gianlu.commonutils.Toaster;
import com.gianlu.commonutils.Tutorial.TutorialManager;

import java.io.File;
import java.util.List;

public class PreferencesActivity extends AppCompatPreferenceActivity {

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    @Override
    public void onHeaderClick(Header header, int position) {
        if (header.iconRes == R.drawable.baseline_announcement_24) {
            startActivity(new Intent(this, LogsActivity.class));
            return;
        }

        super.onHeaderClick(header, position);
    }

    public static class GeneralFragment extends AppCompatPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.general_pref);
            getActivity().setTitle(R.string.general);
            setHasOptionsMenu(true);

            MultiSelectListPreference customInfo = ((MultiSelectListPreference) findPreference(PK.A2_CUSTOM_INFO.getKey()));
            customInfo.setEntryValues(CustomDownloadInfo.Info.stringValues());
            customInfo.setEntries(CustomDownloadInfo.Info.formalValues(getActivity()));

            findPreference("restartTutorial").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    TutorialManager.restartTutorial(getActivity());
                    NavUtils.navigateUpFromSameTask(getActivity());
                    return true;
                }
            });

            findPreference(Prefs.Keys.NIGHT_MODE.getKey()).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ((AppCompatPreferenceActivity) getActivity()).applyNight();
                    return true;
                }
            });
        }

        @Override
        protected Class getParent() {
            return PreferencesActivity.class;
        }
    }

    public static class DirectDownloadFragment extends AppCompatPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.direct_download_pref);
            getActivity().setTitle(R.string.directDownload);
            setHasOptionsMenu(true);

            findPreference(PK.DD_DOWNLOAD_PATH.getKey()).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    File path = new File(((String) o).trim());
                    if (!path.exists() || !path.isDirectory()) {
                        Toaster.with(getActivity()).message(R.string.invalidDownloadPath).extra(o).show();
                        return false;
                    }

                    if (!path.canWrite()) {
                        Toaster.with(getActivity()).message(R.string.invalidDownloadPath).extra(o).show();
                        return false;
                    }

                    return true;
                }
            });

            findPreference(PK.DD_MAX_SIMULTANEOUS_DOWNLOADS.getKey()).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Integer val = Integer.parseInt((String) newValue);
                    if (val > 10 || val <= 0) {
                        Toaster.with(getActivity()).message(R.string.invalidMaxSimultaneousDownloads).extra(val).show();
                        return false;
                    }

                    return true;
                }
            });
        }

        @Override
        protected Class getParent() {
            return PreferencesActivity.class;
        }
    }

    public static class NotificationsFragment extends AppCompatPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.notifications_pref);
            getActivity().setTitle(R.string.notifications);
            setHasOptionsMenu(true);

            findPreference(PK.A2_ENABLE_NOTIFS.getKey()).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if ((boolean) newValue) NotificationService.start(getActivity(), false);
                    else NotificationService.stop(getActivity());
                    return true;
                }
            });
        }

        @Override
        protected Class getParent() {
            return PreferencesActivity.class;
        }
    }

    public static class ThirdPartFragment extends BaseThirdPartProjectsFragment {

        @Override
        protected Class getParent() {
            return PreferencesActivity.class;
        }

        @NonNull
        @Override
        protected ThirdPartProject[] getProjects() {
            return new ThirdPartProject[]{
                    new ThirdPartProject(R.string.mpAndroidChart, R.string.mpAndroidChart_details, ThirdPartProject.License.APACHE),
                    new ThirdPartProject(R.string.tapTargetView, R.string.tapTargetView_details, ThirdPartProject.License.APACHE),
                    new ThirdPartProject(R.string.flowLayout, R.string.flowLayout_details, ThirdPartProject.License.APACHE)
            };
        }
    }

    public static class AboutFragment extends BaseAboutFragment {
        @Override
        protected int getAppNameRes() {
            return R.string.app_name;
        }

        @NonNull
        @Override
        protected String getPackageName() {
            return "com.gianlu.aria2app";
        }

        @Nullable
        @Override
        protected Uri getOpenSourceUrl() {
            return null;
        }

        @Override
        protected Class getParent() {
            return PreferencesActivity.class;
        }
    }
}
