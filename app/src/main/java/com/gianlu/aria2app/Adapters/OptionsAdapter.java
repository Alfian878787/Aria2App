package com.gianlu.aria2app.Adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.gianlu.aria2app.Options.Option;
import com.gianlu.aria2app.Options.OptionsManager;
import com.gianlu.aria2app.R;
import com.gianlu.commonutils.SuperTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OptionsAdapter extends RecyclerView.Adapter<OptionsAdapter.ViewHolder> {
    private final List<Option> options;
    private final List<Option> originalOptions;
    private final LayoutInflater inflater;
    private final boolean global;
    private final Context context;
    private IAdapter handler;

    public OptionsAdapter(Context context, List<Option> options, boolean global, boolean quickOnTop) {
        this.context = context;
        this.originalOptions = options;
        this.options = new ArrayList<>(options);
        this.inflater = LayoutInflater.from(context);
        this.global = global;

        if (quickOnTop)
            Collections.sort(this.options, new OptionsManager.IsQuickComparator(context, global)); // Assumes that options are already ordered alphabetically
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(parent);
    }

    public void setHandler(IAdapter handler) {
        this.handler = handler;
    }

    public void notifyItemChanged(Option option) {
        int pos = options.indexOf(option);
        if (pos != -1) {
            options.set(pos, option);
            super.notifyItemChanged(pos);
        }

        int realPos = originalOptions.indexOf(option);
        if (realPos != -1) originalOptions.set(realPos, option);
    }

    public void filter(@Nullable String query) {
        options.clear();
        if (query == null || query.isEmpty()) {
            options.addAll(originalOptions);
            notifyDataSetChanged();
            return;
        }

        for (Option option : originalOptions)
            if (option.name.startsWith(query) || option.name.contains(query))
                options.add(option);

        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final Option option = options.get(position);

        holder.name.setText(option.name);

        if (option.isValueChanged()) {
            holder.value.setText(option.newValue);
            holder.value.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
        } else {
            holder.value.setText(option.value);
            holder.value.setTextColor(ContextCompat.getColor(context, android.R.color.secondary_text_light));
        }

        if (option.isQuick(context, global))
            holder.toggleFavourite.setImageResource(R.drawable.ic_favorite_black_48dp);
        else
            holder.toggleFavourite.setImageResource(R.drawable.ic_favorite_border_black_48dp);

        holder.edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (handler != null) handler.onEditOption(option);
            }
        });

        holder.toggleFavourite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isQuick = !option.isQuick(context, global);
                option.setQuick(context, global, isQuick);

                int oldIndex = holder.getAdapterPosition();
                notifyItemChanged(oldIndex);

                if (!isQuick) Collections.sort(options);  // Order alphabetically
                Collections.sort(options, new OptionsManager.IsQuickComparator(context, global));

                int newIndex = options.indexOf(option);
                if (newIndex != -1) notifyItemMoved(oldIndex, newIndex);
            }
        });

        holder.info.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://aria2.github.io/manual/en/html/aria2c.html#cmdoption--" + option.name)));
            }
        });
    }

    @Override
    public int getItemCount() {
        return options.size();
    }

    public List<Option> getOptions() {
        return originalOptions;
    }

    public interface IAdapter {
        void onEditOption(Option option);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        final SuperTextView name;
        final SuperTextView value;
        final ImageButton edit;
        final ImageButton toggleFavourite;
        final ImageButton info;

        ViewHolder(ViewGroup parent) {
            super(inflater.inflate(R.layout.option_item, parent, false));

            name = itemView.findViewById(R.id.optionItem_name);
            value = itemView.findViewById(R.id.optionItem_value);
            edit = itemView.findViewById(R.id.optionItem_edit);
            toggleFavourite = itemView.findViewById(R.id.optionItem_toggleFavourite);
            info = itemView.findViewById(R.id.optionItem_info);
        }
    }
}
