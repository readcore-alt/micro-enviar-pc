package com.micro.enviarpc;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private EditText txtIp, txtMensaje;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("config", MODE_PRIVATE);

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) enviarFoto(uri); else finish();
            return;
        }

        setContentView(R.layout.activity_main);
        txtIp = findViewById(R.id.txtIp);
        txtMensaje = findViewById(R.id.txtMensaje);
        Button btnGuardarIp = findViewById(R.id.btnGuardarIp);
        Button btnEnviarTexto = findViewById(R.id.btnEnviarTexto);

        txtIp.setText(prefs.getString("pc_ip", ""));

        btnGuardarIp.setOnClickListener(v -> {
            String ip = txtIp.getText().toString().trim();
            prefs.edit().putString("pc_ip", ip).apply();
            Toast.makeText(this, "IP guardada", Toast.LENGTH_SHORT).show();
        });

        btnEnviarTexto.setOnClickListener(v -> {
            String texto = txtMensaje.getText().toString();
            if (!texto.isEmpty()) {
                enviarTexto(texto);
            }
        });
    }

    private void enviarTexto(String texto) {
        String ip = prefs.getString("pc_ip", "");
        if (ip.isEmpty()) {
            Toast.makeText(this, "Guarda la IP primero", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL("http://" + ip + ":8080/texto");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(2000);

                OutputStream out = conn.getOutputStream();
                out.write(texto.getBytes("UTF-8"));
                out.flush();
                out.close();

                if (conn.getResponseCode() == 200) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "✓ Escrito en PC", Toast.LENGTH_SHORT).show();
                        txtMensaje.setText("");
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error al conectar con la PC", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void enviarFoto(Uri uri) {
        String ip = prefs.getString("pc_ip", "");
        new Thread(() -> {
            try {
                URL url = new URL("http://" + ip + ":8080/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);

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
