package com.example.trustlock.ui.registration;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.trustlock.databinding.FragmentStep1Binding;
import com.example.trustlock.viewmodel.RegistrationViewModel;

public class Step1Fragment extends Fragment {

    private FragmentStep1Binding binding;
    private RegistrationViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStep1Binding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Shared ViewModel scoped to the Activity so all steps share state
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        // Restore previously entered values
        binding.etName.setText(viewModel.getName().getValue());
        binding.etEmail.setText(viewModel.getEmail().getValue());

        binding.btnNext.setOnClickListener(v -> {
            String name = getText(binding.etName);
            String email = getText(binding.etEmail);

            if (TextUtils.isEmpty(name)) {
                binding.tilName.setError("Name is required");
                return;
            }
            binding.tilName.setError(null);

            if (!viewModel.isValidEmail(email)) {
                binding.tilEmail.setError("Enter a valid email");
                return;
            }
            binding.tilEmail.setError(null);

            viewModel.setName(name);
            viewModel.setEmail(email);
            ((RegistrationActivity) requireActivity()).goToPage(1);
        });
    }

    private String getText(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
