package com.example.trustlock.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Singleton client for Supabase Realtime over a single multiplexed WebSocket.
 *
 * Supabase Realtime speaks the Phoenix Channels protocol:
 *   - One WebSocket carries many topics ("channels"), each subscribed via phx_join
 *   - A heartbeat envelope must be sent every 30s or the server closes us
 *   - postgres_changes events arrive on the topic that joined them
 *
 * Usage:
 *   RealtimeManager.init("https://abc.supabase.co", BuildConfig.SUPABASE_ANON_KEY);
 *   RealtimeManager rt = RealtimeManager.getInstance();
 *   rt.setAccessToken(signedInJwt);
 *
 *   Subscription sub = rt.subscribe(
 *       "app_limits",
 *       "user_uid=eq." + myUid,
 *       (eventType, newRow, oldRow) -> {
 *           // eventType is "INSERT" / "UPDATE" / "DELETE"
 *           // newRow is the row after the change (null for DELETE)
 *           // oldRow is the row before (null for INSERT)
 *       });
 *
 *   // later:
 *   rt.unsubscribe(sub);
 *
 * Threading: listener callbacks are dispatched on the main looper so they are
 * safe to use for UI updates without extra plumbing.
 */
public class RealtimeManager {

    private static final String TAG = "RealtimeManager";

    private static final long HEARTBEAT_INTERVAL_MS    = 30_000L;
    private static final long INITIAL_RECONNECT_DELAY  = 1_000L;
    private static final long MAX_RECONNECT_DELAY      = 30_000L;

    public interface OnChangeListener {
        void onChange(@NonNull String eventType,
                      @Nullable JsonObject newRow,
                      @Nullable JsonObject oldRow);
    }

    /** Handle returned by {@link #subscribe} — pass back to {@link #unsubscribe} to detach. */
    public static class Subscription {
        final String           id;
        final String           topic;
        final String           joinRef;
        final String           table;
        final String           filter;
        final OnChangeListener listener;
        volatile boolean       joined;

        Subscription(String id, String topic, String joinRef, String table,
                     String filter, OnChangeListener listener) {
            this.id       = id;
            this.topic    = topic;
            this.joinRef  = joinRef;
            this.table    = table;
            this.filter   = filter;
            this.listener = listener;
        }
    }

    // ─── Singleton plumbing ──────────────────────────────────────────────────

    private static volatile RealtimeManager instance;

    public static synchronized void init(@NonNull String supabaseUrl,
                                         @NonNull String anonKey) {
        if (instance == null) {
            instance = new RealtimeManager(supabaseUrl, anonKey);
        }
    }

