package com.micro.enviarpc;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("config", MODE_PRIVATE);
        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) enviarImagen(uri); else finish();
        } else {
            pedirIpDialogo();
        }
    }
    private void pedirIpDialogo() {
        String ipActual = prefs.getString("pc_ip", "192.168.1.XX");
        EditText input = new EditText(this);
        input.setText(ipActual);
        new AlertDialog.Builder(this)
            .setTitle("IP de tu PC")
            .setMessage("Ejemplo: 192.168.1.15")
            .setView(input)
            .setPositiveButton("Guardar", (d, w) -> {
                prefs.edit().putString("pc_ip", input.getText().toString().trim()).apply();
                Toast.makeText(this, "IP guardada", Toast.LENGTH_SHORT).show();
                finish();
            })
            .setNegativeButton("Cancelar", (d, w) -> finish()).show();
    }
    private void enviarImagen(Uri uri) {
        String ip = prefs.getString("pc_ip", null);
        if (ip == null || ip.contains("XX")) {
            Toast.makeText(this, "Abre la app primero para configurar la IP", Toast.LENGTH_LONG).show();
            pedirIpDialogo();
            return;
        }
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("http://" + ip + ":8080/").openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                InputStream in = getContentResolver().openInputStream(uri);
                OutputStream out = conn.getOutputStream();
                byte[] b = new byte[8192];
                int len;
                while ((len = in.read(b)) != -1) out.write(b, 0, len);
                out.flush(); out.close(); in.close();
                if (conn.getResponseCode() == 200) {
                    runOnUiThread(() -> Toast.makeText(this, "✓ Enviada a la PC", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error de conexion", Toast.LENGTH_SHORT).show());
            } finally {
                runOnUiThread(this::finish);
            }
        }).start();
    }
}
