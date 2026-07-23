package com.thangtinstore.vf3dexopen;

import android.Manifest;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends ComponentActivity implements LocationListener {
    private static final int REQ_LOCATION = 1001;
    private static final int REQ_CAMERA = 1002;
    private static final String PREFS = "vf3dex_prefs";

    private static final String PREF_VEHICLE = "vehicle";
    private static final String PREF_CUSTOM_VEHICLE_NAME = "custom_vehicle_name";
    private static final String PREF_VEHICLE_COLOR = "vehicle_color";
    private static final String PREF_APP_PACKAGE = "app_package";
    private static final String PREF_APP_LABEL = "app_label";
    private static final String PREF_APP_URL = "app_url";
    private static final String PREF_GUIDE_WIDTH_PREFIX = "guide_width_";
    private static final String PREF_GUIDE_CURVE_PREFIX = "guide_curve_";
    private static final String PREF_GUIDE_Y_OFFSET_PREFIX = "guide_y_offset_";

    private static final String[] VEHICLE_CHOICES = new String[]{
            "VF 3", "VF 5", "VF 6", "VF 7", "VF 8", "VF 9", "VF e34", "Minio Green", "Limo Green",
            "Tesla Model 3", "Tesla Model Y", "BMW i4", "Mercedes EQE", "Kia EV6", "Hyundai Ioniq 5"
    };

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private Location lastLocation;
    private int vehicleColor = Color.rgb(255,255,255);
    private boolean copyrightExpanded = false;

    // UI
    private TextView timeText;
    private TextView speedText;
    private TextView statusText;
    private TextView latLngText;
    private TextView accuracyText;
    private TextView headingText;
    private TextView vehicleNameText;
    private TextView appChipText;
    private Spinner vehicleSpinner;
    private Car3DView car3DView;
    private PreviewView cameraPreviewView;
    private FrameLayout cameraContainer;
    private ReverseGuideView reverseGuideView;
    private boolean guideVisible = true;
    private float guideWidthFactor = 1.00f;
    private float guideCurveFactor = 0.00f;
    private float guideYOffsetFactor = 0.00f;
    private LinearLayout appPreviewArea;
    private TextView appHintText;
    private Button startCameraBtn;
    private Button stopCameraBtn;

    private ProcessCameraProvider cameraProvider;
    private boolean cameraRunning = false;

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            if (timeText != null) {
                timeText.setText(new SimpleDateFormat("HH:mm:ss  •  dd/MM/yyyy", Locale.getDefault()).format(new Date()));
            }
            if (car3DView != null) car3DView.tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        vehicleColor = prefs.getInt(PREF_VEHICLE_COLOR, Color.WHITE);
        ensureDefaults();

        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setStatusBarColor(Color.rgb(10, 14, 19));
            getWindow().setNavigationBarColor(Color.rgb(10, 14, 19));
        } catch (Throwable ignored) {}

        buildUi();
        refreshVehicleUi();
        refreshAppUi();
        handler.post(clockRunnable);
        updateStatus("Sẵn sàng. Dùng GPS để xem tốc độ, hoặc mở cam lùi.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        stopLocationUpdates();
        stopReverseCamera();
    }

    private void ensureDefaults() {
        SharedPreferences.Editor ed = prefs.edit();
        if (TextUtils.isEmpty(prefs.getString(PREF_VEHICLE, ""))) ed.putString(PREF_VEHICLE, "VF 3");
        if (TextUtils.isEmpty(prefs.getString(PREF_APP_URL, ""))) ed.putString(PREF_APP_URL, "https://m.youtube.com/");
        if (TextUtils.isEmpty(prefs.getString(PREF_APP_LABEL, ""))) ed.putString(PREF_APP_LABEL, defaultAppLabel());
        if (TextUtils.isEmpty(prefs.getString(PREF_APP_PACKAGE, ""))) ed.putString(PREF_APP_PACKAGE, defaultAppPackage());
        ed.apply();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(buildBackground());
        root.setPadding(dp(10), dp(10), dp(10), dp(10));

        root.addView(buildTopBrandBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams toolbarLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        toolbarLp.topMargin = dp(8);
        root.addView(buildToolbar(), toolbarLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setWeightSum(100f);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentLp.topMargin = dp(8);
        root.addView(content, contentLp);

        LinearLayout dashboardPanel = panel();
        LinearLayout appPanel = panel();
        content.addView(dashboardPanel, slotLp(42));
        content.addView(appPanel, slotLp(58));

        buildDashboardPanel(dashboardPanel);
        buildAppPanel(appPanel);
        setContentView(root);
    }

    private GradientDrawable buildBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(8,12,18), Color.rgb(16,24,35), Color.rgb(10,16,24)});
    }

    private LinearLayout buildTopBrandBar() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.setBackground(chipBg(Color.argb(120, 255,255,255), 18, Color.argb(50,255,255,255)));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_kt);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        logoLp.rightMargin = dp(10);
        top.addView(logo, logoLp);

        TextView title = new TextView(this);
        title.setText("KT • Bản quyền phần mềm");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(12);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView toggle = miniLabel(copyrightExpanded ? "Ẩn" : "Hiện");
        top.addView(toggle);
        card.addView(top);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(44), dp(6), 0, 0);
        TextView line1 = new TextView(this);
        line1.setText("Kim Ngọc Minh Trí");
        line1.setTextColor(Color.WHITE);
        line1.setTypeface(Typeface.DEFAULT_BOLD);
        line1.setTextSize(12);
        details.addView(line1);
        TextView line2 = new TextView(this);
        line2.setText("Hàm Giang, Vĩnh Long");
        line2.setTextColor(Color.rgb(190,205,220));
        line2.setTextSize(11);
        details.addView(line2);
        details.setVisibility(copyrightExpanded ? View.VISIBLE : View.GONE);
        card.addView(details);

        View.OnClickListener listener = v -> {
            copyrightExpanded = !copyrightExpanded;
            details.setVisibility(copyrightExpanded ? View.VISIBLE : View.GONE);
            toggle.setText(copyrightExpanded ? "Ẩn" : "Hiện");
        };
        card.setOnClickListener(listener);
        toggle.setOnClickListener(listener);
        return card;
    }

    private HorizontalScrollView buildToolbar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setFillViewport(true);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(chipBg(Color.argb(200, 19,29,41), 16, Color.argb(80,255,255,255)));
        hsv.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        vehicleSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, VEHICLE_CHOICES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vehicleSpinner.setAdapter(adapter);
        String selected = prefs.getString(PREF_VEHICLE, VEHICLE_CHOICES[0]);
        for (int i = 0; i < VEHICLE_CHOICES.length; i++) if (VEHICLE_CHOICES[i].equals(selected)) vehicleSpinner.setSelection(i);
        vehicleSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override public void onItemSelected(int position) {
                prefs.edit().putString(PREF_VEHICLE, VEHICLE_CHOICES[position]).apply();
                if (TextUtils.isEmpty(prefs.getString(PREF_CUSTOM_VEHICLE_NAME, ""))) refreshVehicleUi();
            }
        });
        row.addView(vehicleSpinner, new LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT));

        row.addView(space(6));
        Button nameBtn = tinyButton("Tên xe");
        nameBtn.setOnClickListener(v -> showVehicleNameDialog());
        row.addView(nameBtn);

        row.addView(space(6));
        Button colorBtn = tinyButton("Màu xe");
        colorBtn.setOnClickListener(v -> showVehicleColorDialog());
        row.addView(colorBtn);

        row.addView(space(10));
        Button gpsBtn = tinyButton("GPS");
        gpsBtn.setOnClickListener(v -> startLocationFlow());
        row.addView(gpsBtn);

        row.addView(space(6));
        Button chooseApp = tinyButton("Chọn app");
        chooseApp.setOnClickListener(v -> chooseApp());
        row.addView(chooseApp);

        row.addView(space(6));
        Button camBtn = tinyButton("Cam lùi");
        camBtn.setOnClickListener(v -> startReverseCameraFlow());
        row.addView(camBtn);

        row.addView(space(6));
        Button stopCamBtn = tinyButton("Tắt cam");
        stopCamBtn.setOnClickListener(v -> stopReverseCamera());
        row.addView(stopCamBtn);

        row.addView(space(6));
        Button settingsBtn = tinyButton("Quyền");
        settingsBtn.setOnClickListener(v -> openAppSettings());
        row.addView(settingsBtn);
        return hsv;
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(10), dp(10), dp(10), dp(10));
        p.setBackground(chipBg(Color.argb(220, 16,23,31), 24, Color.argb(60,255,255,255)));
        return p;
    }

    private void buildDashboardPanel(LinearLayout parent) {
        vehicleNameText = title(getCurrentVehicleName() + "  •  Dashboard 3D");
        parent.addView(vehicleNameText);
        timeText = subText("--:--:--");
        timeText.setGravity(Gravity.CENTER_HORIZONTAL);
        parent.addView(timeText);

        car3DView = new Car3DView(this);
        car3DView.setVehicleLabel(getCurrentVehicleName());
        car3DView.setVehicleColor(vehicleColor);
        LinearLayout.LayoutParams carLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        carLp.topMargin = dp(8);
        carLp.bottomMargin = dp(8);
        parent.addView(car3DView, carLp);

        speedText = bigText("0");
        parent.addView(speedText);
        TextView km = subText("km/h");
        km.setTextSize(20);
        km.setGravity(Gravity.CENTER_HORIZONTAL);
        parent.addView(km);

        statusText = smallText("Trạng thái: chưa bật GPS");
        latLngText = smallText("Tọa độ: --");
        accuracyText = smallText("Độ chính xác: --");
        headingText = smallText("Hướng: --");
        parent.addView(statusText);
        parent.addView(latLngText);
        parent.addView(accuracyText);
        parent.addView(headingText);

        TextView footer = subText("© Kim Ngọc Minh Trí • Hàm Giang, Vĩnh Long");
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(0, dp(8), 0, 0);
        parent.addView(footer);
    }

    private void buildAppPanel(LinearLayout parent) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = title("Màn hình app / cam lùi");
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        appChipText = miniLabel("");
        top.addView(appChipText);
        parent.addView(top);

        TextView desc = subText("Chọn app cần chạy bên phải. Có thể mở app, mở web fallback hoặc bật cam lùi tích hợp.");
        desc.setPadding(0, dp(4), 0, dp(8));
        parent.addView(desc);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setWeightSum(6f);
        Button openApp = actionButton("Mở app");
        openApp.setOnClickListener(v -> openSelectedApp());
        Button openWeb = actionButton("Mở web");
        openWeb.setOnClickListener(v -> openSelectedWeb());
        startCameraBtn = actionButton("Bật cam lùi");
        startCameraBtn.setOnClickListener(v -> startReverseCameraFlow());
        stopCameraBtn = actionButton("Tắt cam");
        stopCameraBtn.setOnClickListener(v -> stopReverseCamera());
        Button guideBtn = actionButton("Line");
        guideBtn.setOnClickListener(v -> {
            guideVisible = !guideVisible;
            if (reverseGuideView != null) reverseGuideView.setVisibility(guideVisible ? View.VISIBLE : View.GONE);
        });
        Button guideSetupBtn = actionButton("Cài line");
        guideSetupBtn.setOnClickListener(v -> showGuideSettingsDialog());
        actions.addView(openApp, btnBarLp());
        actions.addView(openWeb, btnBarLp());
        actions.addView(startCameraBtn, btnBarLp());
        actions.addView(stopCameraBtn, btnBarLp());
        actions.addView(guideBtn, btnBarLp());
        actions.addView(guideSetupBtn, btnBarLp());
        parent.addView(actions);

        appPreviewArea = new LinearLayout(this);
        appPreviewArea.setOrientation(LinearLayout.VERTICAL);
        appPreviewArea.setGravity(Gravity.CENTER);
        appPreviewArea.setBackground(chipBg(Color.argb(150,255,255,255), 18, Color.argb(60,255,255,255)));
        LinearLayout.LayoutParams areaLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        areaLp.topMargin = dp(10);
        parent.addView(appPreviewArea, areaLp);

        TextView icon = new TextView(this);
        icon.setText("▣");
        icon.setTextSize(44);
        icon.setTextColor(Color.WHITE);
        appPreviewArea.addView(icon);
        appHintText = new TextView(this);
        appHintText.setTextColor(Color.WHITE);
        appHintText.setTextSize(18);
        appHintText.setTypeface(Typeface.DEFAULT_BOLD);
        appHintText.setGravity(Gravity.CENTER);
        appPreviewArea.addView(appHintText);
        TextView note = subText("App được mở bằng launch bounds để vừa khung hơn trên Samsung DeX. Cam lùi dùng camera sau của máy / tablet.");
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(16), dp(10), dp(16), 0);
        appPreviewArea.addView(note);

        cameraContainer = new FrameLayout(this);
        cameraContainer.setVisibility(View.GONE);

        cameraPreviewView = new PreviewView(this);
        cameraPreviewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        cameraPreviewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        cameraContainer.addView(cameraPreviewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        reverseGuideView = new ReverseGuideView(this);
        loadGuideSettingsForCurrentVehicle();
        applyGuideSettings();
        reverseGuideView.setVisibility(guideVisible ? View.VISIBLE : View.GONE);
        cameraContainer.addView(reverseGuideView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        parent.addView(cameraContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void refreshAppUi() {
        String label = prefs.getString(PREF_APP_LABEL, defaultAppLabel());
        if (appChipText != null) appChipText.setText(label);
        if (appHintText != null) appHintText.setText(label);
    }

    private String getCurrentVehicleName() {
        String custom = prefs.getString(PREF_CUSTOM_VEHICLE_NAME, "");
        if (!TextUtils.isEmpty(custom)) return custom.trim();
        return prefs.getString(PREF_VEHICLE, VEHICLE_CHOICES[0]);
    }

    private void refreshVehicleUi() {
        String name = getCurrentVehicleName();
        if (vehicleNameText != null) vehicleNameText.setText(name + "  •  Dashboard 3D");
        if (car3DView != null) {
            car3DView.setVehicleLabel(name);
            car3DView.setVehicleColor(vehicleColor);
        }
        loadGuideSettingsForCurrentVehicle();
        applyGuideSettings();
    }

    private void chooseApp() {
        List<AppEntry> apps = queryLaunchableApps();
        if (apps.isEmpty()) {
            Toast.makeText(this, "Không lấy được danh sách ứng dụng", Toast.LENGTH_LONG).show();
            return;
        }
        CharSequence[] names = new CharSequence[apps.size()];
        for (int i = 0; i < apps.size(); i++) names[i] = apps.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle("Chọn app để chạy")
                .setItems(names, (dialog, which) -> {
                    AppEntry entry = apps.get(which);
                    prefs.edit().putString(PREF_APP_PACKAGE, entry.packageName).putString(PREF_APP_LABEL, entry.label).apply();
                    refreshAppUi();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private List<AppEntry> queryLaunchableApps() {
        List<AppEntry> result = new ArrayList<>();
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = getPackageManager().queryIntentActivities(intent, 0);
            for (ResolveInfo ri : infos) {
                if (ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
                CharSequence labelCs = ri.loadLabel(getPackageManager());
                result.add(new AppEntry(labelCs == null ? ri.activityInfo.packageName : labelCs.toString(), ri.activityInfo.packageName));
            }
            Collections.sort(result, Comparator.comparing(a -> a.label.toLowerCase(Locale.getDefault())));
        } catch (Throwable ignored) {}
        return result;
    }

    private void openSelectedApp() {
        stopReverseCamera();
        String pkg = prefs.getString(PREF_APP_PACKAGE, defaultAppPackage());
        if (!TextUtils.isEmpty(pkg)) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                launchIntoBounds(launch, appPreviewArea);
                return;
            }
        }
        openSelectedWeb();
    }

    private void openSelectedWeb() {
        stopReverseCamera();
        openUrlIntoBounds(prefs.getString(PREF_APP_URL, "https://m.youtube.com/"), appPreviewArea);
    }

    private String defaultAppPackage() {
        String[] packages = new String[]{"com.google.android.youtube", "vn.vietmap.vietmap", "com.android.chrome", "com.sec.android.app.sbrowser"};
        for (String pkg : packages) if (getPackageManager().getLaunchIntentForPackage(pkg) != null) return pkg;
        return "";
    }

    private String defaultAppLabel() {
        return resolveLabel(defaultAppPackage(), "Ứng dụng");
    }

    private String resolveLabel(String packageName, String fallback) {
        if (TextUtils.isEmpty(packageName)) return fallback;
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = getPackageManager().getApplicationLabel(ai);
            return label == null ? fallback : label.toString();
        } catch (Throwable ignored) { return fallback; }
    }


    private String guideVehicleKey() {
        String name = getCurrentVehicleName();
        if (TextUtils.isEmpty(name)) name = "VF3";
        return name.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "_");
    }

    private void loadGuideSettingsForCurrentVehicle() {
        String key = guideVehicleKey();
        guideWidthFactor = prefs.getFloat(PREF_GUIDE_WIDTH_PREFIX + key, 1.00f);
        guideCurveFactor = prefs.getFloat(PREF_GUIDE_CURVE_PREFIX + key, 0.00f);
        guideYOffsetFactor = prefs.getFloat(PREF_GUIDE_Y_OFFSET_PREFIX + key, 0.00f);
    }

    private void saveGuideSettingsForCurrentVehicle() {
        String key = guideVehicleKey();
        prefs.edit()
                .putFloat(PREF_GUIDE_WIDTH_PREFIX + key, guideWidthFactor)
                .putFloat(PREF_GUIDE_CURVE_PREFIX + key, guideCurveFactor)
                .putFloat(PREF_GUIDE_Y_OFFSET_PREFIX + key, guideYOffsetFactor)
                .apply();
    }

    private void resetGuideSettingsForCurrentVehicle() {
        guideWidthFactor = 1.00f;
        guideCurveFactor = 0.00f;
        guideYOffsetFactor = 0.00f;
        saveGuideSettingsForCurrentVehicle();
        applyGuideSettings();
    }

    private void applyGuideSettings() {
        if (reverseGuideView != null) {
            reverseGuideView.setGuideParams(guideWidthFactor, guideCurveFactor, guideYOffsetFactor);
        }
    }

    private void showGuideSettingsDialog() {
        loadGuideSettingsForCurrentVehicle();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(4));

        TextView vehicleLabel = subText("Cấu hình riêng cho: " + getCurrentVehicleName());
        vehicleLabel.setPadding(0, 0, 0, dp(8));
        box.addView(vehicleLabel);

        TextView widthLabel = smallText("");
        SeekBar widthSeek = new SeekBar(this);
        widthSeek.setMax(90);
        widthSeek.setProgress(Math.max(0, Math.min(90, Math.round((guideWidthFactor - 0.60f) * 100f))));

        TextView curveLabel = smallText("");
        SeekBar curveSeek = new SeekBar(this);
        curveSeek.setMax(100);
        curveSeek.setProgress(Math.max(0, Math.min(100, Math.round((guideCurveFactor + 1.00f) * 50f))));

        TextView yLabel = smallText("");
        SeekBar ySeek = new SeekBar(this);
        ySeek.setMax(100);
        ySeek.setProgress(Math.max(0, Math.min(100, Math.round((guideYOffsetFactor + 1.00f) * 50f))));

        Runnable syncLabels = () -> {
            float width = 0.60f + widthSeek.getProgress() / 100f;
            float curve = (curveSeek.getProgress() - 50) / 50f;
            float y = (ySeek.getProgress() - 50) / 50f;
            widthLabel.setText(String.format(Locale.getDefault(), "Độ rộng line: %.0f%%", width * 100f));
            curveLabel.setText(String.format(Locale.getDefault(), "Độ cong line: %.0f", curve * 50f));
            yLabel.setText(String.format(Locale.getDefault(), "Vị trí line lên/xuống: %.0f", y * 50f));
        };

        SeekBar.OnSeekBarChangeListener livePreview = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                guideWidthFactor = 0.60f + widthSeek.getProgress() / 100f;
                guideCurveFactor = (curveSeek.getProgress() - 50) / 50f;
                guideYOffsetFactor = (ySeek.getProgress() - 50) / 50f;
                syncLabels.run();
                applyGuideSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        widthSeek.setOnSeekBarChangeListener(livePreview);
        curveSeek.setOnSeekBarChangeListener(livePreview);
        ySeek.setOnSeekBarChangeListener(livePreview);

        box.addView(widthLabel);
        box.addView(widthSeek);
        box.addView(curveLabel);
        box.addView(curveSeek);
        box.addView(yLabel);
        box.addView(ySeek);

        TextView note = subText("Gợi ý VF3: độ rộng 100%, độ cong 0, vị trí 0. Có thể tinh chỉnh theo vị trí camera thực tế.");
        note.setPadding(0, dp(8), 0, 0);
        box.addView(note);

        syncLabels.run();

        new AlertDialog.Builder(this)
                .setTitle("Cài guide line cam lùi")
                .setView(box)
                .setPositiveButton("Lưu cho xe này", (dialog, which) -> {
                    guideWidthFactor = 0.60f + widthSeek.getProgress() / 100f;
                    guideCurveFactor = (curveSeek.getProgress() - 50) / 50f;
                    guideYOffsetFactor = (ySeek.getProgress() - 50) / 50f;
                    saveGuideSettingsForCurrentVehicle();
                    applyGuideSettings();
                    Toast.makeText(this, "Đã lưu cấu hình line cho " + getCurrentVehicleName(), Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Reset xe này", (dialog, which) -> {
                    resetGuideSettingsForCurrentVehicle();
                    Toast.makeText(this, "Đã reset cấu hình line cho " + getCurrentVehicleName(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", (dialog, which) -> {
                    loadGuideSettingsForCurrentVehicle();
                    applyGuideSettings();
                })
                .show();
    }

    private void showVehicleNameDialog() {
        final EditText input = new EditText(this);
        input.setHint("Ví dụ: VF3 Touring / Tesla Model 3 / Xe của tôi");
        input.setText(prefs.getString(PREF_CUSTOM_VEHICLE_NAME, ""));
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Nhập tên xe")
                .setView(input)
                .setPositiveButton("Lưu", (d, w) -> {
                    prefs.edit().putString(PREF_CUSTOM_VEHICLE_NAME, input.getText() == null ? "" : input.getText().toString().trim()).apply();
                    refreshVehicleUi();
                })
                .setNeutralButton("Dùng tên trong danh sách", (d, w) -> {
                    prefs.edit().remove(PREF_CUSTOM_VEHICLE_NAME).apply();
                    refreshVehicleUi();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showVehicleColorDialog() {
        final String[] names = new String[]{"Trắng", "Đen", "Đỏ", "Xanh dương", "Xám", "Vàng", "Xanh lá"};
        final int[] colors = new int[]{Color.WHITE, Color.BLACK, Color.rgb(220, 40, 40), Color.rgb(45,120,235), Color.rgb(145,150,160), Color.rgb(245,196,28), Color.rgb(48,166,91)};
        int selected = 0;
        for (int i = 0; i < colors.length; i++) if (colors[i] == vehicleColor) selected = i;
        final int[] temp = new int[]{selected};
        new AlertDialog.Builder(this)
                .setTitle("Chọn màu xe")
                .setSingleChoiceItems(names, selected, (d, which) -> temp[0] = which)
                .setPositiveButton("Áp dụng", (d, w) -> {
                    vehicleColor = colors[temp[0]];
                    prefs.edit().putInt(PREF_VEHICLE_COLOR, vehicleColor).apply();
                    refreshVehicleUi();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void startReverseCameraFlow() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        startReverseCamera();
    }

    private void startReverseCamera() {
        try {
            appPreviewArea.setVisibility(View.GONE);
            if (cameraContainer != null) cameraContainer.setVisibility(View.VISIBLE);
            if (reverseGuideView != null) reverseGuideView.setVisibility(guideVisible ? View.VISIBLE : View.GONE);
            ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
            future.addListener(() -> {
                try {
                    cameraProvider = future.get();
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(cameraPreviewView.getSurfaceProvider());
                    CameraSelector selector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
                    cameraProvider.unbindAll();
                    cameraProvider.bindToLifecycle((LifecycleOwner) this, selector, preview);
                    cameraRunning = true;
                    updateStatus("Cam lùi đang chạy");
                } catch (Throwable t) {
                    if (cameraContainer != null) cameraContainer.setVisibility(View.GONE);
                    appPreviewArea.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Không bật được camera: " + safeMessage(t), Toast.LENGTH_LONG).show();
                }
            }, ContextCompat.getMainExecutor(this));
        } catch (Throwable t) {
            Toast.makeText(this, "Lỗi camera: " + safeMessage(t), Toast.LENGTH_LONG).show();
        }
    }

    private void stopReverseCamera() {
        if (cameraProvider != null) {
            try { cameraProvider.unbindAll(); } catch (Throwable ignored) {}
        }
        cameraRunning = false;
        if (cameraContainer != null) cameraContainer.setVisibility(View.GONE);
        if (appPreviewArea != null) appPreviewArea.setVisibility(View.VISIBLE);
    }

    private void launchIntoBounds(Intent intent, View view) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 24 && view != null) {
                Rect bounds = getViewBoundsOnScreen(view);
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchBounds(bounds);
                startActivity(intent, options.toBundle());
            } else {
                startActivity(intent);
            }
        } catch (Throwable t) {
            startActivity(intent);
        }
    }

    private Rect getViewBoundsOnScreen(View view) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return new Rect(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());
    }

    private void openUrlIntoBounds(String url, View view) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchIntoBounds(intent, view);
        } catch (Throwable t) {
            Toast.makeText(this, "Không mở được: " + url, Toast.LENGTH_LONG).show();
        }
    }

    // GPS
    private void startLocationFlow() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        startLocationUpdates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startLocationUpdates();
            else Toast.makeText(this, "Cần quyền vị trí để đo tốc độ GPS", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startReverseCamera();
            else Toast.makeText(this, "Cần quyền camera để dùng cam lùi", Toast.LENGTH_LONG).show();
        }
    }

    private void startLocationUpdates() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) { updateStatus("Không lấy được LocationManager."); return; }
            boolean gpsEnabled = false, networkEnabled = false;
            try { gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Throwable ignored) {}
            try { networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Throwable ignored) {}
            if (!gpsEnabled && !networkEnabled) { updateStatus("GPS đang tắt. Hãy bật Vị trí."); openLocationSettings(); return; }
            if (gpsEnabled) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            if (networkEnabled) locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1500, 0, this);
            updateStatus("GPS đang chạy...");
        } catch (SecurityException ignored) {
            updateStatus("Thiếu quyền vị trí.");
        } catch (Throwable t) {
            updateStatus("Lỗi GPS: " + safeMessage(t));
        }
    }

    private void stopLocationUpdates() {
        try { if (locationManager != null) locationManager.removeUpdates(this); } catch (Throwable ignored) {}
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        float speedKmh = 0f;
        if (location.hasSpeed()) speedKmh = location.getSpeed() * 3.6f;
        else if (lastLocation != null && location.getTime() > lastLocation.getTime()) {
            float meters = lastLocation.distanceTo(location);
            float seconds = (location.getTime() - lastLocation.getTime()) / 1000f;
            if (seconds > 0) speedKmh = (meters / seconds) * 3.6f;
        }
        lastLocation = location;
        speedText.setText(String.format(Locale.getDefault(), "%.0f", speedKmh));
        latLngText.setText(String.format(Locale.US, "Tọa độ: %.6f, %.6f", location.getLatitude(), location.getLongitude()));
        accuracyText.setText(location.hasAccuracy() ? String.format(Locale.getDefault(), "Độ chính xác: %.0f m", location.getAccuracy()) : "Độ chính xác: --");
        headingText.setText(location.hasBearing() ? String.format(Locale.getDefault(), "Hướng: %.0f°", location.getBearing()) : "Hướng: --");
        updateStatus("Đang nhận tín hiệu GPS");
        if (car3DView != null) {
            car3DView.setBearing(location.hasBearing() ? location.getBearing() : 0f);
            car3DView.setSpeed(speedKmh);
        }
    }

    @Override public void onProviderEnabled(@NonNull String provider) { updateStatus(provider + " đã bật"); }
    @Override public void onProviderDisabled(@NonNull String provider) { updateStatus(provider + " đã tắt"); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

    private void openLocationSettings() {
        try { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); } catch (Throwable ignored) {}
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "Không mở được cài đặt ứng dụng", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus(String s) {
        if (statusText != null) statusText.setText("Trạng thái: " + s);
    }

    private String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m;
    }

    // Simple UI helpers
    private LinearLayout.LayoutParams slotLp(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        return lp;
    }
    private LinearLayout.LayoutParams btnBarLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp.setMargins(dp(4), dp(4), dp(4), 0);
        return lp;
    }
    private View space(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(dp), 1));
        return v;
    }
    private GradientDrawable chipBg(int color, int radiusDp, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radiusDp));
        gd.setStroke(dp(1), strokeColor);
        return gd;
    }
    private TextView title(String text) { TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(Color.WHITE); tv.setTextSize(22); tv.setTypeface(Typeface.DEFAULT_BOLD); return tv; }
    private TextView subText(String text) { TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(Color.rgb(188,203,220)); tv.setTextSize(13); return tv; }
    private TextView smallText(String text) { TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(Color.rgb(205,218,230)); tv.setTextSize(13); tv.setPadding(0, dp(3), 0, dp(3)); return tv; }
    private TextView bigText(String text) { TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(Color.WHITE); tv.setTextSize(62); tv.setTypeface(Typeface.DEFAULT_BOLD); tv.setGravity(Gravity.CENTER_HORIZONTAL); return tv; }
    private TextView miniLabel(String text) { TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(Color.WHITE); tv.setTextSize(12); tv.setPadding(dp(10), dp(6), dp(10), dp(6)); tv.setBackground(chipBg(Color.argb(90,255,255,255), 999, Color.argb(60,255,255,255))); return tv; }
    private Button tinyButton(String text) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(12); b.setPadding(dp(12), dp(6), dp(12), dp(6)); b.setMinHeight(0); b.setMinimumHeight(0); return b; }
    private Button actionButton(String text) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(13); return b; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private static class AppEntry {
        final String label; final String packageName;
        AppEntry(String label, String packageName) { this.label = label; this.packageName = packageName; }
    }
    private abstract static class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        public abstract void onItemSelected(int position);
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { onItemSelected(position); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }


    public static class ReverseGuideView extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float widthFactor = 1.00f;
        private float curveFactor = 0.00f;
        private float yOffsetFactor = 0.00f;

        public ReverseGuideView(Context context) {
            super(context);
            setWillNotDraw(false);

            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(dp(context, 4));
            linePaint.setStrokeCap(Paint.Cap.ROUND);

            fillPaint.setStyle(Paint.Style.FILL);

            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(dp(context, 13));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);

            centerPaint.setColor(Color.argb(190, 255, 255, 255));
            centerPaint.setStrokeWidth(dp(context, 2));
            centerPaint.setStyle(Paint.Style.STROKE);
        }

        public void setGuideParams(float widthFactor, float curveFactor, float yOffsetFactor) {
            this.widthFactor = Math.max(0.60f, Math.min(1.50f, widthFactor));
            this.curveFactor = Math.max(-1.00f, Math.min(1.00f, curveFactor));
            this.yOffsetFactor = Math.max(-1.00f, Math.min(1.00f, yOffsetFactor));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float yShift = yOffsetFactor * h * 0.16f;
            float bottom = clamp(h * 0.94f + yShift, h * 0.68f, h * 0.98f);
            float greenY = clamp(h * 0.72f + yShift, h * 0.34f, h * 0.93f);
            float yellowY = clamp(h * 0.56f + yShift, h * 0.26f, h * 0.88f);
            float redY = clamp(h * 0.40f + yShift, h * 0.18f, h * 0.80f);
            float topY = clamp(h * 0.26f + yShift, h * 0.10f, h * 0.70f);

            float center = w / 2f;
            float bottomHalf = w * 0.34f * widthFactor;
            float topHalf = w * 0.11f * widthFactor;
            float curve = curveFactor * w * 0.18f;

            // Safe parking zone
            Path safeZone = new Path();
            safeZone.moveTo(center - bottomHalf, bottom);
            safeZone.cubicTo(
                    center - bottomHalf + curve, bottom - (bottom - topY) * 0.34f,
                    center - topHalf + curve, topY + (bottom - topY) * 0.34f,
                    center - topHalf, topY
            );
            safeZone.lineTo(center + topHalf, topY);
            safeZone.cubicTo(
                    center + topHalf + curve, topY + (bottom - topY) * 0.34f,
                    center + bottomHalf + curve, bottom - (bottom - topY) * 0.34f,
                    center + bottomHalf, bottom
            );
            safeZone.close();

            fillPaint.setColor(Color.argb(34, 40, 255, 110));
            canvas.drawPath(safeZone, fillPaint);

            // Curved side guide rails
            linePaint.setColor(Color.argb(245, 50, 255, 110));
            Path leftRail = new Path();
            leftRail.moveTo(center - bottomHalf, bottom);
            leftRail.cubicTo(
                    center - bottomHalf + curve, bottom - (bottom - topY) * 0.34f,
                    center - topHalf + curve, topY + (bottom - topY) * 0.34f,
                    center - topHalf, topY
            );
            canvas.drawPath(leftRail, linePaint);

            Path rightRail = new Path();
            rightRail.moveTo(center + bottomHalf, bottom);
            rightRail.cubicTo(
                    center + bottomHalf + curve, bottom - (bottom - topY) * 0.34f,
                    center + topHalf + curve, topY + (bottom - topY) * 0.34f,
                    center + topHalf, topY
            );
            canvas.drawPath(rightRail, linePaint);

            // Distance bars
            drawDistanceLine(canvas, "2.0m", redY, center, bottomHalf, topHalf, bottom, topY, curve, Color.rgb(255, 70, 70));
            drawDistanceLine(canvas, "1.0m", yellowY, center, bottomHalf, topHalf, bottom, topY, curve, Color.rgb(255, 215, 55));
            drawDistanceLine(canvas, "0.5m", greenY, center, bottomHalf, topHalf, bottom, topY, curve, Color.rgb(70, 255, 120));

            // Center alignment line
            Path centerPath = new Path();
            centerPath.moveTo(center, bottom);
            centerPath.cubicTo(center + curve * 0.35f, bottom - (bottom - topY) * 0.35f,
                    center + curve * 0.35f, topY + (bottom - topY) * 0.35f,
                    center, topY);
            canvas.drawPath(centerPath, centerPaint);

            textPaint.setTextSize(dp(getContext(), 13));
            textPaint.setColor(Color.argb(235, 255, 255, 255));
            canvas.drawText("Camera lùi • Line chỉnh riêng cho xe", dp(getContext(), 14), dp(getContext(), 26), textPaint);
        }

        private void drawDistanceLine(Canvas canvas, String label, float y, float center, float bottomHalf, float topHalf, float bottom, float topY, float curve, int color) {
            float t = (bottom - y) / Math.max(1f, (bottom - topY));
            float half = bottomHalf + (topHalf - bottomHalf) * t;
            float xCurve = curve * (1f - Math.abs(0.50f - t) * 0.60f);

            linePaint.setColor(color);
            canvas.drawLine(center - half + xCurve, y, center + half + xCurve, y, linePaint);

            fillPaint.setColor(Color.argb(145, 0, 0, 0));
            RectF bg = new RectF(
                    center + half + xCurve + dp(getContext(), 8),
                    y - dp(getContext(), 14),
                    center + half + xCurve + dp(getContext(), 62),
                    y + dp(getContext(), 9)
            );
            canvas.drawRoundRect(bg, dp(getContext(), 6), dp(getContext(), 6), fillPaint);

            textPaint.setTextSize(dp(getContext(), 12));
            textPaint.setColor(Color.WHITE);
            canvas.drawText(label, bg.left + dp(getContext(), 7), y + dp(getContext(), 4), textPaint);
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private static int dp(Context context, int value) {
            return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
        }
    }

    public static class Car3DView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lanePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint carPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint carShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint windowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = 0.14f;
        private float speed = 0f;
        private float bearing = 0f;
        private String vehicleLabel = "VF 3";
        private int vehicleColor = Color.WHITE;

        public Car3DView(Context context) {
            super(context);
            lanePaint.setColor(Color.argb(120, 85, 140, 230));
            lanePaint.setStyle(Paint.Style.STROKE);
            lanePaint.setStrokeCap(Paint.Cap.ROUND);
            lanePaint.setStrokeWidth(dp(context, 10));
            glowPaint.setStyle(Paint.Style.FILL);
            darkPaint.setColor(Color.argb(200, 30, 38, 48));
            darkPaint.setStyle(Paint.Style.FILL);
            windowPaint.setColor(Color.argb(230, 228, 239, 247));
            windowPaint.setStyle(Paint.Style.FILL);
            wheelPaint.setColor(Color.rgb(25,30,35));
            wheelPaint.setStyle(Paint.Style.FILL);
            textPaint.setColor(Color.rgb(220,235,255));
            textPaint.setTextSize(dp(context, 12));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        void setVehicleLabel(String label) { this.vehicleLabel = label; invalidate(); }
        void setVehicleColor(int color) { this.vehicleColor = color; invalidate(); }
        void setBearing(float bearing) { this.bearing = bearing; invalidate(); }
        void setSpeed(float speed) { this.speed = speed; invalidate(); }
        void tick() { progress += speed <= 0 ? 0.004f : Math.min(0.035f, speed / 700f); if (progress > 0.90f) progress = 0.14f; invalidate(); }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(); int h = getHeight();
            bgPaint.setShader(new LinearGradient(0, 0, 0, h, Color.rgb(14,20,28), Color.rgb(30,42,58), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(0, 0, w, h), dp(getContext(), 18), dp(getContext(), 18), bgPaint);
            RectF road = new RectF(dp(getContext(), 16), h * 0.15f, w - dp(getContext(), 16), h * 0.87f);
            canvas.drawRoundRect(road, dp(getContext(), 24), dp(getContext(), 24), darkPaint);

            Path lane = new Path();
            lane.moveTo(road.left + road.width()*0.16f, road.bottom - road.height()*0.10f);
            lane.cubicTo(road.left + road.width()*0.30f, road.top + road.height()*0.72f,
                    road.left + road.width()*0.54f, road.top + road.height()*0.48f,
                    road.right - road.width()*0.14f, road.top + road.height()*0.18f);
            canvas.drawPath(lane, lanePaint);
            Paint mid = new Paint(Paint.ANTI_ALIAS_FLAG);
            mid.setColor(Color.argb(150, 255,255,255)); mid.setStrokeWidth(dp(getContext(), 3)); mid.setStyle(Paint.Style.STROKE);
            canvas.drawLine(road.left + road.width()*0.23f, road.bottom - road.height()*0.05f, road.right - road.width()*0.08f, road.top + road.height()*0.21f, mid);

            float x = road.left + road.width() * progress;
            float y = (float)(road.bottom - road.height()*(0.13 + progress*0.58) + Math.sin(progress*6.3f) * dp(getContext(), 10));
            glowPaint.setShader(new RadialGradient(x, y + dp(getContext(), 8), dp(getContext(), 28), Color.argb(130, 80,180,255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y + dp(getContext(), 8), dp(getContext(), 30), glowPaint);

            drawCar(canvas, x, y, bearing, vehicleColor);
            canvas.drawText(vehicleLabel + "  •  3D Dashboard", road.left, h - dp(getContext(), 16), textPaint);
        }

        private void drawCar(Canvas canvas, float cx, float cy, float bearing, int color) {
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate((bearing == 0f ? -8f : (bearing - 90f) * 0.16f));
            float scale = 1.12f;
            canvas.scale(scale, scale);

            carShadowPaint.setColor(Color.argb(90, 0,0,0));
            canvas.drawOval(new RectF(-34, 10, 34, 24), carShadowPaint);

            carPaint.setColor(color);
            Path body = new Path();
            body.moveTo(-44, 8);
            body.quadTo(-46, 3, -42, 0);
            body.lineTo(-24, -8);
            body.quadTo(-10, -22, 12, -21);
            body.lineTo(28, -17);
            body.quadTo(38, -13, 43, -4);
            body.lineTo(46, 4);
            body.quadTo(41, 9, 32, 9);
            body.lineTo(-44, 8);
            body.close();
            canvas.drawPath(body, carPaint);

            Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
            highlight.setColor(adjustBrightness(color, 1.18f));
            highlight.setStyle(Paint.Style.FILL);
            Path roof = new Path();
            roof.moveTo(-18, -8);
            roof.lineTo(12, -8);
            roof.lineTo(23, -2);
            roof.lineTo(-7, -2);
            roof.close();
            canvas.drawPath(roof, highlight);

            Path windows = new Path();
            windows.moveTo(-16, -7);
            windows.lineTo(11, -7);
            windows.lineTo(20, -2);
            windows.lineTo(-5, -2);
            windows.close();
            canvas.drawPath(windows, windowPaint);
            canvas.drawLine(2, -7, -1, -2, wheelPaint);

            Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
            rim.setColor(Color.rgb(230,230,230)); rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(2f);
            canvas.drawCircle(-25, 8, 9, wheelPaint); canvas.drawCircle(25, 8, 9, wheelPaint);
            canvas.drawCircle(-25, 8, 5, rim); canvas.drawCircle(25, 8, 5, rim);

            Paint lamp = new Paint(Paint.ANTI_ALIAS_FLAG); lamp.setColor(Color.argb(230,255,255,255));
            canvas.drawOval(new RectF(-42, 0, -33, 4), lamp);
            Paint rearLamp = new Paint(Paint.ANTI_ALIAS_FLAG); rearLamp.setColor(Color.argb(220,255,90,90));
            canvas.drawOval(new RectF(35, 0, 43, 4), rearLamp);
            canvas.restore();
        }

        private int adjustBrightness(int color, float factor) {
            int r = Math.min(255, Math.max(0, (int) (Color.red(color) * factor)));
            int g = Math.min(255, Math.max(0, (int) (Color.green(color) * factor)));
            int b = Math.min(255, Math.max(0, (int) (Color.blue(color) * factor)));
            return Color.rgb(r,g,b);
        }

        private static int dp(Context context, int value) { return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f); }
    }
}
