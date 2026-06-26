package com.example.trustlock.ui.guardian;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.trustlock.R;
import com.example.trustlock.models.UsageLog;

import java.util.ArrayList;
import java.util.List;

public class LiveAppAdapter extends RecyclerView.Adapter<LiveAppAdapter.AppVH> {

    private final List<UsageLog> items = new ArrayList<>();

    public void submit(List<UsageLog> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_live_app, parent, false);
        return new AppVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull AppVH h, int position) {
        UsageLog log = items.get(position);
        h.name.setText(log.getAppName() != null ? log.getAppName() : log.getPackageName());
        h.pkg.setText(log.getPackageName());
        h.minutes.setText(formatMinutes(log.getMinutesUsed()));
    }

    @Override
    public int getItemCount() { return items.size(); }

    private static String formatMinutes(int minutes) {
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60;
        int m = minutes % 60;
        return m == 0 ? h + " hr" : h + " hr " + m + " min";
    }

    static class AppVH extends RecyclerView.ViewHolder {
        final TextView name, pkg, minutes;
        AppVH(@NonNull View v) {
            super(v);
            name    = v.findViewById(R.id.tvAppName);
            pkg     = v.findViewById(R.id.tvPackage);
            minutes = v.findViewById(R.id.tvMinutes);
        }
    }
}
