package com.tinderbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;

/**
 * 前台服务：创建可拖动的悬浮控制窗
 */
public class FloatingWindowService extends Service {

    private static final String CHANNEL_ID = "swipebot_channel";
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    // 拖动相关
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());
        showFloatingWindow();
    }

    private void showFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 300;

        windowManager.addView(floatingView, params);

        setupControls();
        setupDrag();
    }

    private void setupControls() {
        Button btnToggle = floatingView.findViewById(R.id.btn_float_toggle);
        Button btnClose  = floatingView.findViewById(R.id.btn_float_close);
        TextView tvStatus  = floatingView.findViewById(R.id.tv_float_status);
        TextView tvCounter = floatingView.findViewById(R.id.tv_float_counter);

        // 注册回调，实时更新 UI
        if (AutoSwipeAccessibilityService.instance != null) {
            AutoSwipeAccessibilityService.instance.statusCallback = (status, total) ->
                    floatingView.post(() -> {
                        tvStatus.setText(status);
                        tvCounter.setText(total + " 次");
                    });
        }

        btnToggle.setOnClickListener(v -> {
            AutoSwipeAccessibilityService svc = AutoSwipeAccessibilityService.instance;
            if (svc == null) {
                tvStatus.setText("⚠ 无障碍服务未开启");
                return;
            }
            if (svc.isRunning()) {
                svc.stop();
                btnToggle.setText("▶ 开始");
                btnToggle.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFFF6B6B));
            } else {
                // 绑定配置（从 MainActivity 传入的 SharedPreferences）
                applyConfigFromPrefs(svc);
                // 注册回调
                svc.statusCallback = (status, total) ->
                        floatingView.post(() -> {
                            tvStatus.setText(status);
                            tvCounter.setText(total + " 次");
                        });
                svc.start();
                btnToggle.setText("⏹ 停止");
                btnToggle.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF4ECDC4));
            }
        });

        btnClose.setOnClickListener(v -> stopSelf());
    }

    private void applyConfigFromPrefs(AutoSwipeAccessibilityService svc) {
        android.content.SharedPreferences prefs =
                getSharedPreferences("swipebot_prefs", MODE_PRIVATE);
        svc.config.likeProbability   = prefs.getFloat("like_prob", 0.80f);
        svc.config.swipeIntervalMinMs = prefs.getInt("swipe_min_ms", 500);
        svc.config.swipeIntervalMaxMs = prefs.getInt("swipe_max_ms", 2000);
        svc.config.sessionSizeMin     = 90;
        svc.config.sessionSizeMax     = prefs.getInt("session_max", 150);
    }

    private void setupDrag() {
        View container = floatingView.findViewById(R.id.floating_container);
        container.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    isDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int)(event.getRawX() - initialTouchX);
                    int dy = (int)(event.getRawY() - initialTouchY);
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isDragging = true;
                    if (isDragging) {
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        windowManager.updateViewLayout(floatingView, params);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    return isDragging;
            }
            return false;
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
        if (AutoSwipeAccessibilityService.instance != null) {
            AutoSwipeAccessibilityService.instance.stop();
            AutoSwipeAccessibilityService.instance.statusCallback = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── 通知 ─────────────────────────────────────────────────
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Swipe Bot", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("自动滑动服务运行通知");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Swipe Bot 运行中")
                .setContentText("点击返回控制面板")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .build();
    }
}
