package com.example.trustlock.ui.guardian;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.trustlock.data.RealtimeManager;
import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.databinding.FragmentLiveMonitoringBinding;
import com.example.trustlock.models.CurrentApp;
import com.example.trustlock.models.UsageLog;
import com.example.trustlock.util.GuardianContext;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Live foreground app pinned at top with a ticking elapsed-time clock,
 * followed by a RecyclerView of today's per-app usage (descending).
 *
 * Updates come from two sources:
 *   - Realtime subscription on {@code current_app} (push)
 *   - Poll of {@code usage_logs} every {@link #POLL_INTERVAL_MS} (pull)
 *
 * The 1-second ticker only refreshes the elapsed-time label; it does not
 * re-query the network. Network calls happen on the polling cadence + on
 * Realtime push + on SwipeRefresh.
 */
public class LiveMonitoringFragment extends Fragment {

    private static final String TAG = "LiveMonitoringFragment";

    /** Polling interval for usage_logs (covers any Realtime drop). */
    private static final long POLL_INTERVAL_MS  = 30_000L;
    private static final long TICKER_INTERVAL_MS = 1_000L;

    private FragmentLiveMonitoringBinding binding;
    private LiveAppAdapter adapter;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private RealtimeManager.Subscription currentAppSub;

    /** Parsed "since" timestamp for the current foreground app. */
    @Nullable private Long currentSinceMs;
    @Nullable private String currentAppName;
    @Nullable private String currentPackageName;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateTimer();
            if (binding != null) handler.postDelayed(this, TICKER_INTERVAL_MS);
        }
    };

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            String uid = GuardianContext.getInstance().getMonitoredUserUidValue();
            if (uid != null) {
                fetchCurrentApp(uid);
                fetchTodayUsage(uid);
            }
            if (binding != null) handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLiveMonitoringBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new LiveAppAdapter();
        binding.rvApps.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvApps.setAdapter(adapter);

        binding.swipeRefresh.setColorSchemeResources(
                com.example.trustlock.R.color.purple_primary);
        binding.swipeRefresh.setOnRefreshListener(this::refreshNow);

        GuardianContext.getInstance().monitoredUserName.observe(
                getViewLifecycleOwner(),
                name -> binding.tvMonitoredName.setText(
                        name != null ? name : "—"));

        GuardianContext.getInstance().monitoredUserUid.observe(
                getViewLifecycleOwner(),
                uid -> {
                    unsubscribeCurrent();
                    if (uid == null) {
                        binding.tvEmpty.setVisibility(View.VISIBLE);
                        adapter.submit(null);
                        return;
                    }
                    refreshNow();
                    subscribeCurrentApp(uid);
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.postDelayed(ticker, TICKER_INTERVAL_MS);
        handler.postDelayed(poller, POLL_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(poller);
    }

    private void refreshNow() {
        String uid = GuardianContext.getInstance().getMonitoredUserUidValue();
        if (uid == null) {
            if (binding != null) binding.swipeRefresh.setRefreshing(false);
            return;
        }
        if (binding != null) binding.swipeRefresh.setRefreshing(true);
        fetchCurrentApp(uid);
        fetchTodayUsage(uid);
    }

    private void fetchCurrentApp(String userUid) {
        SupabaseClient.getInstance().db()
                .getCurrentApp("eq." + userUid)
                .enqueue(new Callback<List<CurrentApp>>() {
                    @Override public void onResponse(Call<List<CurrentApp>> call,
                                                     Response<List<CurrentApp>> r) {
                        if (binding == null) return;
                        if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                            applyCurrentApp(r.body().get(0));
                        } else {
                            applyCurrentApp(null);
                        }
                    }
                    @Override public void onFailure(Call<List<CurrentApp>> call, Throwable t) {
                        Log.w(TAG, "fetchCurrentApp failed", t);
                        if (binding != null) applyCurrentApp(null);
                    }
                });
    }

    private void applyCurrentApp(@Nullable CurrentApp app) {
        if (binding == null) return;
        if (app == null || app.getAppName() == null) {
            currentSinceMs     = null;
            currentAppName     = null;
            currentPackageName = null;
            binding.tvLiveAppName.setText("Idle");
            binding.tvLiveAppPackage.setText("Not currently in foreground");
            binding.tvLiveTimer.setText("00:00");
            return;
        }
        currentAppName     = app.getAppName();
        currentPackageName = app.getPackageName();
        currentSinceMs     = parseIso8601(app.getSince());

        binding.tvLiveAppName.setText(currentAppName);
        binding.tvLiveAppPackage.setText(currentPackageName);
        updateTimer();
    }

    private void updateTimer() {
        if (binding == null || currentSinceMs == null) return;
        long elapsed = Math.max(0L, System.currentTimeMillis() - currentSinceMs);
        binding.tvLiveTimer.setText(formatElapsed(elapsed));
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
                        List<UsageLog> body = r.isSuccessful() ? r.body() : null;
                        adapter.submit(body);
                        binding.tvEmpty.setVisibility(
                                (body == null || body.isEmpty()) ? View.VISIBLE : View.GONE);
                    }
                    @Override public void onFailure(Call<List<UsageLog>> call, Throwable t) {
                        Log.w(TAG, "fetchTodayUsage failed", t);
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                    }
                });
    }

    private void subscribeCurrentApp(String userUid) {
        try {
            currentAppSub = RealtimeManager.getInstance().subscribe(
                    "current_app",
                    "user_uid=eq." + userUid,
                    (eventType, newRow, oldRow) -> {
                        if (binding == null) return;
                        fetchCurrentApp(userUid);
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

    @Nullable
    private static Long parseIso8601(@Nullable String iso) {
        if (iso == null || iso.isEmpty()) return null;
        // Postgres timestamptz comes through as e.g. "2026-06-15T08:42:13.123456+00:00"
        // or "2026-06-15T08:42:13Z". Strip fractional seconds beyond millis and
        // normalise the timezone offset for SimpleDateFormat.
        String normalised = iso.replace("Z", "+0000")
                               .replaceAll("\\.(\\d{3})\\d+", ".$1")
                               .replaceAll("([+-]\\d{2}):(\\d{2})$", "$1$2");
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssZ"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = f.parse(normalised);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Calendar.getInstance().getTime());
    }

    private static String formatElapsed(long ms) {
        long totalSec = ms / 1000L;
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%02d:%02d", m, s);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(poller);
        unsubscribeCurrent();
        binding = null;
    }
}
