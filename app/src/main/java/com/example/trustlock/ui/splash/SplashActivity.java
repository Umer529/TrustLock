package com.example.trustlock.ui.splash;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trustlock.MainActivity;
import com.example.trustlock.R;
import com.example.trustlock.ui.welcome.WelcomeActivity;
import com.example.trustlock.util.SessionManager;

/**
 * Animated splash. Plays a logo+text+pills reveal sequence, then routes to
 * MainActivity if the user is signed in, or WelcomeActivity if not.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long TOTAL_VISIBLE_MS = 3000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView   logo     = findViewById(R.id.ivLogo);
        TextView    prefix   = findViewById(R.id.tvBrandPrefix);
        TextView    suffix   = findViewById(R.id.tvBrandSuffix);
        TextView    tagline  = findViewById(R.id.tvTagline);
        View        divider  = findViewById(R.id.divider);
        View        pill1    = findViewById(R.id.pill1);
        View        pill2    = findViewById(R.id.pill2);
        View        pill3    = findViewById(R.id.pill3);
        ProgressBar progress = findViewById(R.id.progress);

        // ── Logo: fade + scale up with a slight overshoot ──────────────────
        logo.setScaleX(0.6f);
        logo.setScaleY(0.6f);
        AnimatorSet logoIn = new AnimatorSet();
        logoIn.playTogether(
                ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.6f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.6f, 1f));
        logoIn.setDuration(700);
        logoIn.setInterpolator(new OvershootInterpolator(1.2f));

        // ── "Screen": slide in from the left + fade ────────────────────────
        AnimatorSet prefixIn = new AnimatorSet();
        prefixIn.playTogether(
                ObjectAnimator.ofFloat(prefix, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(prefix, View.TRANSLATION_X, -40f, 0f));
        prefixIn.setDuration(450);
        prefixIn.setStartDelay(500);
        prefixIn.setInterpolator(new DecelerateInterpolator());

        // ── "Pact": slide in from the right + fade ─────────────────────────
        AnimatorSet suffixIn = new AnimatorSet();
        suffixIn.playTogether(
                ObjectAnimator.ofFloat(suffix, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(suffix, View.TRANSLATION_X, 40f, 0f));
        suffixIn.setDuration(450);
        suffixIn.setStartDelay(700);
        suffixIn.setInterpolator(new DecelerateInterpolator());

        // ── Tagline + divider: gentle fade in ──────────────────────────────
        ObjectAnimator taglineIn = ObjectAnimator.ofFloat(tagline, View.ALPHA, 0f, 1f);
        taglineIn.setDuration(500);
        taglineIn.setStartDelay(1100);

        ObjectAnimator dividerIn = ObjectAnimator.ofFloat(divider, View.ALPHA, 0f, 1f);
        dividerIn.setDuration(400);
        dividerIn.setStartDelay(1350);

        // ── Pills: cascade in from below, one after another ───────────────
        AnimatorSet pillsIn = new AnimatorSet();
        pillsIn.playTogether(
                pillAnimator(pill1, 1500),
                pillAnimator(pill2, 1650),
                pillAnimator(pill3, 1800));

        // ── Progress spinner: fade in last ─────────────────────────────────
        ObjectAnimator progressIn = ObjectAnimator.ofFloat(progress, View.ALPHA, 0f, 1f);
        progressIn.setDuration(400);
        progressIn.setStartDelay(2100);

        AnimatorSet master = new AnimatorSet();
        master.playTogether(logoIn, prefixIn, suffixIn, taglineIn, dividerIn, pillsIn, progressIn);
        master.start();

        new Handler(Looper.getMainLooper()).postDelayed(this::routeNext, TOTAL_VISIBLE_MS);
    }

    /** A single pill's reveal: fade in + slide up from 20dp below. */
    private AnimatorSet pillAnimator(View pill, long startDelayMs) {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(pill, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(pill, View.TRANSLATION_Y, 40f, 0f));
        set.setDuration(450);
        set.setStartDelay(startDelayMs);
        set.setInterpolator(new DecelerateInterpolator());
        return set;
    }

    private void routeNext() {
        Class<?> dest = SessionManager.getInstance().isLoggedIn()
                ? MainActivity.class
                : WelcomeActivity.class;
        startActivity(new Intent(this, dest));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
