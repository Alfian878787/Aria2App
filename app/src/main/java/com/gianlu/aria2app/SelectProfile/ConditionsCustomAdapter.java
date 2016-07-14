package com.gianlu.aria2app.SelectProfile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.gianlu.aria2app.R;
import com.gianlu.aria2app.Utils;

import java.util.Map;
import java.util.Set;

public class ConditionsCustomAdapter extends BaseAdapter {
    private static int currDefault = 0;
    private Map<ConnectivityCondition, SingleModeProfileItem> objs;
    private Activity context;
    private OnClickListener edit;

    public ConditionsCustomAdapter(Activity context, Map<ConnectivityCondition, SingleModeProfileItem> objs, OnClickListener edit) {
        this.context = context;
        this.objs = objs;
        this.edit = edit;
    }

    @Override
    public int getCount() {
        return objs.size();
    }

    @Override
    public ConnectivityCondition getItem(int i) {
        Set<ConnectivityCondition> connectivityConditions = objs.keySet();
        return connectivityConditions.toArray(new ConnectivityCondition[connectivityConditions.size()])[i];
    }

    public SingleModeProfileItem getProfileItem(ConnectivityCondition condition) {
        return objs.get(condition);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @SuppressLint({"InflateParams", "ViewHolder"})
    @Override
    public View getView(final int i, View view, ViewGroup viewGroup) {
        view = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.condition_custom_item, null);
        ((TextView) view.findViewById(R.id.conditionCustomItem_condition)).setText(getItem(i).getFormalName());
        ((TextView) view.findViewById(R.id.conditionCustomItem_url)).setText(getProfileItem(getItem(i)).getFullServerAddr());
        view.findViewById(R.id.conditionCustomItem_edit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                edit.onClick(view, i, getProfileItem(getItem(i)), getItem(i));
            }
        });
        CheckBox _default = (CheckBox) view.findViewById(R.id.conditionCustomItem_default);

        _default.setChecked(i == currDefault);
        getProfileItem(getItem(i)).setDefault(i == currDefault);

        _default.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b)
                    currDefault = i;
                else
                    Utils.UIToast(context, Utils.TOAST_MESSAGES.MUST_PICK_DEFAULT);
                notifyDataSetChanged();
            }
        });

        return view;
    }

    public interface OnClickListener {
        void onClick(View view, int i, SingleModeProfileItem item, ConnectivityCondition condition);
    }
}
