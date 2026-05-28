package com.example.trustlock;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.trustlock.ui.apps.AppsFragment;
import com.example.trustlock.ui.home.HomeFragment;
import com.example.trustlock.ui.settings.SettingsFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new HomeFragment();
            case 1: return new AppsFragment();
            case 2: return new SettingsFragment();
            default: throw new IllegalStateException("Invalid tab position: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
