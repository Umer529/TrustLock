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
    private static final String ARG_NAME = "appName";
    private static final String ARG_CURRENT = "currentMinutes";

    public static SetLimitBottomSheet newInstance(String packageName, String appName,
                                                   int currentLimitMinutes) {
        SetLimitBottomSheet sheet = new SetLimitBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PACKAGE, packageName);
        args.putString(ARG_NAME, appName);
        args.putInt(ARG_CURRENT, currentLimitMinutes);
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

        Bundle args = requireArguments();
        String packageName = args.getString(ARG_PACKAGE);
        String appName = args.getString(ARG_NAME);
        int currentMinutes = args.getInt(ARG_CURRENT, 0);

        // ViewModel is scoped to the host Activity so it shares state with AddAppLimitActivity
        AppLimitViewModel viewModel = new ViewModelProvider(requireActivity())
                .get(AppLimitViewModel.class);

        binding.tvSheetAppName.setText(appName);

        // Configure pickers
        binding.npHours.setMinValue(0);
        binding.npHours.setMaxValue(12);
        binding.npMinutes.setMinValue(0);
        binding.npMinutes.setMaxValue(59);

        // Pre-select current limit values if editing
        binding.npHours.setValue(currentMinutes / 60);
        binding.npMinutes.setValue(currentMinutes % 60);

        // NumberPicker text is dark by default — force white for dark theme
        applyPickerTextColor(binding.npHours, android.graphics.Color.WHITE);
        applyPickerTextColor(binding.npMinutes, android.graphics.Color.WHITE);

        binding.btnSetLimit.setOnClickListener(v -> {
            int hours = binding.npHours.getValue();
            int minutes = binding.npMinutes.getValue();
            int totalMinutes = hours * 60 + minutes;

            if (totalMinutes == 0) {
                binding.btnSetLimit.setError("Please set a limit greater than 0");
                return;
            }

            AppLimit limit = new AppLimit(packageName, appName, totalMinutes, true);
            viewModel.requestLimitChange(limit);
            dismiss();
        });
    }

    /**
     * Forces the NumberPicker's internal EditText to use a specific text color.
     * NumberPicker ignores theme text colors on many API levels.
     */
    private void applyPickerTextColor(NumberPicker picker, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            picker.setTextColor(color);
        } else {
            try {
                java.lang.reflect.Field field = NumberPicker.class.getDeclaredField("mInputText");
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
