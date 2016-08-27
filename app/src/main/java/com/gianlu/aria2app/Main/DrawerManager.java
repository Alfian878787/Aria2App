package com.gianlu.aria2app.Main;

import android.animation.Animator;
import android.app.Activity;
import android.graphics.Color;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.gianlu.aria2app.Main.Profile.MultiModeProfileItem;
import com.gianlu.aria2app.Main.Profile.ProfileItem;
import com.gianlu.aria2app.Main.Profile.ProfilesAdapter;
import com.gianlu.aria2app.Main.Profile.SingleModeProfileItem;
import com.gianlu.aria2app.R;
import com.gianlu.aria2app.Utils;

import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DrawerManager {
    private Activity context;
    private DrawerLayout drawerLayout;
    private LinearLayout drawerList;
    private LinearLayout drawerFooterList;
    private ListView drawerProfiles;
    private LinearLayout drawerProfilesFooter;
    private IDrawerListener listener;
    private ProfilesAdapter profilesAdapter;
    private boolean isProfilesLockedUntilSelected;

    // TODO: Finish header
    public DrawerManager(Activity context, DrawerLayout drawerLayout) {
        this.context = context;
        this.drawerLayout = drawerLayout;
        this.drawerList = (LinearLayout) drawerLayout.findViewById(R.id.mainDrawer_list);
        this.drawerFooterList = (LinearLayout) drawerLayout.findViewById(R.id.mainDrawer_footerList);
        this.drawerProfiles = (ListView) drawerLayout.findViewById(R.id.mainDrawer_profiles);
        this.drawerProfilesFooter = (LinearLayout) drawerLayout.findViewById(R.id.mainDrawer_profilesFooter);
    }

    public DrawerManager setDrawerListener(IDrawerListener listener) {
        this.listener = listener;
        return this;
    }

    public DrawerManager openProfiles(boolean lockUntilSelected) {
        setDrawerState(true, true);
        setProfilesState(true);

        isProfilesLockedUntilSelected = lockUntilSelected;
        if (lockUntilSelected) {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN);
            drawerLayout.findViewById(R.id.mainDrawerHeader_dropdown).setEnabled(false);
        }

        return this;
    }

    public DrawerManager setProfilesState(boolean open) {
        if ((drawerLayout.findViewById(R.id.mainDrawer_profileContainer).getVisibility() == View.INVISIBLE && open)
                || (drawerLayout.findViewById(R.id.mainDrawer_profileContainer).getVisibility() == View.VISIBLE && !open)) {
            drawerLayout.findViewById(R.id.mainDrawerHeader_dropdown).callOnClick();
        }

        return this;
    }

    public DrawerManager setDrawerState(boolean open, boolean animate) {
        if (open)
            drawerLayout.openDrawer(GravityCompat.START, animate);
        else
            drawerLayout.closeDrawer(GravityCompat.START, animate);

        return this;
    }

    private View newItem(@DrawableRes int icon, String title, boolean primary) {
        return newItem(icon, title, primary, -1, -1, -1);
    }

    private View newItem(@DrawableRes int icon, String title, boolean primary, int badgeNumber, @ColorRes int textColorRes, @ColorRes int tintRes) {
        int textColor;
        if (textColorRes != -1)
            textColor = ContextCompat.getColor(context, textColorRes);
        else if (primary)
            textColor = Color.BLACK;
        else
            textColor = ContextCompat.getColor(context, R.color.colorPrimary_ripple);

        View view = View.inflate(context, R.layout.material_drawer_item_primary, null);
        if (tintRes != -1)
            view.setBackgroundColor(ContextCompat.getColor(context, tintRes));
        ((ImageView) view.findViewById(R.id.materialDrawer_itemIcon)).setImageResource(icon);
        ((TextView) view.findViewById(R.id.materialDrawer_itemName)).setText(title);
        ((TextView) view.findViewById(R.id.materialDrawer_itemName)).setTextColor(textColor);
        if (badgeNumber == -1) {
            view.findViewById(R.id.materialDrawer_itemBadgeContainer).setVisibility(View.GONE);
        } else {
            view.findViewById(R.id.materialDrawer_itemBadgeContainer).setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.materialDrawer_itemBadge)).setText(String.valueOf(badgeNumber));
        }

        return view;
    }

    public DrawerManager updateBadge(int num) {
        View view = drawerList.getChildAt(0);

        if (num == -1) {
            view.findViewById(R.id.materialDrawer_itemBadgeContainer).setVisibility(View.GONE);
        } else {
            view.findViewById(R.id.materialDrawer_itemBadgeContainer).setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.materialDrawer_itemBadge)).setText(String.valueOf(num));
        }

        return this;
    }

    public DrawerManager buildMenu() {
        drawerList.removeAllViews();

        View home = newItem(R.drawable.ic_home_black_48dp, context.getString(R.string.home), true, 0, R.color.colorAccent, R.color.colorAccent_tint);
        home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null)
                    setDrawerState(false, listener.onListItemSelected(DrawerListItems.HOME));
            }
        });
        drawerList.addView(home, 0);

        View terminal = newItem(R.drawable.ic_developer_board_black_48dp, context.getString(R.string.terminal), true);
        terminal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null)
                    setDrawerState(false, listener.onListItemSelected(DrawerListItems.TERMINAL));
            }
        });
        drawerList.addView(terminal);

        View globalOptions = newItem(R.drawable.ic_list_black_48dp, context.getString(R.string.menu_globalOptions), true);
        globalOptions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null)
                    setDrawerState(false, listener.onListItemSelected(DrawerListItems.GLOBAL_OPTIONS));
            }
        });
        drawerList.addView(globalOptions);

        // Footer group
        drawerFooterList.removeAllViews();

        View divider = new View(context);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundResource(R.color.colorPrimary_ripple);
        drawerFooterList.addView(divider, 0);

        View preferences = newItem(R.drawable.ic_settings_black_48dp, context.getString(R.string.menu_preferences), false);
        preferences.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null)
                    setDrawerState(false, listener.onListItemSelected(DrawerListItems.PREFERENCES));
            }
        });
        drawerFooterList.addView(preferences);

        View support = newItem(R.drawable.ic_settings_black_48dp, context.getString(R.string.support), false);
        support.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null)
                    setDrawerState(false, listener.onListItemSelected(DrawerListItems.SUPPORT));
            }
        });
        drawerFooterList.addView(support);

        final ImageView dropdownToggle = (ImageView) drawerLayout.findViewById(R.id.mainDrawerHeader_dropdown);

        dropdownToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final View profileContainer = drawerLayout.findViewById(R.id.mainDrawer_profileContainer);
                final View menuContainer = drawerLayout.findViewById(R.id.mainDrawer_menuContainer);

                if (profileContainer.getVisibility() == View.INVISIBLE) {
                    dropdownToggle.animate()
                            .rotation(180)
                            .setDuration(200)
                            .start();
                    profileContainer.setVisibility(View.VISIBLE);
                    profileContainer.setAlpha(0);
                    profileContainer.animate()
                            .alpha(1)
                            .setListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animator) {

                                }

                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    profileContainer.setAlpha(1);
                                    profilesAdapter.startProfilesTest();
                                }

                                @Override
                                public void onAnimationCancel(Animator animator) {

                                }

                                @Override
                                public void onAnimationRepeat(Animator animator) {

                                }
                            })
                            .setDuration(200)
                            .start();

                    menuContainer.animate()
                            .alpha(0)
                            .setDuration(200)
                            .setListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animator) {

                                }

                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    menuContainer.setVisibility(View.INVISIBLE);
                                }

                                @Override
                                public void onAnimationCancel(Animator animator) {

                                }

                                @Override
                                public void onAnimationRepeat(Animator animator) {

                                }
                            })
                            .start();
                } else {
                    dropdownToggle.animate()
                            .rotation(0)
                            .setDuration(200)
                            .start();

                    menuContainer.setVisibility(View.VISIBLE);
                    menuContainer.setAlpha(0);
                    menuContainer.animate()
                            .alpha(1)
                            .setListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animator) {

                                }

                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    menuContainer.setAlpha(1);
                                }

                                @Override
                                public void onAnimationCancel(Animator animator) {

                                }

                                @Override
                                public void onAnimationRepeat(Animator animator) {

                                }
                            })
                            .setDuration(200)
                            .start();

                    profileContainer.animate()
                            .alpha(0)
                            .setDuration(200)
                            .setListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animator) {

                                }

                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    profileContainer.setVisibility(View.INVISIBLE);
                                }

                                @Override
                                public void onAnimationCancel(Animator animator) {

                                }

                                @Override
                                public void onAnimationRepeat(Animator animator) {

                                }
                            })
                            .start();
                }
            }
        });

        return this;
    }

    public DrawerManager buildProfiles() {
        drawerProfilesFooter.removeAllViews();

        View divider = new View(context);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundResource(R.color.colorPrimary_ripple);
        drawerProfilesFooter.addView(divider, 0);

        View add = newItem(R.drawable.ic_add_black_48dp, context.getString(R.string.addProfile), false);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null)
                    listener.onAddProfile();
            }
        });
        drawerProfilesFooter.addView(add);

        View manage = newItem(R.drawable.ic_settings_black_48dp, context.getString(R.string.manageProfiles), false);
        manage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null)
                    listener.onManageProfiles();
            }
        });
        drawerProfilesFooter.addView(manage);

        // Load profiles
        List<ProfileItem> profiles = new ArrayList<>();
        File files[] = context.getFilesDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.toLowerCase().endsWith(".profile");
            }
        });

        for (File profile : files) {
            try {
                if (ProfileItem.isSingleMode(context, profile)) {
                    profiles.add(SingleModeProfileItem.fromFile(context, profile));
                } else {
                    profiles.add(MultiModeProfileItem.fromFile(context, profile));
                }
            } catch (FileNotFoundException ex) {
                Utils.UIToast(context, Utils.TOAST_MESSAGES.FILE_NOT_FOUND, ex);
            } catch (JSONException | IOException ex) {
                Utils.UIToast(context, Utils.TOAST_MESSAGES.FATAL_EXCEPTION, ex);
                ex.printStackTrace();
            }
        }

        profilesAdapter = new ProfilesAdapter(context, profiles, new ProfilesAdapter.IProfile() {
            @Override
            public void onProfileSelected(SingleModeProfileItem which) {
                if (isProfilesLockedUntilSelected) {
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                    drawerLayout.findViewById(R.id.mainDrawerHeader_dropdown).setEnabled(true);

                    isProfilesLockedUntilSelected = false;
                }

                if (listener != null)
                    listener.onProfileItemSelected(which);
            }
        });
        drawerProfiles.setAdapter(profilesAdapter);

        return this;
    }

    public enum DrawerListItems {
        HOME,
        TERMINAL,
        GLOBAL_OPTIONS,
        PREFERENCES,
        SUPPORT
    }

    public interface IDrawerListener {
        boolean onListItemSelected(DrawerListItems which);

        void onProfileItemSelected(SingleModeProfileItem profile);

        void onAddProfile();

        void onManageProfiles();
    }
}
