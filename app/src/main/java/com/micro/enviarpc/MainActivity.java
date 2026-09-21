package com.micro.enviarpc;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private EditText txtIp, txtMensaje, txtTecladoEnVivo;
    private CheckBox chkConfirmar;
    private LinearLayout cardConfirmacion;
    private TextView txtNombreArchivo, txtTamanoArchivo;
    private Uri archivoPendiente = null;
    private boolean ignorarCambio = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("config", MODE_PRIVATE);
        Intent intent = getIntent();
        boolean pedirConfirmacion = prefs.getBoolean("pedir_confirmacion", false);

        // Si se abrió para compartir un archivo
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                if (!pedirConfirmacion) {
                    super.onCreate(savedInstanceState);
                    enviarArchivo(uri, true);
                    return;
                }
            }
        }

        // Si vamos a mostrar interfaz, activamos el tema moderno oscuro
        setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);
        super.onCreate(savedInstanceState);
        inicializarUI();

        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) mostrarTarjetaArchivo(uri);
        }
    }

    private void inicializarUI() {
        setContentView(R.layout.activity_main);
        txtIp = findViewById(R.id.txtIp);
        txtMensaje = findViewById(R.id.txtMensaje);
        txtTecladoEnVivo = findViewById(R.id.txtTecladoEnVivo);
        chkConfirmar = findViewById(R.id.chkConfirmar);
        cardConfirmacion = findViewById(R.id.cardConfirmacion);
        txtNombreArchivo = findViewById(R.id.txtNombreArchivo);
        txtTamanoArchivo = findViewById(R.id.txtTamanoArchivo);

        Button btnGuardarIp = findViewById(R.id.btnGuardarIp);
        Button btnEnviarTexto = findViewById(R.id.btnEnviarTexto);
        Button btnSeleccionarArchivo = findViewById(R.id.btnSeleccionarArchivo);
        Button btnConfirmarEnvio = findViewById(R.id.btnConfirmarEnvio);
        Button btnCancelarEnvio = findViewById(R.id.btnCancelarEnvio);

        txtIp.setText(prefs.getString("pc_ip", ""));
        chkConfirmar.setChecked(prefs.getBoolean("pedir_confirmacion", false));

        chkConfirmar.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.edit().putBoolean("pedir_confirmacion", isChecked).apply();
        });

        btnGuardarIp.setOnClickListener(v -> {
            prefs.edit().putString("pc_ip", txtIp.getText().toString().trim()).apply();
            Toast.makeText(this, "IP guardada", Toast.LENGTH_SHORT).show();
        });

        btnEnviarTexto.setOnClickListener(v -> {
            String texto = txtMensaje.getText().toString();
            if (!texto.isEmpty()) enviarTextoBloque(texto);
        });

        btnSeleccionarArchivo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, 100);
        });

        btnConfirmarEnvio.setOnClickListener(v -> {
            if (archivoPendiente != null) enviarArchivo(archivoPendiente, false);
        });

        btnCancelarEnvio.setOnClickListener(v -> {
            archivoPendiente = null;
            cardConfirmacion.setVisibility(View.GONE);
            if (getIntent().getAction() != null && getIntent().getAction().equals(Intent.ACTION_SEND)) {
                finish();
            }
        });

        // Logica para Teclado en Vivo con soporte de BORRAR
        txtTecladoEnVivo.setText(" ");
        txtTecladoEnVivo.setSelection(1);
        txtTecladoEnVivo.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (ignorarCambio) return;
                String actual = s.toString();

                if (actual.isEmpty()) {
                    // El usuario presionó BORRAR (Backspace)
                    enviarTecla("__BACKSPACE__");
                    resetearTecladoEnVivo();
                } else if (actual.length() > 1) {
                    // El usuario escribió un caracter
                    String nuevaLetra = actual.substring(1);
                    enviarTecla(nuevaLetra);
                    resetearTecladoEnVivo();
                }
            }
        });
    }

    private void resetearTecladoEnVivo() {
        ignorarCambio = true;
        txtTecladoEnVivo.setText(" ");
        txtTecladoEnVivo.setSelection(1);
        ignorarCambio = false;
    }

    private void enviarTecla(String tecla) {
        String ip = prefs.getString("pc_ip", "");
        if (ip.isEmpty()) return;
        new Thread(() -> {
            try {
                URL url = new URL("http://" + ip + ":8080/tecla");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(1000);
                OutputStream out = conn.getOutputStream();
                out.write(tecla.getBytes("UTF-8"));
                out.flush();
                out.close();
                conn.getResponseCode();
            } catch (Exception ignored) {}
        }).start();
    }

    private void enviarTextoBloque(String texto) {
        String ip = prefs.getString("pc_ip", "");
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
            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            mostrarTarjetaArchivo(data.getData());
        }
    }

    private void mostrarTarjetaArchivo(Uri uri) {
        archivoPendiente = uri;
        String nombre = obtenerNombre(uri);
        long tamano = obtenerTamano(uri);
        txtNombreArchivo.setText(nombre);
        txtTamanoArchivo.setText("Peso: " + formatearTamano(tamano));
        cardConfirmacion.setVisibility(View.VISIBLE);
    }

    private void enviarArchivo(Uri uri, boolean cerrarAlFinal) {
        String ip = prefs.getString("pc_ip", "");
        if (ip.isEmpty()) {
            Toast.makeText(this, "Configura la IP de la PC primero", Toast.LENGTH_LONG).show();
            return;
        }

        String nombreArchivo = obtenerNombre(uri);

        new Thread(() -> {
            InputStream in = null;
            OutputStream out = null;
            HttpURLConnection conn = null;
            try {
                in = getContentResolver().openInputStream(uri);
                if (in == null) throw new Exception("No se pudo leer el archivo");

                URL url = new URL("http://" + ip + ":8080/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(4000);
                conn.setRequestProperty("X-Filename", URLEncoder.encode(nombreArchivo, "UTF-8"));

                out = conn.getOutputStream();
                byte[] b = new byte[8192];
                int len;
                while ((len = in.read(b)) != -1) out.write(b, 0, len);
                out.flush();

                if (conn.getResponseCode() == 200) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "✓ Enviado: " + nombreArchivo, Toast.LENGTH_SHORT).show();
                        if (cardConfirmacion != null) cardConfirmacion.setVisibility(View.GONE);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error al enviar", Toast.LENGTH_SHORT).show());
            } finally {
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
                if (cerrarAlFinal) runOnUiThread(this::finish);
            }
        }).start();
    }

    private String obtenerNombre(Uri uri) {
        String nombre = "archivo";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx != -1) nombre = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return nombre;
    }

    private long obtenerTamano(Uri uri) {
        long tamano = 0;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx != -1) tamano = cursor.getLong(idx);
            }
        } catch (Exception ignored) {}
        return tamano;
    }

    private String formatearTamano(long bytes) {
        if (bytes <= 0) return "Desconocido";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
