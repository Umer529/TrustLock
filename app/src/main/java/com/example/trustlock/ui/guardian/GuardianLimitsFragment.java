package com.example.trustlock.ui.guardian;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.trustlock.R;

/** Step 5b lands the real limits editor here. */
public class GuardianLimitsFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_guardian_placeholder, parent, false);
        ((TextView) v.findViewById(R.id.tvPlaceholderTitle)).setText("Limits");
        return v;
    }
}
