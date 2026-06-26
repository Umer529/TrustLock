package com.example.trustlock;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.trustlock.ui.guardian.GuardianApprovalsFragment;
import com.example.trustlock.ui.guardian.GuardianHomeFragment;
import com.example.trustlock.ui.guardian.GuardianLimitsFragment;
import com.example.trustlock.ui.guardian.GuardianSettingsFragment;
import com.example.trustlock.ui.guardian.LiveMonitoringFragment;

public class GuardianPagerAdapter extends FragmentStateAdapter {

    public GuardianPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new GuardianHomeFragment();
            case 1: return new LiveMonitoringFragment();
            case 2: return new GuardianLimitsFragment();
            case 3: return new GuardianApprovalsFragment();
            case 4: return new GuardianSettingsFragment();
            default: throw new IllegalStateException("Invalid tab position: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return 5;
    }
}
