package com.example.trustlock.ui.guardian;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.trustlock.data.RealtimeManager;
import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.data.SupabaseDbApi;
import com.example.trustlock.databinding.FragmentGuardianHomeBinding;
import com.example.trustlock.models.CurrentApp;
import com.example.trustlock.models.UsageLog;
import com.example.trustlock.util.GuardianContext;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Guardian home: live "right now" app, today's total, and a horizontal bar
 * chart of the top 5 apps for the day. Data sources:
 *   - current_app (singleton row, Realtime-subscribed)
 *   - usage_logs  (one row per app per day, polled on resume + swipe refresh)
 */
public class GuardianHomeFragment extends Fragment {

    private static final String TAG = "GuardianHomeFragment";

    private FragmentGuardianHomeBinding binding;

    private RealtimeManager.Subscription currentAppSub;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGuardianHomeBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupChart();

        binding.swipeRefresh.setColorSchemeResources(
                com.example.trustlock.R.color.purple_primary);
        binding.swipeRefresh.setOnRefreshListener(this::refresh);

        GuardianContext.getInstance().monitoredUserName.observe(
                getViewLifecycleOwner(),
                name -> binding.tvMonitoredName.setText(
                        name != null ? name : "—"));

        GuardianContext.getInstance().monitoredUserUid.observe(
                getViewLifecycleOwner(),
                uid -> {
                    unsubscribeCurrent();
                    if (uid == null) {
                        binding.emptyState.setVisibility(View.VISIBLE);
                        return;
                    }
                    binding.emptyState.setVisibility(View.GONE);
                    refresh();
                    subscribeCurrentApp(uid);
                });
    }

    private void refresh() {
        String userUid = GuardianContext.getInstance().getMonitoredUserUidValue();
        if (userUid == null) {
            binding.swipeRefresh.setRefreshing(false);
            return;
        }
        binding.swipeRefresh.setRefreshing(true);
        fetchCurrentApp(userUid);
        fetchTodayUsage(userUid);
    }

    private void fetchCurrentApp(String userUid) {
        SupabaseDbApi db = SupabaseClient.getInstance().db();
        db.getCurrentApp("eq." + userUid).enqueue(new Callback<List<CurrentApp>>() {
            @Override public void onResponse(Call<List<CurrentApp>> call,
                                             Response<List<CurrentApp>> r) {
                if (binding == null) return;
                if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                    renderCurrentApp(r.body().get(0));
                } else {
                    renderCurrentApp(null);
                }
            }
            @Override public void onFailure(Call<List<CurrentApp>> call, Throwable t) {
                Log.w(TAG, "fetchCurrentApp failed", t);
                if (binding != null) renderCurrentApp(null);
            }
        });
    }

    private void renderCurrentApp(@Nullable CurrentApp app) {
        if (binding == null) return;
        if (app == null || app.getAppName() == null) {
            binding.tvCurrentApp.setText("Idle");
            binding.tvCurrentAppFor.setText("No active app reported");
            binding.liveDot.setAlpha(0.3f);
            return;
        }
        binding.tvCurrentApp.setText(app.getAppName());
        binding.tvCurrentAppFor.setText(app.getPackageName());
        binding.liveDot.setAlpha(1f);
    }

    private void fetchTodayUsage(String userUid) {
        String today = today();
        SupabaseClient.getInstance().db()
                .getUsageLogs("eq." + userUid, "eq." + today, "minutes_used.desc")
                .enqueue(new Callback<List<UsageLog>>() {
                    @Override public void onResponse(Call<List<UsageLog>> call,
                                                     Response<List<UsageLog>> r) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        renderUsage(r.isSuccessful() ? r.body() : null);
                    }
                    @Override public void onFailure(Call<List<UsageLog>> call, Throwable t) {
                        Log.w(TAG, "fetchTodayUsage failed", t);
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        renderUsage(null);
                    }
                });
    }

    private void renderUsage(@Nullable List<UsageLog> logs) {
        if (binding == null) return;
        int total = 0, count = 0;
        if (logs != null) {
            for (UsageLog l : logs) {
                total += l.getMinutesUsed();
                count++;
            }
        }
        binding.tvTodayTotal.setText(formatTotal(total));
        binding.tvTodayApps.setText(String.valueOf(count));

        if (logs == null || logs.isEmpty()) {
            binding.tvChartEmpty.setVisibility(View.VISIBLE);
            binding.topAppsChart.setVisibility(View.INVISIBLE);
            binding.topAppsChart.clear();
            return;
        }

        binding.tvChartEmpty.setVisibility(View.GONE);
        binding.topAppsChart.setVisibility(View.VISIBLE);

        // Defensive sort — the server should already sort desc, but in case it doesn't.
        List<UsageLog> sorted = new ArrayList<>(logs);
        sorted.sort(Comparator.comparingInt(UsageLog::getMinutesUsed).reversed());
        List<UsageLog> top = sorted.subList(0, Math.min(5, sorted.size()));

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        // MPAndroidChart draws bars from bottom to top — reverse so the
        // longest row appears at the top of the chart.
        for (int i = top.size() - 1; i >= 0; i--) {
            UsageLog l = top.get(i);
            entries.add(new BarEntry(top.size() - 1 - i, l.getMinutesUsed()));
            labels.add(l.getAppName() != null ? l.getAppName() : l.getPackageName());
        }

        BarDataSet ds = new BarDataSet(entries, "Minutes");
        ds.setColor(Color.parseColor("#7C6EFA"));
        ds.setValueTextColor(Color.parseColor("#F0F0FF"));
        ds.setValueTextSize(10f);

        BarData data = new BarData(ds);
        data.setBarWidth(0.6f);

        binding.topAppsChart.setData(data);
        binding.topAppsChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.topAppsChart.getXAxis().setLabelCount(labels.size(), false);
        binding.topAppsChart.invalidate();
    }

    private void setupChart() {
        HorizontalBarChart c = binding.topAppsChart;
        Description d = new Description();
        d.setText("");
        c.setDescription(d);
        c.getLegend().setEnabled(false);
        c.setDrawGridBackground(false);
        c.setDrawValueAboveBar(true);
        c.setNoDataText("");
        c.setExtraOffsets(8, 4, 16, 4);
        c.setTouchEnabled(false);

        int gridColor = Color.parseColor("#252538");
        int textColor = Color.parseColor("#8080A8");

        XAxis xAxis = c.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(textColor);
        xAxis.setTextSize(10f);
        xAxis.setGranularity(1f);

        YAxis left = c.getAxisLeft();
        left.setDrawAxisLine(false);
        left.setGridColor(gridColor);
        left.setTextColor(textColor);
        left.setAxisMinimum(0f);

        YAxis right = c.getAxisRight();
        right.setEnabled(false);
    }

    private void subscribeCurrentApp(String userUid) {
        try {
            currentAppSub = RealtimeManager.getInstance().subscribe(
                    "current_app",
                    "user_uid=eq." + userUid,
                    (eventType, newRecord, oldRecord) -> {
                        if (binding == null) return;
                        binding.getRoot().post(() -> fetchCurrentApp(userUid));
                    });
        } catch (IllegalStateException e) {
            Log.w(TAG, "RealtimeManager not initialized", e);
        }
    }

    private void unsubscribeCurrent() {
        if (currentAppSub != null) {
            try { RealtimeManager.getInstance().unsubscribe(currentAppSub); }
            catch (Exception ignored) {}
            currentAppSub = null;
        }
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Calendar.getInstance().getTime());
    }

    private static String formatTotal(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return h + " hrs " + m + " min";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unsubscribeCurrent();
        binding = null;
    }
}
