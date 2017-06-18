package com.gianlu.aria2app.Adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;

import com.gianlu.aria2app.ProfilesManager.MultiProfile;
import com.gianlu.aria2app.R;

import java.util.List;

public class RadioConditionsAdapter extends ArrayAdapter<MultiProfile.ConnectivityCondition> {
    private final Context context;

    public RadioConditionsAdapter(@NonNull Context context, @NonNull List<MultiProfile.ConnectivityCondition> objects) {
        super(context, R.layout.radio_condition_item, objects);
        this.context = context;
    }

    @NonNull
    @Override
    @SuppressWarnings("ConstantConditions")
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        CheckedTextView text = (CheckedTextView) super.getView(position, convertView, parent);
        text.setText(getItem(position).getFormal(context));
        return text;
    }
}
