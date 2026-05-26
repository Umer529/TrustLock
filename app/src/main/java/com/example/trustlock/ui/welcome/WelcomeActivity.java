package com.example.trustlock.ui.welcome;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trustlock.databinding.ActivityWelcomeBinding;
import com.example.trustlock.ui.registration.RegistrationActivity;

public class WelcomeActivity extends AppCompatActivity {

    private ActivityWelcomeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnGetStarted.setOnClickListener(v -> {
            startActivity(new Intent(this, RegistrationActivity.class));
            finish();
        });
    }
}
