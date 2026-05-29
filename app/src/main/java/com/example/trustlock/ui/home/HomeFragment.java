package com.example.trustlock.ui.home;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.trustlock.R;
import com.example.trustlock.databinding.FragmentHomeBinding;
import com.example.trustlock.models.AppLimit;
import com.example.trustlock.ui.apps.AddAppLimitActivity;
import com.example.trustlock.util.DailyStatsManager;
import com.example.trustlock.util.UsageStatsHelper;
import com.example.trustlock.viewmodel.AppLimitViewModel;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private AppLimitViewModel viewModel;
    private AppLimitAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Activity-scoped so HomeFragment, AppsFragment and SetLimitBottomSheet share one instance
        viewModel = new ViewModelProvider(requireActivity()).get(AppLimitViewModel.class);

        setupRecyclerView();
        observeViewModel();

        binding.fabAddApp.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddAppLimitActivity.class)));

        binding.swipeRefresh.setColorSchemeResources(
                com.example.trustlock.R.color.purple_primary);
        binding.swipeRefresh.setOnRefreshListener(() -> {
            viewModel.loadAppLimits();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh usage stats every time the user navigates back to this tab
        viewModel.loadAppLimits();
        renderWeeklyChart();
    }

    // ─── Weekly chart ────────────────────────────────────────────────────────

    private static final String[] DAY_INITIALS = {"S", "M", "T", "W", "T", "F", "S"};
    private static final String[] DAY_FULL = {
            "Sunday", "Monday", "Tuesday", "Wednesday",
            "Thursday", "Friday", "Saturday"};

    /**
     * Renders the past-7-days bar chart. Today's usage is computed live from
     * UsageStatsHelper; previous days come from DailyStatsManager snapshots.
     * Bars animate from zero to their actual height with a small stagger.
     */
    private void renderWeeklyChart() {
        if (binding == null) return;

        DailyStatsManager statsManager = new DailyStatsManager(requireContext());

        // Collect minutes for each of the past 7 days (index 0 = 6 days ago, 6 = today)
        long[] minutesPerDay = new long[7];
        String[] dateKeys    = new String[7];
        int[]    dayOfWeek   = new int[7];
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -6);

        SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        for (int i = 0; i < 7; i++) {
            String key = keyFmt.format(cal.getTime());
            dateKeys[i]  = key;
            dayOfWeek[i] = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday
            if (i == 6) {
                // Today: include live, in-progress usage of every limited app
                minutesPerDay[i] = liveTodayTotalMinutes();
            } else {
                minutesPerDay[i] = statsManager.getTotalDailyUsage(key);
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        long maxMins = 1; // avoid divide-by-zero
        long totalMins = 0;
        for (long m : minutesPerDay) {
            if (m > maxMins) maxMins = m;
            totalMins += m;
        }

        // Summary numbers
        long avgMins = totalMins / 7;
        binding.tvWeeklyAvg.setText(formatHm(avgMins));
        binding.tvWeeklyTotal.setText(formatHm(totalMins));

        // Build the columns
        LinearLayout barsContainer   = binding.weeklyBarsContainer;
        LinearLayout labelsContainer = binding.weeklyLabelsContainer;
        barsContainer.removeAllViews();
        labelsContainer.removeAllViews();

        int chartHeightPx = dp(160);
        long finalMaxMins = maxMins;
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            final long minutesForDay = minutesPerDay[i];

            // Column container that lays the bar against the bottom
            FrameLayout column = new FrameLayout(requireContext());
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            colLp.setMarginStart(i == 0 ? 0 : dp(4));
            column.setLayoutParams(colLp);

            // Background track so the column has a visible silhouette even at zero
            View track = new View(requireContext());
            FrameLayout.LayoutParams trackLp = new FrameLayout.LayoutParams(
                    dp(18), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
            track.setLayoutParams(trackLp);
            track.setBackgroundResource(R.drawable.bg_bar_track);
            column.addView(track);

            // The actual bar — height is set after a layout pass so the animation
            // computes the right pixel value relative to the column.
            View bar = new View(requireContext());
            FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                    dp(18), 0, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            bar.setLayoutParams(barLp);
            bar.setBackgroundResource(i == 6
                    ? R.drawable.bg_bar_today
                    : R.drawable.bg_bar_day);
            column.addView(bar);

            // Min visible bar height so user can see "0m" days
            int targetPx = (int) Math.max(
                    dp(4),
                    chartHeightPx * (minutesForDay / (float) finalMaxMins));

            animateBarTo(bar, targetPx, 90L + i * 60L);

            // Tap to focus that day
            column.setOnClickListener(v -> {
                String label = (idx == 6 ? "Today" : DAY_FULL[dayOfWeek[idx]])
                        + " · " + formatHm(minutesForDay);
                binding.tvFocusedDay.setText(label);
            });

            barsContainer.addView(column);

            // Day label
            TextView dayLabel = new TextView(requireContext());
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelLp.setMarginStart(i == 0 ? 0 : dp(4));
            dayLabel.setLayoutParams(labelLp);
            dayLabel.setGravity(Gravity.CENTER);
            dayLabel.setText(DAY_INITIALS[dayOfWeek[i]]);
            dayLabel.setTextColor(getResources().getColor(
                    i == 6 ? R.color.purple_primary : R.color.text_secondary, null));
            dayLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            if (i == 6) {
                dayLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            labelsContainer.addView(dayLabel);
        }

        // Default focused-day text: today's live usage
        binding.tvFocusedDay.setText("Today · " + formatHm(minutesPerDay[6]));
    }

    /** Today's live total across every limited app, including in-progress usage. */
    private long liveTodayTotalMinutes() {
        Map<String, Long> allUsage = UsageStatsHelper.getAllAppsUsageToday(requireContext());
        List<AppLimit> limits = viewModel.getAppLimits().getValue();
        if (limits == null || limits.isEmpty()) {
            // Fall back to a single sum so the bar isn't empty before limits load
            long total = 0;
            for (long v : allUsage.values()) total += v;
            return total;
        }
        long total = 0;
        for (AppLimit l : limits) {
            Long m = allUsage.get(l.getPackageName());
            if (m != null) total += m;
        }
        return total;
    }

    private void animateBarTo(View bar, int targetHeightPx, long startDelayMs) {
        ValueAnimator anim = ValueAnimator.ofInt(0, targetHeightPx);
        anim.setDuration(550);
        anim.setStartDelay(startDelayMs);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.height = (int) a.getAnimatedValue();
            bar.setLayoutParams(lp);
        });
        anim.start();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String formatHm(long minutes) {
        if (minutes <= 0) return "0m";
        long h = minutes / 60;
        long m = minutes % 60;
        if (h == 0) return m + "m";
        if (m == 0) return h + "h";
        return h + "h " + m + "m";
    }

    private void setupRecyclerView() {
        adapter = new AppLimitAdapter(this::onEditApp);
        binding.rvAppLimits.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAppLimits.setAdapter(adapter);
        binding.rvAppLimits.setNestedScrollingEnabled(false);
    }

    private void observeViewModel() {
        viewModel.getAppLimits().observe(getViewLifecycleOwner(), limits -> {
            binding.swipeRefresh.setRefreshing(false);

            boolean isEmpty = limits == null || limits.isEmpty();
            binding.rvAppLimits.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

            if (!isEmpty) {
                adapter.setItems(limits);
                updateSummaryCard(limits);
            } else {
                binding.tvTotalTime.setText("0 hrs 0 min");
                binding.tvTrackingCount.setText("Tracking 0 apps");
            }
            // Refresh the weekly chart once limits have loaded so today's
            // live total includes the just-loaded apps.
            renderWeeklyChart();
        });
    }

    private void updateSummaryCard(List<AppLimit> limits) {
        long totalMinutes = 0;
        for (AppLimit limit : limits) {
            totalMinutes += viewModel.getTodayUsageMinutes(limit.getPackageName());
        }
        long hours = totalMinutes / 60;
        long mins  = totalMinutes % 60;
        binding.tvTotalTime.setText(hours + " hrs " + mins + " min");
        binding.tvTrackingCount.setText(
                "Tracking " + limits.size() + " app" + (limits.size() == 1 ? "" : "s"));
    }

    private void onEditApp(AppLimit limit) {
        Intent intent = new Intent(requireContext(), AddAppLimitActivity.class);
        intent.putExtra(AddAppLimitActivity.EXTRA_EDIT_PACKAGE,    limit.getPackageName());
        intent.putExtra(AddAppLimitActivity.EXTRA_EDIT_APP_NAME,   limit.getAppName());
        intent.putExtra(AddAppLimitActivity.EXTRA_CURRENT_LIMIT,   limit.getDailyLimitMinutes());
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