    @NonNull
    public static RealtimeManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "RealtimeManager.init(url, anonKey) must be called first");
        }
        return instance;
    }

    // ─── State ───────────────────────────────────────────────────────────────

    /** Host portion (no scheme) — e.g. "abc.supabase.co". */
    private final String             host;
    private final String             anonKey;
    private volatile String          accessToken;

    private final OkHttpClient       client;
    private final Gson               gson           = new Gson();
    private final Handler            mainHandler    = new Handler(Looper.getMainLooper());
    private final Handler            controlHandler = new Handler(Looper.getMainLooper());

    private final Map<String, Subscription> subscriptions  = new ConcurrentHashMap<>();
    private final AtomicLong               refCounter     = new AtomicLong(1);

    private volatile WebSocket webSocket;
    private volatile boolean   intentionalDisconnect = false;
    private int                reconnectAttempt      = 0;

    private RealtimeManager(@NonNull String supabaseUrl, @NonNull String anonKey) {
        this.host    = supabaseUrl.replaceFirst("^https?://", "").replaceFirst("/$", "");
        this.anonKey = anonKey;
        this.client  = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Push the user's auth JWT into the active socket so RLS policies that
     * reference {@code auth.uid()} resolve correctly. Call after sign-in and
     * on every token refresh.
     */
    public void setAccessToken(@Nullable String token) {
        this.accessToken = token;
        if (webSocket != null && token != null) {
            for (Subscription sub : subscriptions.values()) {
                if (sub.joined) sendAccessTokenUpdate(sub.topic, token);
            }
        }
    }

    /**
     * Subscribe to row-level changes on a table. {@code filter} uses PostgREST
     * filter syntax (e.g. "user_uid=eq.abc-123"). Pass null for no filter.
     */
    @NonNull
    public Subscription subscribe(@NonNull String table,
                                  @Nullable String filter,
                                  @NonNull OnChangeListener listener) {
        String id      = UUID.randomUUID().toString();
        String topic   = "realtime:" + table + ":" + id.substring(0, 8);
        String joinRef = String.valueOf(refCounter.getAndIncrement());

        Subscription sub = new Subscription(id, topic, joinRef, table, filter, listener);
        subscriptions.put(id, sub);

        ensureConnected();
        if (webSocket != null) {
            joinChannel(sub);
        }
        return sub;
    }

    /** Detach a subscription. Closes the underlying socket when no subs remain. */
    public void unsubscribe(@NonNull Subscription sub) {
        subscriptions.remove(sub.id);
        if (webSocket != null && sub.joined) {
            sendLeave(sub);
        }
        if (subscriptions.isEmpty()) {
            disconnect();
        }
    }

    public void disconnect() {
        intentionalDisconnect = true;
        controlHandler.removeCallbacks(heartbeatRunnable);
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) ws.close(1000, "client_disconnect");
    }

    // ─── Connection management ───────────────────────────────────────────────

    private synchronized void ensureConnected() {
        if (webSocket != null) return;
        intentionalDisconnect = false;
        String url = "wss://" + host + "/realtime/v1/websocket?apikey="
                + anonKey + "&vsn=1.0.0";
        Request req = new Request.Builder().url(url).build();
        webSocket = client.newWebSocket(req, wsListener);
    }

    private void scheduleReconnect() {
        if (intentionalDisconnect)   return;
        if (subscriptions.isEmpty()) return;

        long delay = Math.min(MAX_RECONNECT_DELAY,
                INITIAL_RECONNECT_DELAY * (1L << Math.min(reconnectAttempt, 5)));
        reconnectAttempt++;
        Log.i(TAG, "reconnecting in " + delay + "ms (attempt " + reconnectAttempt + ")");
        controlHandler.postDelayed(this::ensureConnected, delay);
    }

    // ─── Outgoing envelopes ──────────────────────────────────────────────────

    private void joinChannel(@NonNull Subscription sub) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("event",  "*");          // INSERT, UPDATE, DELETE
        changes.put("schema", "public");
        changes.put("table",  sub.table);
        if (sub.filter != null && !sub.filter.isEmpty()) {
            changes.put("filter", sub.filter);
        }

        Map<String, Object> bc = new HashMap<>();
        bc.put("self", false);
        bc.put("ack",  false);

        Map<String, Object> presence = new HashMap<>();
        presence.put("key", "");

        List<Object> pgChanges = new ArrayList<>();
        pgChanges.add(changes);

        Map<String, Object> config = new HashMap<>();
        config.put("broadcast",         bc);
        config.put("presence",          presence);
        config.put("postgres_changes",  pgChanges);

        Map<String, Object> payload = new HashMap<>();
        payload.put("config", config);
        if (accessToken != null) payload.put("access_token", accessToken);

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("topic",     sub.topic);
        envelope.put("event",     "phx_join");
        envelope.put("payload",   payload);
        envelope.put("ref",       sub.joinRef);
        envelope.put("join_ref",  sub.joinRef);

        send(gson.toJson(envelope));
    }

    private void sendLeave(@NonNull Subscription sub) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("topic",     sub.topic);
        envelope.put("event",     "phx_leave");
        envelope.put("payload",   new HashMap<>());
        envelope.put("ref",       String.valueOf(refCounter.getAndIncrement()));
        envelope.put("join_ref",  sub.joinRef);
        send(gson.toJson(envelope));
        sub.joined = false;
    }

    private void sendAccessTokenUpdate(@NonNull String topic, @NonNull String token) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("access_token", token);
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("topic",   topic);
        envelope.put("event",   "access_token");
        envelope.put("payload", payload);
        envelope.put("ref",     String.valueOf(refCounter.getAndIncrement()));
        send(gson.toJson(envelope));
    }

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override public void run() {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("topic",   "phoenix");
            envelope.put("event",   "heartbeat");
            envelope.put("payload", new HashMap<>());
            envelope.put("ref",     String.valueOf(refCounter.getAndIncrement()));
            send(gson.toJson(envelope));
            controlHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    private void send(@NonNull String text) {
        WebSocket ws = webSocket;
        if (ws == null) return;
        try {
            ws.send(text);
        } catch (Exception e) {
            Log.w(TAG, "send failed", e);
        }
    }

    // ─── Incoming dispatch ───────────────────────────────────────────────────

    private final WebSocketListener wsListener = new WebSocketListener() {
        @Override
        public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
            Log.i(TAG, "WebSocket connected");
            reconnectAttempt = 0;
            controlHandler.removeCallbacks(heartbeatRunnable);
            controlHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
            // (Re)join every active channel — this covers both fresh subscribes
            // and recovery after a reconnect.
            for (Subscription sub : subscriptions.values()) {
                sub.joined = false;
                joinChannel(sub);
            }
        }

        @Override
        public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
            try {
                JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
                handleIncoming(obj);
            } catch (Exception e) {
                Log.w(TAG, "parse error: " + text, e);
            }
        }

        @Override
        public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
            Log.i(TAG, "WebSocket closed: " + code + " " + reason);
            webSocket = null;
            controlHandler.removeCallbacks(heartbeatRunnable);
            scheduleReconnect();
        }

        @Override
        public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t,
                              @Nullable Response response) {
            Log.w(TAG, "WebSocket failure", t);
            webSocket = null;
            controlHandler.removeCallbacks(heartbeatRunnable);
            scheduleReconnect();
        }
    };

    private void handleIncoming(@NonNull JsonObject obj) {
        String event = obj.has("event") ? obj.get("event").getAsString() : "";
        String topic = obj.has("topic") ? obj.get("topic").getAsString() : "";

        switch (event) {
            case "phx_reply":
                handlePhxReply(topic, obj);
                break;
            case "postgres_changes":
                handlePostgresChange(topic, obj);
                break;
            case "phx_error":
            case "phx_close":
                Log.w(TAG, "channel " + event + " for " + topic + ": " + obj);
                // Mark sub as not-joined; reconnect cycle will rejoin
                for (Subscription sub : subscriptions.values()) {
                    if (sub.topic.equals(topic)) sub.joined = false;
                }
                break;
            default:
                // heartbeat replies and unknown events — ignore
                break;
        }
    }

    private void handlePhxReply(String topic, @NonNull JsonObject obj) {
        JsonObject payload = obj.has("payload") && obj.get("payload").isJsonObject()
                ? obj.getAsJsonObject("payload") : null;
        if (payload == null) return;
        String status = payload.has("status") ? payload.get("status").getAsString() : "";
        if (!"ok".equals(status)) {
            Log.w(TAG, "phx_reply error for " + topic + ": " + obj);
            return;
        }
        for (Subscription sub : subscriptions.values()) {
            if (sub.topic.equals(topic)) {
                sub.joined = true;
                Log.d(TAG, "joined channel " + topic);
                break;
            }
        }
    }

    private void handlePostgresChange(String topic, @NonNull JsonObject obj) {
        JsonObject payload = obj.has("payload") && obj.get("payload").isJsonObject()
                ? obj.getAsJsonObject("payload") : null;
        if (payload == null) return;
        JsonObject data = payload.has("data") && payload.get("data").isJsonObject()
                ? payload.getAsJsonObject("data") : null;
        if (data == null) return;

        // The Supabase server has changed key names between protocol revisions —
        // accept both "type"/"eventType" and "record"/"new", "old_record"/"old".
        String eventType = "";
        if (data.has("type") && data.get("type").isJsonPrimitive()) {
            eventType = data.get("type").getAsString();
        } else if (data.has("eventType") && data.get("eventType").isJsonPrimitive()) {
            eventType = data.get("eventType").getAsString();
        }

        JsonObject newRow = pickObject(data, "record", "new");
        JsonObject oldRow = pickObject(data, "old_record", "old");

        for (Subscription sub : subscriptions.values()) {
            if (sub.topic.equals(topic)) {
                final String et = eventType;
                final JsonObject n = newRow;
                final JsonObject o = oldRow;
                mainHandler.post(() -> {
                    try {
                        sub.listener.onChange(et, n, o);
                    } catch (Throwable t) {
                        Log.e(TAG, "listener threw", t);
                    }
                });
                break;
            }
        }
    }

    @Nullable
    private static JsonObject pickObject(@NonNull JsonObject parent,
                                         @NonNull String primary,
                                         @NonNull String fallback) {
        if (parent.has(primary) && parent.get(primary).isJsonObject()) {
            return parent.getAsJsonObject(primary);
        }
        if (parent.has(fallback) && parent.get(fallback).isJsonObject()) {
            return parent.getAsJsonObject(fallback);
        }
        return null;
    }
}
