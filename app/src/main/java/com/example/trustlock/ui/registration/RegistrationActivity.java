package com.example.trustlock.ui.registration;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.trustlock.databinding.ActivityRegistrationBinding;
import com.example.trustlock.ui.permissions.PermissionsActivity;
import com.example.trustlock.viewmodel.RegistrationViewModel;

public class RegistrationActivity extends AppCompatActivity {

    private ActivityRegistrationBinding binding;
    private RegistrationViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegistrationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(RegistrationViewModel.class);

        RegistrationPagerAdapter adapter = new RegistrationPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);
        // Prevent swipe — navigation is driven by Next/Back buttons
        binding.viewPager.setUserInputEnabled(false);

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getRegistrationState().observe(this, state -> {
            switch (state) {
                case SUCCESS:
                    startActivity(new Intent(this, PermissionsActivity.class));
                    finish();
                    break;
                case LOADING:
                    // Step3Fragment disables its button — nothing else needed here
                    break;
                case ERROR:
                    // Errors are shown as Snackbars by the fragments via errorMessage LiveData
                    break;
            }
        });
    }

    /** Called by step fragments to move to a specific page. */
    public void goToPage(int page) {
        binding.viewPager.setCurrentItem(page, true);
        updateDots(page);
    }

    private void updateDots(int activePage) {
        View[] dots = {binding.dot1, binding.dot2, binding.dot3};
        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackgroundResource(
                    i == activePage
                            ? com.example.trustlock.R.drawable.dot_active
                            : com.example.trustlock.R.drawable.dot_inactive
            );
        }
    }
}
