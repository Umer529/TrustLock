package com.example.trustlock.ui.permissions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.trustlock.databinding.ItemPermissionBinding;

import java.util.List;

public class PermissionsAdapter extends RecyclerView.Adapter<PermissionsAdapter.ViewHolder> {

    public interface OnGrantClickListener {
        void onGrantClick(PermissionItem item, int position);
    }

    private final List<PermissionItem> items;
    private final OnGrantClickListener listener;

    public PermissionsAdapter(List<PermissionItem> items, OnGrantClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPermissionBinding binding = ItemPermissionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void markGranted(int position) {
        items.get(position).setGranted(true);
        notifyItemChanged(position);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemPermissionBinding binding;

        ViewHolder(ItemPermissionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PermissionItem item) {
            binding.tvPermName.setText(item.getName());
            binding.tvPermDesc.setText(item.getDescription());
            binding.ivPermIcon.setImageResource(item.getIconRes());

            if (item.isGranted()) {
                binding.btnGrant.setVisibility(View.GONE);
                binding.ivGranted.setVisibility(View.VISIBLE);
            } else {
                binding.btnGrant.setVisibility(View.VISIBLE);
                binding.ivGranted.setVisibility(View.GONE);
                binding.btnGrant.setOnClickListener(v ->
                        listener.onGrantClick(item, getAdapterPosition()));
            }
        }
    }
}
