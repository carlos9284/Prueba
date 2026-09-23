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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Aviso HUN");
        title.setTextSize(30);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Vigila tu código de paciente y recibe una notificación cuando te llamen.");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.setMargins(0, dp(10), 0, dp(20));
        root.addView(subtitle, subParams);

        patientInput = new EditText(this);
        patientInput.setHint("Ejemplo: JC580");
        patientInput.setSingleLine(true);
        patientInput.setTextSize(22);
        patientInput.setGravity(Gravity.CENTER);
        patientInput.setAllCaps(true);
        patientInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        patientInput.setText(prefs.getString("patient", ""));
        root.addView(patientInput, new LinearLayout.LayoutParams(-1, dp(62)));

        TextView intervalLabel = new TextView(this);
        intervalLabel.setText("Comprobar cada:");
        intervalLabel.setTextSize(14);
        intervalLabel.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.setMargins(0, dp(16), 0, dp(4));
        root.addView(intervalLabel, labelParams);

        intervalSpinner = new Spinner(this);
        String[] options = {"15 segundos", "30 segundos", "60 segundos"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options);
        intervalSpinner.setAdapter(adapter);
        int savedInterval = prefs.getInt("interval_seconds", 15);
        intervalSpinner.setSelection(savedInterval == 60 ? 2 : savedInterval == 30 ? 1 : 0);
        root.addView(intervalSpinner, new LinearLayout.LayoutParams(-1, dp(52)));

        Button startButton = new Button(this);
        startButton.setText("INICIAR VIGILANCIA");
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(-1, dp(58));
        startParams.setMargins(0, dp(16), 0, dp(8));
        root.addView(startButton, startParams);

        Button stopButton = new Button(this);
        stopButton.setText("DETENER");
        root.addView(stopButton, new LinearLayout.LayoutParams(-1, dp(52)));

        Button testButton = new Button(this);
        testButton.setText("PROBAR NOTIFICACIÓN");
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(-1, dp(52));
        testParams.setMargins(0, dp(8), 0, 0);
        root.addView(testButton, testParams);

        statusText = createInfoText();
        connectionText = createInfoText();
        lastCheckText = createInfoText();
        lastAlertText = createInfoText();

        LinearLayout.LayoutParams infoParams1 = new LinearLayout.LayoutParams(-1, -2);
        infoParams1.setMargins(0, dp(22), 0, 0);
        root.addView(statusText, infoParams1);
        root.addView(connectionText, new LinearLayout.LayoutParams(-1, -2));
        root.addView(lastCheckText, new LinearLayout.LayoutParams(-1, -2));
        root.addView(lastAlertText, new LinearLayout.LayoutParams(-1, -2));

        TextView note = new TextView(this);
        note.setText("La vigilancia funciona mediante una notificación permanente mientras está activa.");
        note.setTextSize(12);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.setMargins(0, dp(18), 0, 0);
        root.addView(note, noteParams);

        setContentView(root);

        requestNotificationPermissionIfNeeded();
        createTestChannel();
        updateStatus();

        startButton.setOnClickListener(v -> startMonitoring());
        stopButton.setOnClickListener(v -> stopMonitoring());
        testButton.setOnClickListener(v -> showTestNotification());
    }

    private TextView createInfoText() {
        TextView t = new TextView(this);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, dp(5), 0, dp(5));
        return t;
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
        updateStatus();
    }

    private void updateStatus() {
        boolean monitoring = prefs.getBoolean("monitoring", false);
        String patient = prefs.getString("patient", "");
        int interval = prefs.getInt("interval_seconds", 15);

        if (monitoring && !patient.isEmpty()) {
            statusText.setText("Estado: Vigilando " + patient + " cada " + interval + " s");
            statusText.setTextColor(Color.rgb(196, 120, 0));
        } else {
            statusText.setText("Estado: Vigilancia detenida");
            statusText.setTextColor(Color.DKGRAY);
        }

        long lastCheck = prefs.getLong("last_check_ms", 0L);
        String checkResult = prefs.getString("last_check_result", "");
        if ("OK".equals(checkResult)) {
            connectionText.setText("Conexion: OK");
            connectionText.setTextColor(Color.rgb(0, 128, 0));
        } else if (!checkResult.isEmpty()) {
            connectionText.setText("Conexion: " + checkResult);
            connectionText.setTextColor(Color.rgb(190, 0, 0));
        } else {
            connectionText.setText("Conexion: esperando primera comprobacion");
            connectionText.setTextColor(Color.DKGRAY);
        }

        if (lastCheck > 0) {
            lastCheckText.setText("Última comprobación: " + formatTime(lastCheck) +
                    (checkResult.isEmpty() ? "" : " · " + checkResult));
        } else {
            lastCheckText.setText("Última comprobación: —");
        }

        String lastAlert = prefs.getString("last_alert_text", "");
        if (lastAlert.isEmpty()) {
            lastAlertText.setText("Ultimo aviso: -");
            lastAlertText.setTextColor(Color.DKGRAY);
        } else {
            lastAlertText.setText("Ultimo aviso: " + lastAlert);
            lastAlertText.setTextColor(Color.BLACK);
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
                .setSmallIcon(android.R.drawable.ic_dialog_info)
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
