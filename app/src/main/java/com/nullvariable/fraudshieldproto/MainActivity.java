package com.nullvariable.fraudshieldproto;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView status;
    private TextView logView;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            status.setText("Accessibility service: " + (isEnabled() ? "ENABLED" : "NOT enabled")
                    + "\nAndroid " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")"
                    + "\n\n" + ProtectService.stateLine);
            logView.setText(ProtectService.dumpLog());
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences prefs = getSharedPreferences("proto", MODE_PRIVATE);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, p);
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("FraudShield Proto");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("Rule: GPay, PhonePe or Paytm in front + a live call = 15-second warning, "
                + "shown right away on whatever screen is open. The service reads only the "
                + "front app's package name and never any text on screen.");
        info.setPadding(0, dp(6), 0, dp(10));
        root.addView(info);

        status = new TextView(this);
        status.setTypeface(Typeface.MONOSPACE);
        status.setPadding(0, 0, 0, dp(10));
        root.addView(status);

        addButton(root, "1. Open Accessibility settings", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addButton(root, "If the toggle is greyed out: App info (Allow restricted settings)", v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));

        Switch protect = new Switch(this);
        protect.setText("Protection on");
        protect.setChecked(prefs.getBoolean("protect_on", true));
        protect.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean("protect_on", checked).apply());
        protect.setPadding(0, dp(10), 0, dp(6));
        root.addView(protect);

        Switch sim = new Switch(this);
        sim.setText("Pretend a call is active (for testing without calling)");
        sim.setChecked(prefs.getBoolean("sim_call", false));
        sim.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean("sim_call", checked).apply());
        sim.setPadding(0, dp(6), 0, dp(10));
        root.addView(sim);

        addButton(root, "Test warning now", v -> {
            if (ProtectService.instance == null) {
                Toast.makeText(this, "Enable the accessibility service first", Toast.LENGTH_SHORT).show();
            } else {
                ProtectService.instance.showWarning(true);
            }
        });
        addButton(root, "Hide warning", v -> {
            if (ProtectService.instance != null) {
                ProtectService.instance.hideWarning("hidden from app");
            }
        });
        addButton(root, "Copy log", v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("proto log", ProtectService.dumpLog()));
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
        });
        addButton(root, "Clear log", v -> {
            ProtectService.clearLog();
            logView.setText("");
        });

        TextView logTitle = new TextView(this);
        logTitle.setText("Log (newest first)");
        logTitle.setTypeface(Typeface.DEFAULT_BOLD);
        logTitle.setPadding(0, dp(12), 0, dp(4));
        root.addView(logTitle);

        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(11);
        logView.setTextIsSelectable(true);
        root.addView(logView);

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(refresh);
    }

    private void addButton(LinearLayout parent, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        parent.addView(b);
    }

    private boolean isEnabled() {
        String s = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) {
            return false;
        }
        ComponentName cn = new ComponentName(this, ProtectService.class);
        return s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString());
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
