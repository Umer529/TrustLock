package com.example.trustlock.ui.permissions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.trustlock.R;
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
            binding.tvPermCategory.setText(item.getCategory());
            binding.tvPermName.setText(item.getName());
            binding.tvPermDesc.setText(item.getDescription());
            binding.ivPermIcon.setImageResource(item.getIconRes());

            android.content.Context ctx = binding.getRoot().getContext();

            if (item.isGranted()) {
                // Settled / green look — no longer asking for action.
                binding.cardRoot.setCardBackgroundColor(0); // let bg drawable show
                binding.cardRoot.setBackgroundResource(R.drawable.bg_perm_card_granted);
                binding.iconBackdrop.setBackgroundResource(R.drawable.bg_perm_icon_granted);
                binding.ivPermIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.success));

                binding.ivGrantedCheck.setVisibility(View.VISIBLE);
                binding.ivChevron.setVisibility(View.GONE);

                binding.tvStatusPill.setText("GRANTED");
                binding.tvStatusPill.setTextColor(ContextCompat.getColor(ctx, R.color.success));
                binding.tvStatusPill.setBackgroundResource(R.drawable.bg_status_pill_granted);

                binding.cardRoot.setOnClickListener(null);
                binding.cardRoot.setClickable(false);
            } else {
                // Action-needed look — purple icon + tappable card + chevron.
                binding.cardRoot.setBackgroundResource(0);
                binding.cardRoot.setCardBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.surface_variant));
                binding.iconBackdrop.setBackgroundResource(R.drawable.bg_purple_circle);
                binding.ivPermIcon.setColorFilter(
                        ContextCompat.getColor(ctx, R.color.purple_light));

                binding.ivGrantedCheck.setVisibility(View.GONE);
                binding.ivChevron.setVisibility(View.VISIBLE);

                if (item.isOptional()) {
                    binding.tvStatusPill.setText("OPTIONAL");
                    binding.tvStatusPill.setTextColor(
                            ContextCompat.getColor(ctx, R.color.purple_light));
                    binding.tvStatusPill.setBackgroundResource(
                            R.drawable.bg_status_pill_optional);
                } else {
                    binding.tvStatusPill.setText("REQUIRED");
                    binding.tvStatusPill.setTextColor(
                            ContextCompat.getColor(ctx, R.color.error));
                    binding.tvStatusPill.setBackgroundResource(
                            R.drawable.bg_status_pill_required);
                }

                binding.cardRoot.setClickable(true);
                binding.cardRoot.setOnClickListener(v ->
                        listener.onGrantClick(item, getAdapterPosition()));
            }
        }
    }
}
