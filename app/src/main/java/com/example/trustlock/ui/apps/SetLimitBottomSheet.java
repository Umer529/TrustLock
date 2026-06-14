package com.example.trustlock.ui.apps;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.trustlock.databinding.BottomSheetSetLimitBinding;
import com.example.trustlock.models.AppLimit;
import com.example.trustlock.viewmodel.AppLimitViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class SetLimitBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_PACKAGE = "packageName";
    private static final String ARG_NAME    = "appName";
    private static final String ARG_CURRENT = "currentMinutes";

    public static SetLimitBottomSheet newInstance(String packageName, String appName,
                                                   int currentLimitMinutes) {
        SetLimitBottomSheet sheet = new SetLimitBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PACKAGE, packageName);
        args.putString(ARG_NAME,    appName);
        args.putInt(ARG_CURRENT,    currentLimitMinutes);
        sheet.setArguments(args);
        return sheet;
    }

    private BottomSheetSetLimitBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSetLimitBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args          = requireArguments();
        String packageName   = args.getString(ARG_PACKAGE);
        String appName       = args.getString(ARG_NAME);
        int    currentMinutes = args.getInt(ARG_CURRENT, 0);

        AppLimitViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(AppLimitViewModel.class);

        binding.tvSheetAppName.setText(appName);

        binding.npHours.setMinValue(0);
        binding.npHours.setMaxValue(12);
        binding.npMinutes.setMinValue(0);
        binding.npMinutes.setMaxValue(59);
        binding.npHours.setValue(currentMinutes / 60);
        binding.npMinutes.setValue(currentMinutes % 60);

        applyPickerTextColor(binding.npHours,    android.graphics.Color.WHITE);
        applyPickerTextColor(binding.npMinutes,  android.graphics.Color.WHITE);

        // "Update Limit" when editing an existing one, "Set Limit" for new
        if (currentMinutes > 0) {
            binding.btnSetLimit.setText("Update Limit");
        }

        if (currentMinutes > 0) {
            binding.btnRemoveLimit.setVisibility(View.VISIBLE);
            binding.btnRemoveLimit.setOnClickListener(v ->
                    confirmGuardianRequest(
                            "Ask guardian to remove limit?",
                            "Your guardian will be emailed a request to remove the limit on "
                                    + appName + ". Once sent, the email cannot be recalled.",
                            () -> {
                                viewModel.requestRemoveLimit(packageName, appName);
                                dismiss();
                            }));
        }

        binding.btnSetLimit.setOnClickListener(v -> {
            int totalMinutes = binding.npHours.getValue() * 60 + binding.npMinutes.getValue();
            if (totalMinutes == 0) {
                binding.btnSetLimit.setError("Set a limit greater than 0");
                return;
            }
            // New limits don't require guardian approval; changes to an
            // existing one do — confirm before sending in that case.
            if (currentMinutes > 0) {
                confirmGuardianRequest(
                        "Ask guardian to change limit?",
                        "Your guardian will be emailed a request to change the limit on "
                                + appName + " to " + formatLimit(totalMinutes)
                                + ". Once sent, the email cannot be recalled.",
                        () -> {
                            viewModel.requestLimitChange(
                                    new AppLimit(packageName, appName, totalMinutes, true));
                            dismiss();
                        });
            } else {
                viewModel.requestLimitChange(
                        new AppLimit(packageName, appName, totalMinutes, true));
                dismiss();
            }
        });
    }

    private void confirmGuardianRequest(String title, String message, Runnable onConfirm) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Not now", null)
                .setPositiveButton("Send request", (d, w) -> onConfirm.run())
                .show();
    }

    private static String formatLimit(int minutes) {
        if (minutes >= 60) {
            int h = minutes / 60;
            int m = minutes % 60;
            return (m == 0 ? h + "h" : h + "h " + m + "m");
        }
        return minutes + "m";
    }

    private void applyPickerTextColor(NumberPicker picker, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            picker.setTextColor(color);
        } else {
            try {
                java.lang.reflect.Field field =
                        NumberPicker.class.getDeclaredField("mInputText");
                field.setAccessible(true);
                android.widget.TextView tv = (android.widget.TextView) field.get(picker);
                if (tv != null) tv.setTextColor(color);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
