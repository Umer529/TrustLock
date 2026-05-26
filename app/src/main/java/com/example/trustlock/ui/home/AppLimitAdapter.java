package com.example.trustlock.ui.home;

import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.trustlock.R;
import com.example.trustlock.databinding.ItemAppLimitBinding;
import com.example.trustlock.models.AppLimit;
import com.example.trustlock.util.UsageStatsHelper;

import java.util.ArrayList;
import java.util.List;

public class AppLimitAdapter extends RecyclerView.Adapter<AppLimitAdapter.ViewHolder> {

    public interface OnEditClickListener {
        void onEdit(AppLimit limit);
    }

    private List<AppLimit> items = new ArrayList<>();
    private final OnEditClickListener listener;

    public AppLimitAdapter(OnEditClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<AppLimit> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemAppLimitBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemAppLimitBinding binding;

        ViewHolder(ItemAppLimitBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AppLimit limit) {
            binding.tvAppName.setText(limit.getAppName());

            long usedMinutes = UsageStatsHelper.getTodayUsageMinutes(
                    binding.getRoot().getContext(), limit.getPackageName());
            int limitMinutes = limit.getDailyLimitMinutes();

            binding.tvUsage.setText(formatMinutes(usedMinutes) + " / " + formatMinutes(limitMinutes));

            int progress = limitMinutes > 0 ? (int) (usedMinutes * 100L / limitMinutes) : 0;
            binding.progressUsage.setProgress(Math.min(progress, 100));

            // Red when above 80 % of daily limit
            int tintColor = progress > 80
                    ? ContextCompat.getColor(binding.getRoot().getContext(), R.color.error)
                    : ContextCompat.getColor(binding.getRoot().getContext(), R.color.purple_primary);
            binding.progressUsage.setProgressTintList(ColorStateList.valueOf(tintColor));

            // Load app icon via Glide — falls back to a generic icon on failure
            try {
                Drawable icon = binding.getRoot().getContext()
                        .getPackageManager().getApplicationIcon(limit.getPackageName());
                Glide.with(binding.getRoot().getContext())
                        .load(icon)
                        .into(binding.ivAppIcon);
            } catch (PackageManager.NameNotFoundException e) {
                binding.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }

            binding.btnEdit.setOnClickListener(v -> listener.onEdit(limit));
        }

        private String formatMinutes(long minutes) {
            if (minutes >= 60) {
                return (minutes / 60) + "h " + (minutes % 60) + "m";
            }
            return minutes + "m";
        }
    }
}
