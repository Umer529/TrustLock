package com.example.trustlock.ui.registration;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.trustlock.databinding.FragmentStep3Binding;
import com.example.trustlock.viewmodel.RegistrationViewModel;

public class Step3Fragment extends Fragment {

    private FragmentStep3Binding binding;
    private RegistrationViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStep3Binding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        setupNumpad();

        binding.btnBack.setOnClickListener(v -> {
            binding.pinEntryView.clearPin();
            ((RegistrationActivity) requireActivity()).goToPage(1);
        });
    }

    private void setupNumpad() {
        int[] btnIds = {
                binding.btn0.getId(), binding.btn1.getId(), binding.btn2.getId(),
                binding.btn3.getId(), binding.btn4.getId(), binding.btn5.getId(),
                binding.btn6.getId(), binding.btn7.getId(), binding.btn8.getId(),
                binding.btn9.getId()
        };

        // Digit buttons 1–9
        binding.btn1.setOnClickListener(v -> onDigit(1));
        binding.btn2.setOnClickListener(v -> onDigit(2));
        binding.btn3.setOnClickListener(v -> onDigit(3));
        binding.btn4.setOnClickListener(v -> onDigit(4));
        binding.btn5.setOnClickListener(v -> onDigit(5));
        binding.btn6.setOnClickListener(v -> onDigit(6));
        binding.btn7.setOnClickListener(v -> onDigit(7));
        binding.btn8.setOnClickListener(v -> onDigit(8));
        binding.btn9.setOnClickListener(v -> onDigit(9));
        binding.btn0.setOnClickListener(v -> onDigit(0));

        binding.btnDelete.setOnClickListener(v -> binding.pinEntryView.removeDigit());
    }

    private void onDigit(int digit) {
        binding.pinEntryView.addDigit(digit);
        if (binding.pinEntryView.isFull()) {
            submitPin();
        }
    }

    private void submitPin() {
        String pin = binding.pinEntryView.getPin();
        viewModel.setPin(pin);
        // Trigger Firebase registration
        viewModel.saveUserToFirebase();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
