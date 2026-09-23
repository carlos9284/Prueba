package com.example.hunurgencias;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MonitoringService extends Service {

    private static final String ENDPOINT = "https://sigue.navarra.es/GN.Sanidad.Sigue.Movil.WebUI/seguimiento/avisos/2";
    private static final String CHANNEL_MONITOR = "monitoring";
    private static final String CHANNEL_ALERT = "patient_alert";
    private static final int FOREGROUND_ID = 2001;

    private ScheduledExecutorService scheduler;
    private String patient;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("hun_prefs", MODE_PRIVATE);
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        patient = intent != null ? intent.getStringExtra("patient") : null;
        if (patient == null || patient.trim().isEmpty()) {
            patient = prefs.getString("patient", "");
        }
        patient = patient.trim().toUpperCase(Locale.ROOT);

        int interval = intent != null ? intent.getIntExtra("interval_seconds", -1) : -1;
        if (interval <= 0) interval = prefs.getInt("interval_seconds", 15);
        if (interval != 15 && interval != 30 && interval != 60) interval = 15;

        if (patient.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        prefs.edit()
                .putString("patient", patient)
                .putInt("interval_seconds", interval)
                .putBoolean("monitoring", true)
                .apply();

        startForeground(FOREGROUND_ID, buildMonitoringNotification(patient, interval));

        if (scheduler != null) scheduler.shutdownNow();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        final int pollSeconds = interval;
        scheduler.scheduleWithFixedDelay(this::checkEndpoint, 0, pollSeconds, TimeUnit.SECONDS);

        return START_STICKY;
    }

    private void checkEndpoint() {
        HttpURLConnection connection = null;
        long now = System.currentTimeMillis();

        try {
            URL url = new URL(ENDPOINT + "?_=" + now);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("User-Agent", "AvisoHUN/1.1 Android");

            int code = connection.getResponseCode();
            if (code != 200) {
                saveCheckStatus(now, "HTTP " + code);
                return;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            reader.close();

            JSONArray array = new JSONArray(body.toString());
            saveCheckStatus(now, "OK");

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                String currentPatient = item.optString("Paciente", "").trim().toUpperCase(Locale.ROOT);
                if (!patient.equals(currentPatient)) continue;

                String fecha = String.valueOf(item.opt("Fecha"));
                String lastKey = "last_fecha_" + patient;
                String lastFecha = prefs.getString(lastKey, "");

                String location = item.optString("Ubicacion", "");
                String description = item.optString("Descripcion", "");

                String alertSummary = patient +
                        (location.isEmpty() ? "" : " · " + location) +
                        " · " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

                prefs.edit().putString("last_alert_text", alertSummary).apply();

                if (fecha.equals(lastFecha)) return;

                prefs.edit().putString(lastKey, fecha).apply();
                showPatientNotification(patient, location, description);
                return;
            }

        } catch (Exception e) {
            saveCheckStatus(now, "Error de conexión");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void saveCheckStatus(long millis, String result) {
        prefs.edit()
                .putLong("last_check_ms", millis)
                .putString("last_check_result", result)
                .apply();
    }

    private Notification buildMonitoringNotification(String patient, int interval) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_MONITOR)
                .setSmallIcon(R.drawable.ic_stat_hun)
                .setContentTitle("Aviso HUN")
                .setContentText("Vigilando " + patient + " cada " + interval + " s")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void showPatientNotification(String patient, String location, String description) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 1, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = location.isEmpty() ? description : location;
        Notification notification = new Notification.Builder(this, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_stat_hun)
                .setContentTitle("Te están llamando: " + patient)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(description.isEmpty() ? text : description))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        getSystemService(NotificationManager.class).notify(3001, notification);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            NotificationChannel monitor = new NotificationChannel(
                    CHANNEL_MONITOR,
                    "Vigilancia activa",
                    NotificationManager.IMPORTANCE_LOW);
            monitor.setDescription("Indica que Aviso HUN está comprobando tu código.");

            NotificationChannel alert = new NotificationChannel(
                    CHANNEL_ALERT,
                    "Aviso de llamada",
                    NotificationManager.IMPORTANCE_DEFAULT);
            alert.setDescription("Aviso normal cuando aparece tu código de paciente.");
            alert.enableVibration(false);

            nm.createNotificationChannel(monitor);
            nm.createNotificationChannel(alert);
        }
    }

    @Override
    public void onDestroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        prefs.edit().putBoolean("monitoring", false).apply();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
