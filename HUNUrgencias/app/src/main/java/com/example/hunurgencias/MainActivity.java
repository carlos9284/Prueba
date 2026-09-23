package com.example.hunurgencias;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private EditText patientInput;
    private TextView statusText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("hun_prefs", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("HUN Urgencias");
        title.setTextSize(28);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Introduce tu código de paciente y la app vigilará los avisos.");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.setMargins(0, dp(12), 0, dp(24));
        root.addView(subtitle, subParams);

        patientInput = new EditText(this);
        patientInput.setHint("Ejemplo: LV924");
        patientInput.setSingleLine(true);
        patientInput.setTextSize(22);
        patientInput.setGravity(Gravity.CENTER);
        patientInput.setAllCaps(true);
        patientInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        patientInput.setText(prefs.getString("patient", ""));
        root.addView(patientInput, new LinearLayout.LayoutParams(-1, dp(64)));

        Button startButton = new Button(this);
        startButton.setText("INICIAR VIGILANCIA");
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(-1, dp(58));
        startParams.setMargins(0, dp(20), 0, dp(10));
        root.addView(startButton, startParams);

        Button stopButton = new Button(this);
        stopButton.setText("DETENER");
        root.addView(stopButton, new LinearLayout.LayoutParams(-1, dp(54)));

        statusText = new TextView(this);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.setMargins(0, dp(26), 0, 0);
        root.addView(statusText, statusParams);

        TextView note = new TextView(this);
        note.setText("La app consulta periódicamente la lista pública de avisos. Mantén activa la vigilancia mientras esperas.");
        note.setTextSize(13);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.setMargins(0, dp(20), 0, 0);
        root.addView(note, noteParams);

        setContentView(root);

        updateStatus();
        requestNotificationPermissionIfNeeded();

        startButton.setOnClickListener(v -> startMonitoring());
        stopButton.setOnClickListener(v -> stopMonitoring());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusText != null) updateStatus();
    }

    private void startMonitoring() {
        String patient = patientInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        if (patient.isEmpty()) {
            Toast.makeText(this, "Introduce tu código de paciente", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit().putString("patient", patient).putBoolean("monitoring", true).apply();

        Intent intent = new Intent(this, MonitoringService.class);
        intent.putExtra("patient", patient);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        statusText.setText("Vigilando " + patient + "…");
        Toast.makeText(this, "Vigilancia iniciada", Toast.LENGTH_SHORT).show();
    }

    private void stopMonitoring() {
        stopService(new Intent(this, MonitoringService.class));
        prefs.edit().putBoolean("monitoring", false).apply();
        statusText.setText("Vigilancia detenida");
    }

    private void updateStatus() {
        boolean monitoring = prefs.getBoolean("monitoring", false);
        String patient = prefs.getString("patient", "");
        if (monitoring && !patient.isEmpty()) {
            statusText.setText("Vigilando " + patient + "…");
        } else {
            statusText.setText("Vigilancia detenida");
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
