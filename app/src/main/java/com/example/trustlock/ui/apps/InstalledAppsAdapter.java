package com.example.trustlock.ui.apps;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.trustlock.databinding.ItemInstalledAppBinding;
import com.example.trustlock.models.AppInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class InstalledAppsAdapter extends RecyclerView.Adapter<InstalledAppsAdapter.ViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }

    private List<AppInfo> allItems = new ArrayList<>();
    private List<AppInfo> filteredItems = new ArrayList<>();
    private Set<String> limitedPackages;
    private final OnAppClickListener listener;

    public InstalledAppsAdapter(OnAppClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<AppInfo> items, Set<String> limitedPackages) {
        this.allItems = items != null ? items : new ArrayList<>();
        this.limitedPackages = limitedPackages;
        this.filteredItems = new ArrayList<>(allItems);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        filteredItems = new ArrayList<>();
        String lower = query.toLowerCase().trim();
        for (AppInfo app : allItems) {
            if (app.getAppName().toLowerCase().contains(lower)
                    || app.getPackageName().toLowerCase().contains(lower)) {
                filteredItems.add(app);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemInstalledAppBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(filteredItems.get(position));
    }

    @Override
    public int getItemCount() {
        return filteredItems.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemInstalledAppBinding binding;

        ViewHolder(ItemInstalledAppBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AppInfo app) {
            binding.tvAppName.setText(app.getAppName());
            binding.tvPackageName.setText(app.getPackageName());

            boolean isLimited = limitedPackages != null
                    && limitedPackages.contains(app.getPackageName());
            binding.tvLimitSet.setVisibility(isLimited ? View.VISIBLE : View.GONE);

            try {
                Drawable icon = binding.getRoot().getContext()
                        .getPackageManager().getApplicationIcon(app.getPackageName());
                Glide.with(binding.getRoot().getContext())
                        .load(icon)
                        .into(binding.ivAppIcon);
            } catch (PackageManager.NameNotFoundException e) {
                binding.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }

            binding.getRoot().setOnClickListener(v -> listener.onAppClick(app));
        }
    }
}
