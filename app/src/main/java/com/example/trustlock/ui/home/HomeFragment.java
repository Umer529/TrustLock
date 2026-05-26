package com.example.trustlock.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.trustlock.databinding.FragmentHomeBinding;
import com.example.trustlock.models.AppLimit;
import com.example.trustlock.ui.apps.AddAppLimitActivity;
import com.example.trustlock.viewmodel.AppLimitViewModel;

import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private AppLimitViewModel viewModel;
    private AppLimitAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AppLimitViewModel.class);

        setupRecyclerView();
        observeViewModel();

        binding.fabAddApp.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddAppLimitActivity.class)));
    }

    private void setupRecyclerView() {
        adapter = new AppLimitAdapter(this::onEditApp);
        binding.rvAppLimits.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAppLimits.setAdapter(adapter);
        // RecyclerView is inside NestedScrollView — disable its own scrolling
        binding.rvAppLimits.setNestedScrollingEnabled(false);
    }

    private void observeViewModel() {
        viewModel.getAppLimits().observe(getViewLifecycleOwner(), limits -> {
            boolean isEmpty = limits == null || limits.isEmpty();
            binding.rvAppLimits.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

            if (!isEmpty) {
                adapter.setItems(limits);
                updateSummaryCard(limits);
            } else {
                binding.tvTotalTime.setText("0 hrs 0 min");
                binding.tvTrackingCount.setText("Tracking 0 apps");
            }
        });
    }

    private void updateSummaryCard(List<AppLimit> limits) {
        long totalMinutes = 0;
        for (AppLimit limit : limits) {
            totalMinutes += viewModel.getTodayUsageMinutes(limit.getPackageName());
        }
        long hours = totalMinutes / 60;
        long mins = totalMinutes % 60;
        binding.tvTotalTime.setText(hours + " hrs " + mins + " min");
        binding.tvTrackingCount.setText("Tracking " + limits.size() + " app" + (limits.size() == 1 ? "" : "s"));
    }

    private void onEditApp(AppLimit limit) {
        // Open AddAppLimitActivity in edit mode, pre-selecting the app
        Intent intent = new Intent(requireContext(), AddAppLimitActivity.class);
        intent.putExtra(AddAppLimitActivity.EXTRA_EDIT_PACKAGE, limit.getPackageName());
        intent.putExtra(AddAppLimitActivity.EXTRA_EDIT_APP_NAME, limit.getAppName());
        intent.putExtra(AddAppLimitActivity.EXTRA_CURRENT_LIMIT, limit.getDailyLimitMinutes());
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
