package com.example.hunurgencias;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int BLUE = Color.rgb(11, 110, 153);
    private static final int TEAL = Color.rgb(31, 143, 131);
    private static final int BG = Color.rgb(245, 248, 250);
    private static final int TEXT = Color.rgb(26, 39, 52);
    private static final int MUTED = Color.rgb(91, 104, 116);
    private static final int GREEN = Color.rgb(21, 128, 61);
    private static final int RED = Color.rgb(190, 40, 40);
    private static final int ORANGE = Color.rgb(196, 120, 0);

    private EditText patientInput;
    private TextView statusText;
    private TextView connectionText;
    private TextView lastCheckText;
    private TextView lastAlertText;
    private Spinner intervalSpinner;
    private SharedPreferences prefs;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            uiHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("hun_prefs", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(20), dp(20), dp(20));
        header.setGravity(Gravity.CENTER);
        header.setBackground(rounded(BLUE, 22));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView cross = new TextView(this);
        cross.setText("+");
        cross.setTextSize(42);
        cross.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cross.setTextColor(Color.WHITE);
        cross.setGravity(Gravity.CENTER);
        header.addView(cross, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView title = new TextView(this);
        title.setText("Aviso HUN");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Te avisamos cuando aparezca tu código de paciente.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(225, 242, 248));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.setMargins(0, dp(6), 0, 0);
        header.addView(subtitle, subtitleParams);

        LinearLayout controlCard = card();
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(-1, -2);
        controlParams.setMargins(0, dp(16), 0, 0);
        root.addView(controlCard, controlParams);

        TextView codeLabel = label("Código de paciente");
        controlCard.addView(codeLabel);

        patientInput = new EditText(this);
        patientInput.setHint("Ejemplo: JC580");
        patientInput.setSingleLine(true);
        patientInput.setTextSize(24);
        patientInput.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        patientInput.setTextColor(TEXT);
        patientInput.setHintTextColor(Color.rgb(145, 155, 164));
        patientInput.setGravity(Gravity.CENTER);
        patientInput.setAllCaps(true);
        patientInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        patientInput.setText(prefs.getString("patient", ""));
        patientInput.setPadding(dp(14), 0, dp(14), 0);
        patientInput.setBackground(roundedStroke(Color.WHITE, Color.rgb(204, 215, 223), 14));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(62));
        inputParams.setMargins(0, dp(8), 0, dp(14));
        controlCard.addView(patientInput, inputParams);

        TextView intervalLabel = label("Comprobar cada");
        controlCard.addView(intervalLabel);

        intervalSpinner = new Spinner(this);
        String[] options = {"15 segundos", "30 segundos", "60 segundos"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options);
        intervalSpinner.setAdapter(adapter);
        int savedInterval = prefs.getInt("interval_seconds", 15);
        intervalSpinner.setSelection(savedInterval == 60 ? 2 : savedInterval == 30 ? 1 : 0);
        intervalSpinner.setBackground(roundedStroke(Color.WHITE, Color.rgb(204, 215, 223), 14));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(-1, dp(54));
        spinnerParams.setMargins(0, dp(8), 0, dp(16));
        controlCard.addView(intervalSpinner, spinnerParams);

        Button startButton = actionButton("INICIAR VIGILANCIA", TEAL, Color.WHITE);
        controlCard.addView(startButton, buttonParams());

        Button stopButton = actionButton("DETENER", Color.rgb(230, 236, 240), TEXT);
        LinearLayout.LayoutParams stopParams = buttonParams();
        stopParams.setMargins(0, dp(8), 0, 0);
        controlCard.addView(stopButton, stopParams);

        Button testButton = actionButton("PROBAR NOTIFICACIÓN", Color.WHITE, BLUE);
        GradientDrawable testBg = roundedStroke(Color.WHITE, Color.rgb(170, 199, 213), 14);
        testButton.setBackground(testBg);
        LinearLayout.LayoutParams testParams = buttonParams();
        testParams.setMargins(0, dp(8), 0, 0);
        controlCard.addView(testButton, testParams);

        LinearLayout statusCard = card();
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.setMargins(0, dp(14), 0, 0);
        root.addView(statusCard, statusParams);

        TextView statusTitle = label("Estado");
        statusTitle.setTextSize(17);
        statusTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusCard.addView(statusTitle);

        statusText = statusLine();
        connectionText = statusLine();
        lastCheckText = statusLine();
        lastAlertText = statusLine();

        statusCard.addView(statusText);
        statusCard.addView(connectionText);
        statusCard.addView(lastCheckText);
        statusCard.addView(lastAlertText);

        TextView note = new TextView(this);
        note.setText("Mientras la vigilancia esté activa verás una notificación permanente. Puedes cerrar esta pantalla sin detenerla.");
        note.setTextSize(12);
        note.setTextColor(MUTED);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(8), dp(18), dp(8), 0);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);

        requestNotificationPermissionIfNeeded();
        createTestChannel();
        updateStatus();

        startButton.setOnClickListener(v -> startMonitoring());
        stopButton.setOnClickListener(v -> stopMonitoring());
        testButton.setOnClickListener(v -> showTestNotification());
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(Color.WHITE, 20));
        card.setElevation(dp(2));
        return card;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(MUTED);
        return t;
    }

    private TextView statusLine() {
        TextView t = new TextView(this);
        t.setTextSize(15);
        t.setTextColor(TEXT);
        t.setPadding(0, dp(8), 0, dp(2));
        return t;
    }

    private Button actionButton(String text, int background, int foreground) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(foreground);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(background, 14));
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        return new LinearLayout.LayoutParams(-1, dp(56));
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable roundedStroke(int fill, int stroke, int radiusDp) {
        GradientDrawable g = rounded(fill, radiusDp);
        g.setStroke(dp(1), stroke);
        return g;
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiHandler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        uiHandler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    private void startMonitoring() {
        String patient = patientInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        if (patient.isEmpty()) {
            Toast.makeText(this, "Introduce tu código de paciente", Toast.LENGTH_SHORT).show();
            return;
        }

        int interval = intervalSpinner.getSelectedItemPosition() == 2 ? 60 :
                intervalSpinner.getSelectedItemPosition() == 1 ? 30 : 15;

        prefs.edit()
                .putString("patient", patient)
                .putInt("interval_seconds", interval)
                .putBoolean("monitoring", true)
                .apply();

        Intent intent = new Intent(this, MonitoringService.class);
        intent.putExtra("patient", patient);
        intent.putExtra("interval_seconds", interval);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        Toast.makeText(this, "Vigilancia iniciada", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void stopMonitoring() {
        stopService(new Intent(this, MonitoringService.class));
        prefs.edit().putBoolean("monitoring", false).apply();
        Toast.makeText(this, "Vigilancia detenida", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void updateStatus() {
        boolean monitoring = prefs.getBoolean("monitoring", false);
        String patient = prefs.getString("patient", "");
        int interval = prefs.getInt("interval_seconds", 15);

        if (monitoring && !patient.isEmpty()) {
            statusText.setText("● Vigilancia activa: " + patient + " · cada " + interval + " s");
            statusText.setTextColor(ORANGE);
        } else {
            statusText.setText("● Vigilancia detenida");
            statusText.setTextColor(MUTED);
        }

        long lastCheck = prefs.getLong("last_check_ms", 0L);
        String checkResult = prefs.getString("last_check_result", "");

        if ("OK".equals(checkResult)) {
            connectionText.setText("● Conexión: OK");
            connectionText.setTextColor(GREEN);
        } else if (!checkResult.isEmpty()) {
            connectionText.setText("● Conexión: " + checkResult);
            connectionText.setTextColor(RED);
        } else {
            connectionText.setText("● Conexión: esperando primera comprobación");
            connectionText.setTextColor(MUTED);
        }

        if (lastCheck > 0) {
            lastCheckText.setText("Última comprobación: " + formatTime(lastCheck));
        } else {
            lastCheckText.setText("Última comprobación: —");
        }

        String lastAlert = prefs.getString("last_alert_text", "");
        if (lastAlert.isEmpty()) {
            lastAlertText.setText("Último aviso: —");
            lastAlertText.setTextColor(MUTED);
        } else {
            lastAlertText.setText("Último aviso: " + lastAlert);
            lastAlertText.setTextColor(TEXT);
        }
    }

    private String formatTime(long millis) {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(millis));
    }

    private void createTestChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "patient_alert",
                    "Aviso de llamada",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.enableVibration(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void showTestNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionIfNeeded();
            return;
        }

        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 77, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, "patient_alert")
                .setSmallIcon(R.drawable.ic_stat_hun)
                .setContentTitle("Notificación de prueba")
                .setContentText("Aviso HUN funciona correctamente")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        getSystemService(NotificationManager.class).notify(3077, notification);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
