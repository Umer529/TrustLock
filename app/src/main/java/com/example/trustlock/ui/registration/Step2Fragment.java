package com.example.trustlock.ui.registration;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.trustlock.databinding.FragmentStep2Binding;
import com.example.trustlock.viewmodel.RegistrationViewModel;
import com.google.android.material.snackbar.Snackbar;

public class Step2Fragment extends Fragment {

    private FragmentStep2Binding binding;
    private RegistrationViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStep2Binding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        binding.etGuardianEmail.setText(viewModel.getGuardianEmail().getValue());

        binding.btnBack.setOnClickListener(v ->
                ((RegistrationActivity) requireActivity()).goToPage(0));

        binding.btnNext.setOnClickListener(v -> {
            String guardianEmail = binding.etGuardianEmail.getText() != null
                    ? binding.etGuardianEmail.getText().toString().trim() : "";

            if (!viewModel.isValidEmail(guardianEmail)) {
                binding.tilGuardianEmail.setError("Enter a valid guardian email");
                return;
            }

            String userEmail = viewModel.getEmail().getValue();
            if (guardianEmail.equalsIgnoreCase(userEmail)) {
                binding.tilGuardianEmail.setError("Guardian email must be different from yours");
                return;
            }

            binding.tilGuardianEmail.setError(null);
            viewModel.setGuardianEmail(guardianEmail);
            viewModel.saveUserToFirebase();
        });

        viewModel.getRegistrationState().observe(getViewLifecycleOwner(), state -> {
            switch (state) {
                case LOADING:
                    binding.btnNext.setEnabled(false);
                    binding.btnNext.setText("Creating account...");
                    break;
                case ERROR:
                    binding.btnNext.setEnabled(true);
                    binding.btnNext.setText("Complete Setup");
                    break;
                default:
                    break;
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
