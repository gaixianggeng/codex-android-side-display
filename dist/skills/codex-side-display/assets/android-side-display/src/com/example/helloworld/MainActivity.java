package com.example.helloworld;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String[] BASE_URLS = {
            "http://192.168.31.163:8765",
            "http://127.0.0.1:8765"
    };
    private static final int REFRESH_MS = 2000;

    private static final int BG = Color.rgb(30, 33, 32);
    private static final int PANEL = Color.rgb(29, 31, 30);
    private static final int BORDER = Color.rgb(143, 134, 111);
    private static final int TEXT = Color.rgb(237, 223, 185);
    private static final int MUTED = Color.rgb(150, 143, 121);
    private static final int GREEN = Color.rgb(106, 160, 104);
    private static final int BAR = Color.rgb(232, 215, 166);
    private static final int BAR_DIM = Color.rgb(85, 85, 75);

    private final Handler handler = new Handler();
    private TextView statusValue;
    private TextView weeklyUsageValue;
    private TextView resetTimeValue;
    private TextView usageSourceValue;
    private TextView spotifyValue;
    private TextView artistValue;
    private TextView albumValue;
    private TextView playbackValue;
    private TextView trackProgressValue;
    private TextView codexUsageValue;
    private TextView codexBudgetValue;
    private TextView codexScaleValue;
    private TextView usagePercentValue;
    private TextView topTimeValue;
    private TextView topDateValue;
    private TextView topBatteryValue;
    private BatteryPillView topBatteryIcon;
    private ImageView topSourceIcon;
    private TextView topSourceNameValue;
    private PlaybackButtonView playPauseButton;
    private TextView trackMetaValue;
    private TextView positionValue;
    private TextView durationValue;
    private TextView powerValue;
    private TextView updatedValue;
    private TextView sourceValue;
    private ProgressStripView trackProgressStrip;
    private ProgressStripView bottomProgressStrip;
    private ProgressStripView totalUsageStrip;
    private ProgressStripView hourLimitStrip;
    private ProgressStripView weeklyLimitStrip;
    private TextView hourLimitValue;
    private TextView weeklyLimitValue;
    private UsageBarsView codexUsageBars;
    private boolean stopped;
    private boolean powerReceiverRegistered;
    private VinylView vinylView;
    private Bitmap spotifySourceIcon;
    private Bitmap appleMusicSourceIcon;
    private String lastArtworkKey = "";
    private String activeBaseUrl = BASE_URLS[0];

    private static class LimitStatusRow {
        final ProgressStripView strip;
        final TextView value;

        LimitStatusRow(ProgressStripView strip, TextView value) {
            this.strip = strip;
            this.value = value;
        }
    }

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePowerState(intent);
        }
    };

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!stopped) {
                refreshStatus();
                handler.postDelayed(this, REFRESH_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        enterFullscreen();
        buildLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        stopped = false;
        enterFullscreen();
        registerPowerReceiver();
        handler.removeCallbacks(pollRunnable);
        handler.post(pollRunnable);
    }

    @Override
    protected void onPause() {
        stopped = true;
        handler.removeCallbacks(pollRunnable);
        unregisterPowerReceiver();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterFullscreen();
        }
    }

    private void buildLayout() {
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(7, 8, 8));

        LinearLayout deck = new LinearLayout(this);
        deck.setOrientation(LinearLayout.VERTICAL);
        deck.setPadding(
                portrait ? dp(18) : dp(28),
                portrait ? dp(18) : dp(22),
                portrait ? dp(18) : dp(28),
                portrait ? dp(16) : dp(20));
        deck.setBackgroundColor(PANEL);
        FrameLayout.LayoutParams deckParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(deck, deckParams);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        deck.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)));

        LinearLayout topSourceRow = new LinearLayout(this);
        topSourceRow.setOrientation(LinearLayout.HORIZONTAL);
        topSourceRow.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        topBar.addView(topSourceRow, new LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT));

        topSourceIcon = new ImageView(this);
        topSourceIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        spotifySourceIcon = loadAssetBitmap("spotify_icon.png");
        appleMusicSourceIcon = loadAssetBitmap("apple_music_icon.png");
        if (spotifySourceIcon != null) {
            topSourceIcon.setImageBitmap(spotifySourceIcon);
        }
        LinearLayout.LayoutParams topBadgeParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        topBadgeParams.setMargins(0, 0, dp(8), 0);
        topSourceRow.addView(topSourceIcon, topBadgeParams);

        topSourceNameValue = terminalText("Spotify", TEXT, 13, true);
        topSourceNameValue.setSingleLine(true);
        topSourceRow.addView(topSourceNameValue, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View topSpacer = new View(this);
        topBar.addView(topSpacer, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        LinearLayout topStatusRow = new LinearLayout(this);
        topStatusRow.setOrientation(LinearLayout.HORIZONTAL);
        topStatusRow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        topBar.addView(topStatusRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        topTimeValue = terminalText("--:--", TEXT, 19, false);
        topTimeValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        topStatusRow.addView(topTimeValue, new LinearLayout.LayoutParams(dp(86), LinearLayout.LayoutParams.WRAP_CONTENT));

        addStatusDivider(topStatusRow);

        topDateValue = terminalText(
                new SimpleDateFormat("EEE MM/dd", Locale.US).format(new Date()).toUpperCase(Locale.US),
                MUTED,
                12,
                false);
        topDateValue.setGravity(Gravity.CENTER);
        topStatusRow.addView(topDateValue, new LinearLayout.LayoutParams(dp(104), LinearLayout.LayoutParams.WRAP_CONTENT));

        addStatusDivider(topStatusRow);

        topBatteryIcon = new BatteryPillView(this);
        LinearLayout.LayoutParams batteryIconParams = new LinearLayout.LayoutParams(dp(28), dp(14));
        batteryIconParams.setMargins(0, 0, dp(8), 0);
        topStatusRow.addView(topBatteryIcon, batteryIconParams);

        topBatteryValue = terminalText("--%", TEXT, 12, false);
        topBatteryValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        topStatusRow.addView(topBatteryValue, new LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout mainRow = new LinearLayout(this);
        mainRow.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        mainRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1);
        mainParams.setMargins(0, dp(10), 0, dp(10));
        deck.addView(mainRow, mainParams);

        LinearLayout infoColumn = new LinearLayout(this);
        infoColumn.setOrientation(portrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        infoColumn.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams infoParams = portrait
                ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(300))
                : new LinearLayout.LayoutParams(dp(360), LinearLayout.LayoutParams.MATCH_PARENT);
        if (portrait) {
            infoParams.setMargins(0, dp(16), 0, 0);
        } else {
            infoParams.setMargins(0, 0, dp(24), 0);
        }

        LinearLayout nowPanel = panelBlock();
        LinearLayout.LayoutParams nowParams = portrait
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.86f)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.78f);
        if (portrait) {
            nowParams.setMargins(0, 0, dp(14), 0);
        } else {
            nowParams.setMargins(0, 0, 0, dp(14));
        }
        infoColumn.addView(nowPanel, nowParams);

        LinearLayout nowHeader = new LinearLayout(this);
        nowHeader.setOrientation(LinearLayout.HORIZONTAL);
        nowHeader.setGravity(Gravity.CENTER_VERTICAL);
        nowPanel.addView(nowHeader, wrap());

        playbackValue = terminalText("01  NOW PLAYING", GREEN, 12, true);
        playbackValue.setSingleLine(true);
        nowHeader.addView(playbackValue, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        addSpacer(nowPanel, 18);

        spotifyValue = displayText("Waiting for Spotify", TEXT, 25, false);
        spotifyValue.setGravity(Gravity.LEFT);
        spotifyValue.setMaxLines(2);
        spotifyValue.setEllipsize(TextUtils.TruncateAt.END);
        nowPanel.addView(spotifyValue, wrap());

        artistValue = displayText("-", TEXT, 17, false);
        artistValue.setGravity(Gravity.LEFT);
        artistValue.setMaxLines(1);
        artistValue.setEllipsize(TextUtils.TruncateAt.END);
        nowPanel.addView(artistValue, wrap());

        addSpacer(nowPanel, 14);
        View accent = new View(this);
        accent.setBackgroundColor(GREEN);
        nowPanel.addView(accent, new LinearLayout.LayoutParams(dp(42), dp(1)));
        addSpacer(nowPanel, 14);

        trackMetaValue = terminalText("Track        -\nArtist       -\nAlbum        -\nDuration     -", MUTED, 12, false);
        trackMetaValue.setSingleLine(false);
        nowPanel.addView(trackMetaValue, wrap());

        LinearLayout usagePanel = panelBlock();
        LinearLayout.LayoutParams usageParams = portrait
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        infoColumn.addView(usagePanel, usageParams);

        addPanelHeader(usagePanel, "02  CODEX USAGE", "5.1 - 5.31");
        addSpacer(usagePanel, 22);

        TextView totalLabel = terminalText("Total Tokens", MUTED, 12, false);
        usagePanel.addView(totalLabel, wrap());

        LinearLayout totalRow = new LinearLayout(this);
        totalRow.setOrientation(LinearLayout.HORIZONTAL);
        totalRow.setGravity(Gravity.BOTTOM);
        usagePanel.addView(totalRow, wrap());

        codexUsageValue = terminalText("0", TEXT, 30, false);
        codexUsageValue.setSingleLine(true);
        totalRow.addView(codexUsageValue, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        codexBudgetValue = terminalText("/ local", Color.rgb(80, 77, 68), 17, false);
        codexBudgetValue.setGravity(Gravity.RIGHT);
        totalRow.addView(codexBudgetValue, new LinearLayout.LayoutParams(dp(126), LinearLayout.LayoutParams.WRAP_CONTENT));

        addSpacer(usagePanel, 14);

        LinearLayout totalProgressRow = new LinearLayout(this);
        totalProgressRow.setOrientation(LinearLayout.HORIZONTAL);
        totalProgressRow.setGravity(Gravity.CENTER_VERTICAL);
        usagePanel.addView(totalProgressRow, wrap());

        totalUsageStrip = new ProgressStripView(this);
        totalProgressRow.addView(totalUsageStrip, new LinearLayout.LayoutParams(0, dp(10), 1));

        usagePercentValue = terminalText("--", GREEN, 13, false);
        usagePercentValue.setGravity(Gravity.RIGHT);
        totalProgressRow.addView(usagePercentValue, new LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT));

        addSpacer(usagePanel, 24);

        LimitStatusRow hourLimitRow = addLimitRow(usagePanel, "5 小时", "--");
        hourLimitStrip = hourLimitRow.strip;
        hourLimitValue = hourLimitRow.value;
        LimitStatusRow weeklyLimitRow = addLimitRow(usagePanel, "1 周", "--");
        weeklyLimitStrip = weeklyLimitRow.strip;
        weeklyLimitValue = weeklyLimitRow.value;

        addSpacer(usagePanel, 18);
        updatedValue = terminalText("Updated: -", Color.rgb(80, 77, 68), 11, false);
        updatedValue.setSingleLine(true);
        updatedValue.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        usagePanel.addView(updatedValue, wrap());

        albumValue = new TextView(this);
        trackProgressValue = new TextView(this);
        trackProgressStrip = new ProgressStripView(this);
        codexScaleValue = new TextView(this);
        codexUsageBars = new UsageBarsView(this);
        weeklyUsageValue = new TextView(this);
        resetTimeValue = new TextView(this);

        LinearLayout mediaColumn = new LinearLayout(this);
        mediaColumn.setOrientation(LinearLayout.VERTICAL);
        mediaColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams mediaParams = portrait
                ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1)
                : new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);

        if (portrait) {
            mainRow.addView(mediaColumn, mediaParams);
            mainRow.addView(infoColumn, infoParams);
        } else {
            mainRow.addView(infoColumn, infoParams);
            mainRow.addView(mediaColumn, mediaParams);
        }

        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1);
        mediaColumn.addView(stage, stageParams);

        vinylView = new VinylView(this);
        FrameLayout.LayoutParams vinylParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        stage.addView(vinylView, vinylParams);
        vinylView.setClickable(true);
        vinylView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePlayback();
            }
        });

        LinearLayout mediaControls = new LinearLayout(this);
        mediaControls.setOrientation(LinearLayout.HORIZONTAL);
        mediaControls.setGravity(Gravity.CENTER_VERTICAL);
        mediaControls.setPadding(portrait ? dp(10) : dp(36), dp(8), portrait ? dp(10) : dp(36), 0);
        mediaColumn.addView(mediaControls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)));

        playPauseButton = new PlaybackButtonView(this);
        playPauseButton.setContentDescription("Play or pause");
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePlayback();
            }
        });
        LinearLayout.LayoutParams playButtonParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        playButtonParams.setMargins(0, 0, dp(16), 0);
        mediaControls.addView(playPauseButton, playButtonParams);

        positionValue = terminalText("--:--", TEXT, 14, false);
        mediaControls.addView(positionValue, new LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT));

        bottomProgressStrip = new ProgressStripView(this);
        LinearLayout.LayoutParams bottomProgressParams = new LinearLayout.LayoutParams(0, dp(10), 1);
        bottomProgressParams.setMargins(dp(14), 0, dp(14), 0);
        mediaControls.addView(bottomProgressStrip, bottomProgressParams);

        durationValue = terminalText("--:--", TEXT, 14, false);
        durationValue.setGravity(Gravity.RIGHT);
        mediaControls.addView(durationValue, new LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT));

        statusValue = new TextView(this);
        usageSourceValue = new TextView(this);
        powerValue = new TextView(this);

        setContentView(root);
    }

    private void registerPowerReceiver() {
        if (powerReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent stickyIntent = registerReceiver(powerReceiver, filter);
        powerReceiverRegistered = true;
        if (stickyIntent != null) {
            updatePowerState(stickyIntent);
        }
    }

    private void unregisterPowerReceiver() {
        if (!powerReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(powerReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver state can be reset by Activity teardown on old Android builds.
        }
        powerReceiverRegistered = false;
    }

    private void updatePowerState(Intent intent) {
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        boolean charging = plugged != 0;

        if (charging) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (powerValue != null) {
            String percent = "-";
            if (level >= 0 && scale > 0) {
                percent = Math.round(level * 100.0f / scale) + "%";
            }
            powerValue.setText((charging ? "charging, keep screen on" : "not charging, screen may sleep") + " (" + percent + ")");
            if (topBatteryValue != null) {
                topBatteryValue.setText(percent);
            }
            if (topBatteryIcon != null) {
                int batteryPercent = 0;
                if (level >= 0 && scale > 0) {
                    batteryPercent = Math.max(0, Math.min(100, Math.round(level * 100.0f / scale)));
                }
                topBatteryIcon.setBattery(batteryPercent, charging);
            }
        }
    }

    private void addHeader(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, wrap());

        TextView prompt = terminalText(">_ ", MUTED, 15, true);
        row.addView(prompt, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = terminalText("OpenAI Codex", TEXT, 15, true);
        row.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView version = terminalText(" (native side display)", MUTED, 15, false);
        row.addView(version, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addVisitText(LinearLayout parent) {
        addSpacer(parent, 16);
        TextView line1 = terminalText("Visit http://127.0.0.1:8765/status for local sidecar JSON", GREEN, 13, false);
        parent.addView(line1, wrap());
        TextView line2 = terminalText("information, progress, device state, and task details", GREEN, 13, false);
        parent.addView(line2, wrap());
    }

    private TextView addKeyValue(LinearLayout parent, String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(row, wrap());

        TextView keyView = labelText(key, MUTED, 12, false);
        keyView.setGravity(Gravity.LEFT);
        row.addView(keyView, new LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView valueView = labelText(value, TEXT, 13, false);
        valueView.setGravity(Gravity.RIGHT);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        row.addView(valueView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return valueView;
    }

    private LinearLayout panelBlock() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(18));
        panel.setBackground(blockBackground());
        return panel;
    }

    private void addPanelHeader(LinearLayout parent, String title, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(row, wrap());

        TextView left = terminalText(title, GREEN, 12, true);
        left.setSingleLine(true);
        left.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView rightView = terminalText(right, MUTED, 12, false);
        rightView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        rightView.setSingleLine(true);
        row.addView(rightView, new LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));

        View rule = new View(this);
        rule.setBackgroundColor(Color.rgb(53, 52, 46));
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1)));
        ruleParams.setMargins(0, dp(10), 0, 0);
        parent.addView(rule, ruleParams);
    }

    private LimitStatusRow addLimitRow(LinearLayout parent, String label, String rightText) {
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(labelRow, wrap());

        TextView left = terminalText(label, MUTED, 12, false);
        left.setSingleLine(true);
        left.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView right = terminalText(rightText, TEXT, 12, false);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        right.setSingleLine(true);
        labelRow.addView(right, new LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout barRow = new LinearLayout(this);
        barRow.setOrientation(LinearLayout.HORIZONTAL);
        barRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams barRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        barRowParams.setMargins(0, dp(8), 0, dp(18));
        parent.addView(barRow, barRowParams);

        ProgressStripView strip = new ProgressStripView(this);
        barRow.addView(strip, new LinearLayout.LayoutParams(0, dp(10), 1));
        return new LimitStatusRow(strip, right);
    }

    private TextView displayText(String value, int color, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setTypeface(Typeface.create("sans-serif-condensed", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(true);
        view.setSingleLine(false);
        return view;
    }

    private TextView labelText(String value, int color, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(true);
        view.setSingleLine(false);
        return view;
    }

    private TextView badgeText(String value, int color, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        return view;
    }

    private TextView terminalText(String value, int color, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(true);
        view.setSingleLine(false);
        return view;
    }

    private void addSpacer(LinearLayout parent, int heightDp) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp)));
    }

    private void addStatusDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(67, 64, 55));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.max(1, dp(1)),
                dp(22));
        params.setMargins(dp(14), 0, dp(14), 0);
        parent.addView(divider, params);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(PANEL);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), BORDER);
        return drawable;
    }

    private GradientDrawable blockBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(24, 26, 25));
        drawable.setCornerRadius(dp(4));
        drawable.setStroke(dp(1), Color.rgb(61, 58, 50));
        return drawable;
    }

    private GradientDrawable ramsRightPanelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(22, 24, 23));
        drawable.setCornerRadius(dp(4));
        drawable.setStroke(dp(1), Color.rgb(44, 45, 42));
        return drawable;
    }

    private GradientDrawable stageBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(21, 23, 22));
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private GradientDrawable sourceBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(4));
        drawable.setStroke(dp(1), Color.argb(120, 255, 255, 255));
        return drawable;
    }

    private void refreshStatus() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String response = httpGetStatus();
                    final JSONObject json = new JSONObject(response);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            renderStatus(json);
                        }
                    });
                } catch (final Exception exception) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            renderError(exception);
                        }
                    });
                }
            }
        }).start();
    }

    private String httpGetStatus() throws Exception {
        Exception lastException = null;
        for (int i = 0; i < BASE_URLS.length; i++) {
            String baseUrl = BASE_URLS[i];
            try {
                String response = httpGet(baseUrl + "/status");
                activeBaseUrl = baseUrl;
                return response;
            } catch (Exception exception) {
                lastException = exception;
            }
        }
        throw lastException == null ? new IllegalStateException("No sidecar endpoint configured") : lastException;
    }

    private void httpPostPlaybackToggle() throws Exception {
        Exception lastException = null;
        String[] orderedBaseUrls = {
                activeBaseUrl,
                BASE_URLS[0],
                BASE_URLS[1]
        };
        for (int i = 0; i < orderedBaseUrls.length; i++) {
            String baseUrl = orderedBaseUrls[i];
            if (baseUrl == null || baseUrl.length() == 0) {
                continue;
            }
            try {
                httpPost(baseUrl + "/playback/toggle");
                activeBaseUrl = baseUrl;
                return;
            } catch (Exception exception) {
                lastException = exception;
            }
        }
        throw lastException == null ? new IllegalStateException("No sidecar endpoint configured") : lastException;
    }


    private String httpGet(String urlText) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readAll(stream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + " " + body);
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String httpPost(String urlText) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(2000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(0);
            connection.getOutputStream().close();

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readAll(stream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + " " + body);
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readAll(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    private void togglePlayback() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    httpPostPlaybackToggle();
                    final String response = httpGetStatus();
                    final JSONObject json = new JSONObject(response);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            renderStatus(json);
                        }
                    });
                } catch (final Exception exception) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            renderError(exception);
                        }
                    });
                }
            }
        }).start();
    }

    private void renderStatus(JSONObject json) {
        JSONObject task = json.optJSONObject("task");
        JSONObject adb = json.optJSONObject("adb");
        JSONObject codex = json.optJSONObject("codex");
        JSONObject usage = json.optJSONObject("usage");
        JSONObject logs = json.optJSONObject("logs");
        JSONObject spotify = json.optJSONObject("spotify");
        String state = json.optString("state", "ready");

        if (statusValue != null) {
            statusValue.setText(state);
            statusValue.setTextColor(colorForState(state));
        }
        if (topTimeValue != null) {
            topTimeValue.setText(new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
        }
        if (topDateValue != null) {
            topDateValue.setText(new SimpleDateFormat("EEE MM/dd", Locale.US).format(new Date()).toUpperCase(Locale.US));
        }
        renderSpotify(spotify);
        renderAccountUsage(json, usage);
        updatedValue.setText("Updated: " + shortSession(codexText(codex, "updated_at_local")));
    }

    private void renderAccountUsage(JSONObject json, JSONObject usage) {
        if (usage == null) {
            codexUsageValue.setText("Thread tokens: no local row");
            codexBudgetValue.setText("tokens");
            codexScaleValue.setText("offline");
            codexUsageBars.setUsage(0, 0, 0, false, 100);
            usagePercentValue.setText("offline");
            totalUsageStrip.setProgress(0);
            renderLimitWindow(null, hourLimitStrip, hourLimitValue, false);
            renderLimitWindow(null, weeklyLimitStrip, weeklyLimitValue, true);
            return;
        }

        long threadTokens = usage.optLong("thread_tokens_used", 0);
        long goalTokens = usage.optLong("goal_tokens_used", 0);
        long tokenBudget = usage.optLong("token_budget", 0);
        boolean budgetKnown = tokenBudget > 0 || json.optBoolean("progress_known", false);

        codexUsageValue.setText(formatNumber(threadTokens));
        if (budgetKnown) {
            long realUsed = goalTokens > 0 ? goalTokens : threadTokens;
            int percent = (int) Math.min(100, Math.round(realUsed * 100.0f / Math.max(1, tokenBudget)));
            codexBudgetValue.setText("/ " + formatCompactNumber(tokenBudget));
            usagePercentValue.setText(percent + "%");
            totalUsageStrip.setProgress(percent);
            hourLimitStrip.setProgress(percent);
        } else {
            codexBudgetValue.setText("tokens");
            usagePercentValue.setText("live");
            totalUsageStrip.setProgress(0);
        }
        JSONObject accountRateLimits = usage.optJSONObject("account_rate_limits");
        if (accountRateLimits != null && accountRateLimits.optBoolean("account_limits_available", false)) {
            renderLimitWindow(accountRateLimits.optJSONObject("primary"), hourLimitStrip, hourLimitValue, false);
            renderLimitWindow(accountRateLimits.optJSONObject("secondary"), weeklyLimitStrip, weeklyLimitValue, true);
        } else {
            renderLimitWindow(null, hourLimitStrip, hourLimitValue, false);
            renderLimitWindow(null, weeklyLimitStrip, weeklyLimitValue, true);
        }
        codexUsageBars.setUsage(threadTokens, goalTokens, tokenBudget, budgetKnown, Math.max(tokenBudget, 1));
    }

    private void renderLimitWindow(JSONObject window, ProgressStripView strip, TextView value, boolean dateStyle) {
        if (strip == null || value == null) {
            return;
        }
        if (window == null) {
            strip.setProgress(0);
            value.setText("unavailable");
            value.setTextColor(MUTED);
            return;
        }
        int remaining = clamp(window.optInt("remaining_percent", 0));
        long resetsAt = window.optLong("resets_at", 0);
        strip.setProgress(remaining);
        value.setText(remaining + "%  " + formatResetAt(resetsAt, dateStyle));
        value.setTextColor(TEXT);
    }

    private void renderSpotify(JSONObject spotify) {
        if (spotify == null || !spotify.optBoolean("available", false)) {
            String state = spotify == null ? "unavailable" : spotify.optString("state", "unavailable");
            String sourceId = spotify == null ? "unknown" : spotify.optString("source_id", "spotify");
            String sourceName = spotify == null ? "No Source" : spotify.optString("source_name", "Spotify");
            updateSourceBadge(sourceId, sourceName, false);
            updatePlaybackBadge(state, false);
            spotifyValue.setText("No track");
            artistValue.setText("-");
            albumValue.setText("-");
            trackProgressValue.setText("0%");
            trackProgressStrip.setProgress(0);
            if (bottomProgressStrip != null) {
                bottomProgressStrip.setProgress(0);
            }
            if (positionValue != null) {
                positionValue.setText("--:--");
            }
            if (durationValue != null) {
                durationValue.setText("--:--");
            }
            if (trackMetaValue != null) {
                trackMetaValue.setText("Track        -\nArtist       -\nAlbum        -\nDuration     -");
            }
            vinylView.setPlaying(false);
            vinylView.setTrackText("No track", "-");
            vinylView.setArtwork(null);
            lastArtworkKey = "";
            return;
        }

        String sourceId = spotify.optString("source_id", "spotify");
        String sourceName = spotify.optString("source_name", "Spotify");
        String state = spotify.optString("state", "unknown");
        String track = spotify.optString("track", "-");
        String artist = spotify.optString("artist", "-");
        String album = spotify.optString("album", "-");
        int progress = spotify.optInt("progress_percent", 0);
        int positionMs = spotify.optInt("position_ms", 0);
        int durationMs = spotify.optInt("duration_ms", 0);
        updateSourceBadge(sourceId, sourceName, true);
        updatePlaybackBadge(state, true);
        spotifyValue.setText(track);
        artistValue.setText(artist);
        albumValue.setText(album);
        trackProgressValue.setText(clamp(progress) + "%");
        trackProgressStrip.setProgress(progress);
        if (bottomProgressStrip != null) {
            bottomProgressStrip.setProgress(progress);
        }
        if (positionValue != null) {
            positionValue.setText(formatDuration(positionMs));
        }
        if (durationValue != null) {
            durationValue.setText(formatDuration(durationMs));
        }
        if (trackMetaValue != null) {
            trackMetaValue.setText(
                    "Track        " + ellipsize(track, 18) + "\n"
                            + "Artist       " + ellipsize(artist, 18) + "\n"
                            + "Album        " + ellipsize(album, 18) + "\n"
                            + "Duration     " + formatDuration(durationMs));
        }
        vinylView.setPlaying("playing".equalsIgnoreCase(state));
        vinylView.setTrackText(track, artist);

        String key = spotify.optString("artwork_key", "");
        if (spotify.optBoolean("artwork_available", false) && !key.equals(lastArtworkKey)) {
            lastArtworkKey = key;
            loadArtworkAsync(key);
        } else if (!spotify.optBoolean("artwork_available", false) && lastArtworkKey.length() > 0) {
            lastArtworkKey = "";
            vinylView.setArtwork(null);
        }
    }

    private void updateSourceBadge(String sourceId, String sourceName, boolean active) {
        String normalized = sourceId == null ? "" : sourceId.toLowerCase();
        String icon = "?";
        int background = MUTED;
        int text = Color.rgb(15, 16, 16);
        if ("spotify".equals(normalized)) {
            icon = "S";
            background = active ? Color.rgb(30, 215, 96) : BAR_DIM;
        } else if ("apple_music".equals(normalized) || "music".equals(normalized)) {
            icon = "AM";
            background = active ? Color.rgb(250, 78, 120) : BAR_DIM;
        }
        if (sourceValue != null) {
            sourceValue.setText(icon);
            sourceValue.setTextColor(active ? text : Color.rgb(205, 197, 173));
            sourceValue.setBackground(sourceBackground(background));
        }
        if (topSourceIcon != null) {
            Bitmap sourceIcon = null;
            if ("spotify".equals(normalized)) {
                sourceIcon = spotifySourceIcon;
            } else if ("apple_music".equals(normalized) || "music".equals(normalized)) {
                sourceIcon = appleMusicSourceIcon;
            }
            if (sourceIcon != null) {
                topSourceIcon.setImageBitmap(sourceIcon);
            }
            topSourceIcon.setAlpha(active ? 1.0f : 0.45f);
        }
        if (topSourceNameValue != null) {
            topSourceNameValue.setText(sourceName == null || sourceName.length() == 0 ? "Spotify" : sourceName);
            topSourceNameValue.setTextColor(active ? TEXT : MUTED);
        }
    }

    private void updatePlaybackBadge(String state, boolean active) {
        boolean playing = "playing".equalsIgnoreCase(state);
        if (playbackValue != null) {
            playbackValue.setText(playing ? "01  NOW PLAYING" : active ? "01  PAUSED" : "01  NO SOURCE");
            playbackValue.setTextColor(active ? GREEN : MUTED);
            playbackValue.setBackground(null);
        }
        if (playPauseButton != null) {
            playPauseButton.setPlaybackState(playing, active);
            playPauseButton.setEnabled(active);
        }
    }

    private Bitmap loadAssetBitmap(String assetName) {
        InputStream stream = null;
        try {
            stream = getAssets().open(assetName);
            return BitmapFactory.decodeStream(stream);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void loadArtworkAsync(final String key) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(activeBaseUrl + "/spotify-art");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(1500);
                    connection.setReadTimeout(2500);
                    final Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream());
                    if (bitmap != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                vinylView.setArtwork(bitmap);
                            }
                        });
                    }
                } catch (Exception ignored) {
                    // Spotify artwork is optional; the vinyl still renders without it.
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    private String renderUsage(JSONObject usage, boolean progressKnown, int progress) {
        if (usage == null) {
            return "no local usage row";
        }
        String tokens = formatNumber(usage.optLong("thread_tokens_used", 0));
        if (progressKnown) {
            return makeBar(progress, 28) + " " + progress + "% of local goal budget (" + tokens + " tokens)";
        }
        return tokens + " thread tokens used; account quota unavailable locally";
    }

    private String codexText(JSONObject codex, String key) {
        if (codex == null) {
            return "-";
        }
        return codex.optString(key, "-");
    }

    private String formatNumber(long value) {
        String raw = Long.toString(value);
        StringBuilder builder = new StringBuilder();
        int firstGroup = raw.length() % 3;
        if (firstGroup == 0) {
            firstGroup = 3;
        }
        builder.append(raw.substring(0, firstGroup));
        for (int i = firstGroup; i < raw.length(); i += 3) {
            builder.append(",");
            builder.append(raw.substring(i, i + 3));
        }
        return builder.toString();
    }

    private String formatCompactNumber(long value) {
        if (value >= 1000000) {
            return trimDecimal(value / 1000000.0f) + "M";
        }
        if (value >= 1000) {
            return trimDecimal(value / 1000.0f) + "K";
        }
        return Long.toString(value);
    }

    private String trimDecimal(float value) {
        String text = String.format(java.util.Locale.US, "%.1f", value);
        if (text.endsWith(".0")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }

    private long niceTokenScale(long value) {
        long target = Math.max(1000000L, (long) Math.ceil(value * 1.15));
        long[] steps = {
                1000000L, 1500000L, 2000000L, 2500000L, 3000000L,
                4000000L, 5000000L, 7500000L, 10000000L, 15000000L,
                20000000L, 30000000L, 50000000L
        };
        for (int i = 0; i < steps.length; i++) {
            if (target <= steps[i]) {
                return steps[i];
            }
        }
        long million = 1000000L;
        return ((target + million - 1) / million) * million;
    }

    private String renderAdb(JSONObject adb) {
        if (adb == null) {
            return "no adb info";
        }
        int count = adb.optInt("device_count", 0);
        String first = adb.optString("first_device", "no authorized device");
        return count + " device(s), " + first;
    }

    private void renderError(Exception exception) {
        if (statusValue != null) {
            statusValue.setText("offline");
            statusValue.setTextColor(Color.rgb(248, 113, 113));
        }
        if (weeklyUsageValue != null) {
            weeklyUsageValue.setText("sidecar offline");
        }
        if (resetTimeValue != null) {
            resetTimeValue.setText(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
        if (codexUsageValue != null) {
            codexUsageValue.setText("Thread tokens: sidecar offline");
        }
        if (codexBudgetValue != null) {
            codexBudgetValue.setText(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
        if (codexScaleValue != null) {
            codexScaleValue.setText("offline");
        }
        if (codexUsageBars != null) {
            codexUsageBars.setUsage(0, 0, 0, false, 100);
        }
        renderLimitWindow(null, hourLimitStrip, hourLimitValue, false);
        renderLimitWindow(null, weeklyLimitStrip, weeklyLimitValue, true);
    }

    private String makeBar(int percent, int width) {
        int filled = Math.round(width * clamp(percent) / 100.0f);
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0; i < width; i++) {
            builder.append(i < filled ? "█" : "░");
        }
        builder.append("]");
        return builder.toString();
    }

    private int colorForState(String state) {
        if ("done".equalsIgnoreCase(state) || "ready".equalsIgnoreCase(state)) {
            return GREEN;
        }
        if ("working".equalsIgnoreCase(state) || "running".equalsIgnoreCase(state)) {
            return TEXT;
        }
        if ("blocked".equalsIgnoreCase(state) || "error".equalsIgnoreCase(state)) {
            return Color.rgb(251, 146, 60);
        }
        return MUTED;
    }

    private int progressForState(String state) {
        if ("done".equalsIgnoreCase(state)) {
            return 100;
        }
        if ("working".equalsIgnoreCase(state) || "running".equalsIgnoreCase(state)) {
            return 45;
        }
        if ("ready".equalsIgnoreCase(state)) {
            return 10;
        }
        return 0;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String formatDuration(int millis) {
        if (millis <= 0) {
            return "--:--";
        }
        int totalSeconds = millis / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private String formatResetAt(long epochSeconds, boolean dateStyle) {
        if (epochSeconds <= 0) {
            return "--";
        }
        String pattern = dateStyle ? "M月d日" : "HH:mm";
        return new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(epochSeconds * 1000L));
    }

    private String ellipsize(String value, int maxChars) {
        if (value == null) {
            return "-";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 1)) + "...";
    }

    private String shortSession(String updatedAt) {
        if (updatedAt == null) {
            return "-";
        }
        if (updatedAt.length() >= 16) {
            return updatedAt.substring(5, 16).replace('T', ' ');
        }
        if (updatedAt.length() < 19) {
            return updatedAt == null ? "-" : updatedAt;
        }
        return updatedAt.substring(0, 19).replace('T', ' ');
    }

    private String formatUptime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }

    private void enterFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class UsageBarsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float density;
        private long threadTokens;
        private long goalTokens;
        private long tokenBudget;
        private boolean budgetKnown;
        private long visualMax = 100;

        public UsageBarsView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
        }

        public void setUsage(long threadTokens, long goalTokens, long tokenBudget, boolean budgetKnown, long visualMax) {
            this.threadTokens = Math.max(0, threadTokens);
            this.goalTokens = Math.max(0, goalTokens);
            this.tokenBudget = Math.max(0, tokenBudget);
            this.budgetKnown = budgetKnown;
            this.visualMax = Math.max(1, visualMax);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float labelWidth = 72 * density;
            float valueWidth = 58 * density;
            float barLeft = labelWidth;
            float barRight = getWidth() - valueWidth;
            float rowHeight = getHeight() / 2.0f;
            drawRow(canvas, "THREAD", threadTokens, visualMax, 0, barLeft, barRight, rowHeight, BAR);

            if (budgetKnown) {
                long value = goalTokens > 0 ? goalTokens : threadTokens;
                long max = tokenBudget > 0 ? tokenBudget : visualMax;
                drawRow(canvas, goalTokens > 0 ? "GOAL" : "BUDGET", value, max, rowHeight, barLeft, barRight, rowHeight, GREEN);
            } else {
                drawUnavailableRow(canvas, "LIMIT", rowHeight, barLeft, barRight, rowHeight);
            }
        }

        private void drawRow(Canvas canvas, String label, long value, long max, float top, float barLeft, float barRight, float rowHeight, int fillColor) {
            float centerY = top + rowHeight * 0.5f;
            float barHeight = 13 * density;
            float radius = barHeight / 2.0f;
            float barWidth = Math.max(1, barRight - barLeft);
            float percent = Math.max(0, Math.min(1, value / (float) Math.max(1, max)));
            float fillWidth = barWidth * percent;

            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextSize(10 * density);
            paint.setColor(MUTED);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawText(label, 0, centerY + 4 * density, paint);

            rect.set(barLeft, centerY - barHeight / 2, barRight, centerY + barHeight / 2);
            paint.setColor(BAR_DIM);
            canvas.drawRoundRect(rect, radius, radius, paint);

            if (fillWidth > 0) {
                rect.set(barLeft, centerY - barHeight / 2, barLeft + fillWidth, centerY + barHeight / 2);
                paint.setColor(fillColor);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.0f, density));
            paint.setColor(Color.rgb(100, 94, 78));
            rect.set(barLeft + 0.5f, centerY - barHeight / 2 + 0.5f, barRight - 0.5f, centerY + barHeight / 2 - 0.5f);
            canvas.drawRoundRect(rect, radius, radius, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextSize(10 * density);
            paint.setColor(TEXT);
            String valueText = compact(value);
            canvas.drawText(valueText, barRight + 10 * density, centerY + 4 * density, paint);
        }

        private void drawUnavailableRow(Canvas canvas, String label, float top, float barLeft, float barRight, float rowHeight) {
            float centerY = top + rowHeight * 0.5f;
            float barHeight = 13 * density;
            float radius = barHeight / 2.0f;

            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextSize(10 * density);
            paint.setColor(MUTED);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawText(label, 0, centerY + 4 * density, paint);

            rect.set(barLeft, centerY - barHeight / 2, barRight, centerY + barHeight / 2);
            paint.setColor(Color.rgb(42, 43, 39));
            canvas.drawRoundRect(rect, radius, radius, paint);

            paint.setStrokeWidth(1 * density);
            paint.setColor(Color.rgb(83, 79, 67));
            for (float x = barLeft + 5 * density; x < barRight; x += 10 * density) {
                canvas.drawLine(x, centerY - barHeight / 2, x - 5 * density, centerY + barHeight / 2, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(10 * density);
            paint.setColor(MUTED);
            canvas.drawText("local", barRight + 10 * density, centerY + 4 * density, paint);
        }

        private String compact(long value) {
            if (value >= 1000000) {
                return trim(value / 1000000.0f) + "M";
            }
            if (value >= 1000) {
                return trim(value / 1000.0f) + "K";
            }
            return Long.toString(value);
        }

        private String trim(float value) {
            String text = String.format(java.util.Locale.US, "%.1f", value);
            if (text.endsWith(".0")) {
                return text.substring(0, text.length() - 2);
            }
            return text;
        }
    }

    private static class BatteryPillView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float density;
        private int percent;
        private boolean charging;

        public BatteryPillView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
        }

        public void setBattery(int percent, boolean charging) {
            this.percent = Math.max(0, Math.min(100, percent));
            this.charging = charging;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float capWidth = Math.max(2.0f * density, width * 0.10f);
            float bodyRight = width - capWidth - 1.0f * density;
            float top = height * 0.20f;
            float bottom = height * 0.80f;
            float radius = (bottom - top) * 0.28f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.0f, density));
            paint.setColor(Color.rgb(153, 145, 120));
            rect.set(0.5f * density, top, bodyRight, bottom);
            canvas.drawRoundRect(rect, radius, radius, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(153, 145, 120));
            rect.set(bodyRight + 1.0f * density, height * 0.36f, width - 0.5f * density, height * 0.64f);
            canvas.drawRoundRect(rect, 1.0f * density, 1.0f * density, paint);

            float inset = 2.0f * density;
            float fillWidth = Math.max(0, (bodyRight - inset * 2) * percent / 100.0f);
            if (fillWidth > 0) {
                paint.setColor(charging ? GREEN : BAR);
                rect.set(0.5f * density + inset, top + inset, 0.5f * density + inset + fillWidth, bottom - inset);
                canvas.drawRoundRect(rect, Math.max(1.0f, radius - inset), Math.max(1.0f, radius - inset), paint);
            }
        }
    }

    private static class PlaybackButtonView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private boolean playing;
        private boolean active;
        private final float density;

        public PlaybackButtonView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            active = true;
            setFocusable(true);
        }

        public void setPlaybackState(boolean playing, boolean active) {
            this.playing = playing;
            this.active = active;
            invalidate();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float size = Math.min(width, height);
            float cx = width * 0.5f;
            float cy = height * 0.5f;
            float radius = size * 0.42f;
            float pressedOffset = isPressed() ? 1.0f * density : 0f;
            float alpha = active ? 1.0f : 0.45f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((int) (120 * alpha), 0, 0, 0));
            canvas.drawCircle(cx + 2.0f * density, cy + 3.0f * density, radius * 1.02f, paint);

            paint.setColor(Color.rgb(176, 169, 147));
            canvas.drawCircle(cx, cy + pressedOffset, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f * density);
            paint.setColor(Color.argb((int) (210 * alpha), 232, 222, 194));
            canvas.drawCircle(cx, cy + pressedOffset, radius - 0.7f * density, paint);

            paint.setStrokeWidth(0.8f * density);
            paint.setColor(Color.argb((int) (160 * alpha), 83, 80, 69));
            canvas.drawCircle(cx, cy + pressedOffset, radius * 0.82f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((int) (70 * alpha), 252, 244, 218));
            rect.set(cx - radius * 0.40f, cy - radius * 0.52f + pressedOffset,
                    cx + radius * 0.40f, cy - radius * 0.20f + pressedOffset);
            canvas.drawArc(rect, 194, 152, false, paint);

            paint.setColor(Color.argb((int) (230 * alpha), 29, 31, 29));
            if (playing) {
                float barWidth = radius * 0.11f;
                float barHeight = radius * 0.48f;
                float gap = radius * 0.12f;
                rect.set(cx - gap - barWidth, cy - barHeight / 2 + pressedOffset,
                        cx - gap, cy + barHeight / 2 + pressedOffset);
                canvas.drawRoundRect(rect, 0.9f * density, 0.9f * density, paint);
                rect.set(cx + gap, cy - barHeight / 2 + pressedOffset,
                        cx + gap + barWidth, cy + barHeight / 2 + pressedOffset);
                canvas.drawRoundRect(rect, 0.9f * density, 0.9f * density, paint);
            } else {
                path.reset();
                path.moveTo(cx - radius * 0.16f, cy - radius * 0.26f + pressedOffset);
                path.lineTo(cx - radius * 0.16f, cy + radius * 0.26f + pressedOffset);
                path.lineTo(cx + radius * 0.28f, cy + pressedOffset);
                path.close();
                canvas.drawPath(path, paint);
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.7f * density);
            paint.setColor(Color.argb((int) (95 * alpha), 28, 28, 24));
            canvas.drawCircle(cx, cy + pressedOffset, radius * 0.52f, paint);
        }
    }

    private static class ProgressStripView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int progress;
        private final float density;

        public ProgressStripView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
        }

        public void setProgress(int progress) {
            this.progress = Math.max(0, Math.min(100, progress));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = getHeight() / 2.0f;
            rect.set(0, 0, getWidth(), getHeight());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(BAR_DIM);
            canvas.drawRoundRect(rect, radius, radius, paint);

            float fillWidth = getWidth() * progress / 100.0f;
            if (fillWidth > 0) {
                rect.set(0, 0, fillWidth, getHeight());
                paint.setColor(BAR);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.0f, density));
            paint.setColor(Color.rgb(100, 94, 78));
            rect.set(0.5f, 0.5f, getWidth() - 0.5f, getHeight() - 0.5f);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }

    private static class VinylView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private Bitmap artwork;
        private boolean playing;
        private float angle;
        private float armDrop;
        private String track = "No track";
        private String artist = "-";

        public VinylView(Context context) {
            super(context);
        }

        public void setArtwork(Bitmap artwork) {
            this.artwork = artwork;
            invalidate();
        }

        public void setPlaying(boolean playing) {
            this.playing = playing;
            invalidate();
        }

        public void setTrackText(String track, String artist) {
            this.track = track == null ? "-" : track;
            this.artist = artist == null ? "-" : artist;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            float density = getResources().getDisplayMetrics().density;
            float recordCx = width * 0.58f;
            float recordCy = height * 0.50f;
            float radius = Math.min(width * 0.37f, height * 0.44f);
            float targetDrop = playing ? 1.0f : 0.0f;
            if (Math.abs(targetDrop - armDrop) > 0.01f) {
                armDrop += (targetDrop - armDrop) * 0.10f;
            } else {
                armDrop = targetDrop;
            }

            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 8; i++) {
                paint.setColor(Color.argb(10 - i, 210, 205, 190));
                canvas.drawCircle(recordCx, recordCy, radius * (1.05f + i * 0.030f), paint);
            }

            paint.setColor(Color.rgb(9, 10, 10));
            canvas.drawCircle(recordCx, recordCy, radius * 1.04f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f * density);
            paint.setColor(Color.rgb(76, 70, 56));
            canvas.drawCircle(recordCx, recordCy, radius * 1.03f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(12, 13, 13));
            canvas.drawCircle(recordCx, recordCy, radius, paint);

            canvas.save();
            canvas.rotate(angle, recordCx, recordCy);
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 22; i++) {
                paint.setStrokeWidth(i % 5 == 0 ? 1.0f * density : 0.45f * density);
                paint.setColor(i % 5 == 0 ? Color.rgb(36, 37, 34) : Color.rgb(20, 21, 20));
                canvas.drawCircle(recordCx, recordCy, radius * (0.30f + i * 0.030f), paint);
            }
            drawRecordMotionMarks(canvas, recordCx, recordCy, radius, density);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(196, 184, 150));
            canvas.drawCircle(recordCx, recordCy, radius * 0.22f, paint);
            paint.setColor(Color.rgb(14, 15, 15));
            canvas.drawCircle(recordCx, recordCy, radius * 0.055f, paint);
            canvas.restore();

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f * density);
            paint.setColor(Color.rgb(143, 134, 111));
            canvas.drawCircle(recordCx, recordCy, radius, paint);

            float coverSize = radius * 1.44f;
            float coverCx = recordCx - radius * 0.42f;
            float coverCy = recordCy;
            drawAlbumCover(canvas, coverCx, coverCy, coverSize, density);
            drawTonearm(canvas, width, height, recordCx, recordCy, radius, density);

            if (playing) {
                angle = (angle + 0.45f) % 360f;
            }
            if (playing || Math.abs(targetDrop - armDrop) > 0.01f) {
                postInvalidateDelayed(16);
            }
        }

        private void drawRecordMotionMarks(Canvas canvas, float cx, float cy, float radius, float density) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);

            for (int i = 0; i < 9; i++) {
                float degrees = i * 40f + 12f;
                double radians = Math.toRadians(degrees);
                float inner = radius * (0.42f + (i % 3) * 0.12f);
                float outer = inner + radius * (0.16f + (i % 2) * 0.06f);
                float x1 = cx + (float) Math.cos(radians) * inner;
                float y1 = cy + (float) Math.sin(radians) * inner;
                float x2 = cx + (float) Math.cos(radians) * outer;
                float y2 = cy + (float) Math.sin(radians) * outer;
                paint.setStrokeWidth((i % 3 == 0 ? 1.4f : 0.8f) * density);
                paint.setColor(i % 3 == 0 ? Color.rgb(58, 57, 51) : Color.rgb(31, 32, 30));
                canvas.drawLine(x1, y1, x2, y2, paint);
            }

            paint.setStrokeWidth(1.0f * density);
            paint.setColor(Color.rgb(66, 61, 50));
            rect.set(cx - radius * 0.86f, cy - radius * 0.86f, cx + radius * 0.86f, cy + radius * 0.86f);
            canvas.drawArc(rect, 214, 24, false, paint);
            rect.set(cx - radius * 0.68f, cy - radius * 0.68f, cx + radius * 0.68f, cy + radius * 0.68f);
            canvas.drawArc(rect, 28, 18, false, paint);

            paint.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawTonearm(Canvas canvas, int width, int height, float cx, float cy, float radius, float density) {
            float pivotX = cx + radius * 0.80f;
            float pivotY = cy - radius * 0.78f;
            float raisedHeadX = cx + radius * 0.82f;
            float raisedHeadY = cy - radius * 0.30f;
            float loweredHeadX = cx + radius * 0.46f;
            float loweredHeadY = cy + radius * 0.18f;
            float easedDrop = armDrop * armDrop * (3.0f - 2.0f * armDrop);
            float headX = raisedHeadX + (loweredHeadX - raisedHeadX) * easedDrop;
            float headY = raisedHeadY + (loweredHeadY - raisedHeadY) * easedDrop;
            float liftOffset = (1.0f - easedDrop) * 10.0f * density;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(140, 0, 0, 0));
            canvas.drawCircle(pivotX + 3 * density, pivotY + 4 * density, radius * 0.13f, paint);
            paint.setColor(Color.rgb(7, 8, 8));
            canvas.drawCircle(pivotX, pivotY, radius * 0.13f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.0f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.argb(150, 0, 0, 0));
            Path cable = new Path();
            cable.moveTo(pivotX + 3 * density, pivotY + 4 * density);
            cable.cubicTo(cx + radius * 0.78f, cy - radius * 0.42f,
                    cx + radius * 0.72f, cy - radius * 0.05f,
                    headX + 2 * density, headY + liftOffset + 3 * density);
            canvas.drawPath(cable, paint);

            paint.setStrokeWidth(2.0f * density);
            paint.setColor(Color.rgb(74, 73, 66));
            cable.reset();
            cable.moveTo(pivotX, pivotY);
            cable.cubicTo(cx + radius * 0.78f, cy - radius * 0.42f,
                    cx + radius * 0.72f, cy - radius * 0.05f,
                    headX, headY + liftOffset);
            canvas.drawPath(cable, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(24, 25, 24));
            rect.set(pivotX - radius * 0.08f, pivotY - radius * 0.08f, pivotX + radius * 0.08f, pivotY + radius * 0.08f);
            canvas.drawOval(rect, paint);
            paint.setColor(Color.rgb(163, 154, 128));
            canvas.drawCircle(pivotX, pivotY, radius * 0.035f, paint);

            canvas.save();
            float cartridgeY = headY + liftOffset;
            float cartridgeAngle = (float) Math.toDegrees(Math.atan2(cartridgeY - pivotY, headX - pivotX)) + 92.0f;
            canvas.rotate(cartridgeAngle, headX, cartridgeY);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(20, 21, 20));
            rect.set(headX - radius * 0.065f, cartridgeY - radius * 0.040f, headX + radius * 0.095f, cartridgeY + radius * 0.045f);
            canvas.drawRoundRect(rect, 3 * density, 3 * density, paint);
            paint.setColor(Color.rgb(70, 66, 57));
            canvas.drawCircle(headX - radius * 0.025f, cartridgeY, 2.0f * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.0f * density);
            paint.setColor(Color.rgb(165, 155, 126));
            canvas.drawLine(headX - radius * 0.02f, cartridgeY + radius * 0.045f,
                    headX - radius * 0.02f, cartridgeY + radius * (0.045f + 0.030f * easedDrop), paint);
            canvas.restore();

            paint.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawArtwork(Canvas canvas, float cx, float cy, float radius) {
            if (artwork == null) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(232, 215, 166));
                canvas.drawCircle(cx, cy, radius, paint);
                paint.setColor(Color.rgb(29, 31, 30));
                canvas.drawCircle(cx, cy, radius * 0.72f, paint);
                return;
            }

            Path path = new Path();
            path.addCircle(cx, cy, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(path);
            float size = radius * 2;
            float scale = Math.max(size / artwork.getWidth(), size / artwork.getHeight());
            float drawWidth = artwork.getWidth() * scale;
            float drawHeight = artwork.getHeight() * scale;
            rect.set(cx - drawWidth / 2, cy - drawHeight / 2, cx + drawWidth / 2, cy + drawHeight / 2);
            canvas.drawBitmap(artwork, null, rect, paint);
            canvas.restore();
        }

        private void drawAlbumCover(Canvas canvas, float cx, float cy, float size, float density) {
            float left = cx - size / 2;
            float top = cy - size / 2;
            float right = cx + size / 2;
            float bottom = cy + size / 2;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(150, 0, 0, 0));
            rect.set(left + 6 * density, top + 8 * density, right + 8 * density, bottom + 10 * density);
            canvas.drawRoundRect(rect, 5 * density, 5 * density, paint);

            rect.set(left, top, right, bottom);
            paint.setColor(Color.rgb(18, 19, 19));
            canvas.drawRoundRect(rect, 4 * density, 4 * density, paint);

            Path path = new Path();
            path.addRoundRect(rect, 4 * density, 4 * density, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(path);
            if (artwork != null) {
                float scale = Math.max(size / artwork.getWidth(), size / artwork.getHeight());
                float drawWidth = artwork.getWidth() * scale;
                float drawHeight = artwork.getHeight() * scale;
                rect.set(cx - drawWidth / 2, cy - drawHeight / 2, cx + drawWidth / 2, cy + drawHeight / 2);
                canvas.drawBitmap(artwork, null, rect, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(26, 27, 26));
                canvas.drawRect(left, top, right, bottom, paint);
                paint.setColor(Color.rgb(232, 215, 166));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                paint.setTextSize(16 * density);
                canvas.drawText(ellipsize(track, 14), cx, cy - 4 * density, paint);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                paint.setTextSize(10 * density);
                canvas.drawText(ellipsize(artist, 18), cx, cy + 14 * density, paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }
            canvas.restore();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(168, 20, 22, 22));
            rect.set(left, top, right, top + 20 * density);
            canvas.drawRect(rect, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.0f, density));
            paint.setColor(Color.rgb(72, 70, 62));
            rect.set(left + 0.5f, top + 0.5f, right - 0.5f, bottom - 0.5f);
            canvas.drawRoundRect(rect, 4 * density, 4 * density, paint);
        }

        private String ellipsize(String value, int maxChars) {
            if (value == null) {
                return "-";
            }
            if (value.length() <= maxChars) {
                return value;
            }
            return value.substring(0, Math.max(0, maxChars - 1)) + "...";
        }
    }
}
