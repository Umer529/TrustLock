package com.example.trustlock.ui.apps;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.trustlock.databinding.FragmentAppsBinding;
import com.example.trustlock.models.AppInfo;
import com.example.trustlock.models.AppLimit;
import com.example.trustlock.ui.approval.WaitingForApprovalDialog;
import com.example.trustlock.viewmodel.AppLimitViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppsFragment extends Fragment {

    private FragmentAppsBinding binding;
    private AppLimitViewModel   viewModel;
    private InstalledAppsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAppsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Activity-scoped — shares the same instance as HomeFragment and SetLimitBottomSheet
        viewModel = new ViewModelProvider(requireActivity()).get(AppLimitViewModel.class);

        adapter = new InstalledAppsAdapter(this::onAppSelected);
        binding.rvInstalledApps.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvInstalledApps.setAdapter(adapter);

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
        });

        viewModel.getInstalledApps().observe(getViewLifecycleOwner(), apps -> {
            binding.progressLoading.setVisibility(View.GONE);
            binding.rvInstalledApps.setVisibility(View.VISIBLE);
            List<AppLimit> limits = viewModel.getAppLimits().getValue();
            adapter.setItems(apps, buildLimitedSet(limits));
        });

        viewModel.getAppLimits().observe(getViewLifecycleOwner(), limits -> {
            List<AppInfo> apps = viewModel.getInstalledApps().getValue();
            if (apps != null) {
                adapter.setItems(apps, buildLimitedSet(limits));
            }
        });

        viewModel.getPendingApproval().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            viewModel.clearPendingApproval();

            WaitingForApprovalDialog dialog = WaitingForApprovalDialog.newInstance(
                    data.requestId, data.guardianEmail, data.description);
            dialog.setOnApprovalResultListener(new WaitingForApprovalDialog.OnApprovalResultListener() {
                @Override public void onApproved()  { viewModel.executePendingAction(); }
                @Override public void onDenied() {
                    Snackbar.make(requireView(), "Request denied by guardian",
                            Snackbar.LENGTH_LONG).show();
                }
            });
            dialog.show(getChildFragmentManager(), "waiting_approval");
        });

        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void onAppSelected(AppInfo app) {
        List<AppLimit> limits = viewModel.getAppLimits().getValue();
        int currentLimit = 0;
        if (limits != null) {
            for (AppLimit l : limits) {
                if (l.getPackageName().equals(app.getPackageName())) {
                    currentLimit = l.getDailyLimitMinutes();
                    break;
                }
            }
        }
        SetLimitBottomSheet.newInstance(app.getPackageName(), app.getAppName(), currentLimit)
                .show(getChildFragmentManager(), "set_limit");
    }

    private Set<String> buildLimitedSet(List<AppLimit> limits) {
        Set<String> set = new HashSet<>();
        if (limits != null) for (AppLimit l : limits) set.add(l.getPackageName());
        return set;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
