package com.tinderbot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    // UI refs
    private TextView tvOverlayStatus, tvAccessibilityStatus;
    private TextView tvLikeProb, tvSwipeMin, tvSwipeMax, tvSessionMax;
    private SeekBar seekLikeProb, seekSwipeMin, seekSwipeMax, seekSessionMax;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("swipebot_prefs", MODE_PRIVATE);
        bindViews();
        setupSeekbars();
        setupButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusDisplay();
    }

    private void bindViews() {
        tvOverlayStatus       = findViewById(R.id.tv_overlay_status);
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);
        tvLikeProb            = findViewById(R.id.tv_like_prob);
        tvSwipeMin            = findViewById(R.id.tv_swipe_min);
        tvSwipeMax            = findViewById(R.id.tv_swipe_max);
        tvSessionMax          = findViewById(R.id.tv_session_max);
        seekLikeProb          = findViewById(R.id.seekbar_like_prob);
        seekSwipeMin          = findViewById(R.id.seekbar_swipe_min);
        seekSwipeMax          = findViewById(R.id.seekbar_swipe_max);
        seekSessionMax        = findViewById(R.id.seekbar_session_max);
    }

    private void setupSeekbars() {
        // 恢复上次设置
        seekLikeProb.setProgress(prefs.getInt("like_prob_pct", 80));
        seekSwipeMin.setProgress(prefs.getInt("swipe_min_prog", 5));
        seekSwipeMax.setProgress(prefs.getInt("swipe_max_prog", 20));
        seekSessionMax.setProgress(prefs.getInt("session_max_prog", 60));
        updateLabels();

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                updateLabels();
                savePrefs();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
        seekLikeProb.setOnSeekBarChangeListener(listener);
        seekSwipeMin.setOnSeekBarChangeListener(listener);
        seekSwipeMax.setOnSeekBarChangeListener(listener);
        seekSessionMax.setOnSeekBarChangeListener(listener);
    }

    private void updateLabels() {
        // Like probability: 0~100 → display as %
        int likeP = seekLikeProb.getProgress();
        tvLikeProb.setText(likeP + "%");

        // Swipe min: 0~30 → 0.5s + progress*0.1s
        float swipeMin = 0.5f + seekSwipeMin.getProgress() * 0.1f;
        tvSwipeMin.setText(String.format("%.1fs", swipeMin));

        // Swipe max: 0~200 → 0.5s + progress*0.1s
        float swipeMax = 0.5f + seekSwipeMax.getProgress() * 0.1f;
        tvSwipeMax.setText(String.format("%.1fs", swipeMax));

        // Session max: 0~110 → 90 + progress
        int sessionMax = 90 + seekSessionMax.getProgress();
        tvSessionMax.setText("90~" + sessionMax);
    }

    private void savePrefs() {
        float swipeMin = 0.5f + seekSwipeMin.getProgress() * 0.1f;
        float swipeMax = 0.5f + seekSwipeMax.getProgress() * 0.1f;
        int sessionMax = 90 + seekSessionMax.getProgress();
        int likeP = seekLikeProb.getProgress();

        prefs.edit()
                .putFloat("like_prob", likeP / 100.0f)
                .putInt("like_prob_pct", likeP)
                .putInt("swipe_min_ms", (int)(swipeMin * 1000))
                .putInt("swipe_max_ms", (int)(swipeMax * 1000))
                .putInt("swipe_min_prog", seekSwipeMin.getProgress())
                .putInt("swipe_max_prog", seekSwipeMax.getProgress())
                .putInt("session_max", sessionMax)
                .putInt("session_max_prog", seekSessionMax.getProgress())
                .apply();
    }

    private void setupButtons() {
        Button btnOverlay       = findViewById(R.id.btn_grant_overlay);
        Button btnAccessibility = findViewById(R.id.btn_grant_accessibility);
        Button btnStart         = findViewById(R.id.btn_start_floating);

        btnOverlay.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this,
                    "找到「Swipe Bot 自动滑动服务」并开启", Toast.LENGTH_LONG).show();
        });

        btnStart.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授权悬浮窗权限！", Toast.LENGTH_SHORT).show();
                return;
            }
            if (AutoSwipeAccessibilityService.instance == null) {
                Toast.makeText(this, "请先开启无障碍服务！", Toast.LENGTH_SHORT).show();
                return;
            }
            savePrefs();
            startService(new Intent(this, FloatingWindowService.class));
            Toast.makeText(this, "悬浮窗已启动，切换到 Tinder 即可使用", Toast.LENGTH_SHORT).show();
            // 切换到桌面
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            startActivity(home);
        });
    }

    private void updateStatusDisplay() {
        // 悬浮窗权限
        if (Settings.canDrawOverlays(this)) {
            tvOverlayStatus.setText("✅ 已授权");
            tvOverlayStatus.setTextColor(0xFF4ECDC4);
        } else {
            tvOverlayStatus.setText("❌ 未授权");
            tvOverlayStatus.setTextColor(0xFFFF6B6B);
        }

        // 无障碍服务
        if (AutoSwipeAccessibilityService.instance != null) {
            tvAccessibilityStatus.setText("✅ 已启用");
            tvAccessibilityStatus.setTextColor(0xFF4ECDC4);
        } else {
            tvAccessibilityStatus.setText("❌ 未启用");
            tvAccessibilityStatus.setTextColor(0xFFFF6B6B);
        }
    }
}
