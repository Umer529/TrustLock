package com.example.trustlock.ui.registration;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class RegistrationPagerAdapter extends FragmentStateAdapter {

    public RegistrationPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new Step1Fragment();
            case 1: return new Step2Fragment();
            case 2: return new Step3Fragment();
            default: throw new IllegalArgumentException("Invalid page: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
