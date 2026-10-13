package com.nullvariable.fraudshieldproto;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * FraudShield prototype service.
 *
 * Signals: (1) which app is in front (package name only), (2) a live call, detected from the
 * system audio mode (cellular and WhatsApp/Telegram-style calls, no permission needed).
 * Rule: payment app in front AND call active -> show the 15-second warning right away,
 * on whatever screen is showing (not only on screen changes).
 *
 * Privacy: this service never reads or logs any text on screen.
 */
public class ProtectService extends AccessibilityService {

    static ProtectService instance;
    static volatile String stateLine = "Service not running";

    private static final ArrayList<String> LOG = new ArrayList<>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    static synchronized void log(String line) {
        LOG.add(FMT.format(new Date()) + "  " + line);
        while (LOG.size() > 300) {
            LOG.remove(0);
        }
    }

    static synchronized String dumpLog() {
        StringBuilder sb = new StringBuilder();
        for (int i = LOG.size() - 1; i >= 0; i--) {
            sb.append(LOG.get(i)).append("\n");
        }
        return sb.toString();
    }

    static synchronized void clearLog() {
        LOG.clear();
    }

    private static final int WARNING_SECONDS = 15;
    private static final long COOLDOWN_MS = 30000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private AudioManager audio;
    private LinearLayout overlay;
    private TextView countdownView;
    private Button okButton;

    private String foreground = "";
    private boolean callActive;
    private boolean manualTest;
    private int secondsLeft;
    private long cooldownUntil;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            refreshState();
            evaluate();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            secondsLeft--;
            if (secondsLeft > 0) {
                countdownView.setText(String.valueOf(secondsLeft));
                okButton.setText("Wait " + secondsLeft + " s");
                handler.postDelayed(this, 1000);
            } else {
                countdownView.setText("0");
                okButton.setEnabled(true);
                okButton.setText("I understand, continue");
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        log("service connected");
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e != null && e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            refreshState();
            evaluate();
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("proto", MODE_PRIVATE);
    }

    /** Reads only the package name of the front window and the audio mode. No screen text. */
    private void refreshState() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && root.getPackageName() != null) {
            String p = root.getPackageName().toString();
            if (!p.equals("com.android.systemui") && !p.equals(foreground)) {
                foreground = p;
                log("foreground -> " + label(p));
            }
        }

        int mode = audio.getMode();
        boolean real = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION;
        boolean sim = prefs().getBoolean("sim_call", false);
        boolean now = real || sim;
        if (now != callActive) {
            callActive = now;
            log("call " + (now ? "ACTIVE" : "idle") + " (audio mode " + mode + (sim ? ", simulated" : "") + ")");
        }

        stateLine = "Foreground app: " + label(foreground)
                + "\nCall: " + (callActive ? "ACTIVE" : "idle") + "  (audio mode " + mode + ")"
                + "\nProtection: " + (prefs().getBoolean("protect_on", true) ? "on" : "off")
                + (overlay != null ? "   |  WARNING SHOWING" : "");
    }

    private void evaluate() {
        boolean target = isTarget(foreground);
        if (overlay != null) {
            if (!target && !manualTest) {
                hideWarning("left the payment app");
            }
            return;
        }
        if (!prefs().getBoolean("protect_on", true) || !target || !callActive) {
            return;
        }
        if (SystemClock.uptimeMillis() < cooldownUntil) {
            return;
        }
        log("RULE MET: payment app in front + live call");
        showWarning(false);
    }

    void showWarning(boolean manual) {
        if (overlay != null || wm == null) {
            return;
        }
        manualTest = manual;
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (28 * d);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(Color.argb(245, 214, 69, 69));
        overlay.setPadding(pad, pad, pad, pad);
        overlay.setClickable(true); // swallow touches everywhere except the button

        TextView tag = new TextView(this);
        tag.setText("FRAUDSHIELD ALERT");
        tag.setTextColor(Color.WHITE);
        tag.setTextSize(14);
        tag.setLetterSpacing(0.15f);
        tag.setTypeface(Typeface.DEFAULT_BOLD);
        tag.setGravity(Gravity.CENTER);
        overlay.addView(tag);

        TextView title = new TextView(this);
        title.setText("Pause. This payment looks risky.");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, (int) (14 * d), 0, (int) (14 * d));
        overlay.addView(title);

        StringBuilder reasons = new StringBuilder();
        if (manual) {
            reasons.append("\u2022 Manual test from the app");
        } else {
            if (callActive) {
                reasons.append("\u2022 Live call in progress\n");
            }
            reasons.append("\u2022 Payment app open: ").append(label(foreground));
        }
        TextView why = new TextView(this);
        why.setText(reasons.toString());
        why.setTextColor(Color.WHITE);
        why.setTextSize(18);
        why.setGravity(Gravity.CENTER);
        overlay.addView(why);

        countdownView = new TextView(this);
        countdownView.setText(String.valueOf(WARNING_SECONDS));
        countdownView.setTextColor(Color.WHITE);
        countdownView.setTextSize(72);
        countdownView.setTypeface(Typeface.DEFAULT_BOLD);
        countdownView.setGravity(Gravity.CENTER);
        countdownView.setPadding(0, (int) (18 * d), 0, (int) (18 * d));
        overlay.addView(countdownView);

        TextView tip = new TextView(this);
        tip.setText("No bank or officer will ask you to pay urgently over a call.");
        tip.setTextColor(Color.WHITE);
        tip.setTextSize(15);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, 0, 0, (int) (18 * d));
        overlay.addView(tip);

        okButton = new Button(this);
        okButton.setAllCaps(false);
        okButton.setEnabled(false);
        okButton.setText("Wait " + WARNING_SECONDS + " s");
        okButton.setOnClickListener(v -> {
            hideWarning("dismissed by user");
            cooldownUntil = SystemClock.uptimeMillis() + COOLDOWN_MS;
        });
        overlay.addView(okButton);

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);

        try {
            wm.addView(overlay, lp);
            log("WARNING SHOWN over " + label(foreground) + (manual ? " (manual test)" : ""));
        } catch (Exception ex) {
            log("WARNING FAILED: " + ex);
            overlay = null;
            return;
        }
        secondsLeft = WARNING_SECONDS;
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 1000);
    }

    void hideWarning(String why) {
        handler.removeCallbacks(ticker);
        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (Exception ignored) {
                // already removed
            }
            overlay = null;
            manualTest = false;
            log("warning closed: " + why);
        }
    }

    private static boolean isTarget(String p) {
        return p.startsWith("com.google.android.apps.nbu.paisa.user")
                || p.startsWith("com.phonepe.app")
                || p.startsWith("net.one97.paytm");
    }

    private String label(String p) {
        if (p == null || p.isEmpty()) return "(none yet)";
        if (p.startsWith("com.google.android.apps.nbu.paisa.user")) return "GPay";
        if (p.startsWith("com.phonepe.app")) return "PhonePe";
        if (p.startsWith("net.one97.paytm")) return "Paytm";
        if (p.equals(getPackageName())) return "FraudShield Proto (this app)";
        return p;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        handler.removeCallbacks(poll);
        hideWarning("service stopped");
        instance = null;
        stateLine = "Service not running";
        return super.onUnbind(intent);
    }
}
