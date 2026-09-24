package com.micro.enviarpc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private EditText txtIp, txtMensaje, txtTecladoEnVivo;
    private CheckBox chkConfirmar;
    private LinearLayout cardConfirmacion;
    private TextView txtNombreArchivo, txtTamanoArchivo;
    private Button btnConfirmarEnvio, btnCancelarEnvio, btnConectar;
    private Uri archivoPendiente = null;
    private boolean ignorarCambio = false;
    private DatagramSocket udpSocket = null;

    // Servidor local para recibir de la PC
    private ServerSocket serverSocketRecibir = null;
    private boolean conectadoConPC = false;
    private static final int PUERTO_ESCUCHA = 8082;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("config", MODE_PRIVATE);
        Intent intent = getIntent();
        boolean pedirConfirmacion = prefs.getBoolean("pedir_confirmacion", false);

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
        btnConfirmarEnvio = findViewById(R.id.btnConfirmarEnvio);
        btnCancelarEnvio = findViewById(R.id.btnCancelarEnvio);

        // Inyectar el botón 'Conectar' sutilmente junto a Guardar IP
        btnConectar = new Button(this);
        btnConectar.setText("Conectar");
        btnConectar.setTextColor(Color.WHITE);
        btnConectar.setBackgroundColor(Color.parseColor("#334155"));
        if (btnGuardarIp.getParent() instanceof ViewGroup) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(10, 0, 0, 0);
            btnConectar.setLayoutParams(lp);
            ((ViewGroup) btnGuardarIp.getParent()).addView(btnConectar);
        }

        txtIp.setText(prefs.getString("pc_ip", ""));
        chkConfirmar.setChecked(prefs.getBoolean("pedir_confirmacion", false));

        chkConfirmar.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.edit().putBoolean("pedir_confirmacion", isChecked).apply();
        });

        btnGuardarIp.setOnClickListener(v -> {
            prefs.edit().putString("pc_ip", txtIp.getText().toString().trim()).apply();
            Toast.makeText(this, "IP guardada", Toast.LENGTH_SHORT).show();
        });

        btnConectar.setOnClickListener(v -> toggleConexionPC());

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

        // Crear socket UDP reutilizable
        new Thread(() -> {
            try { udpSocket = new DatagramSocket(); } catch (Exception ignored) {}
        }).start();

        // Lógica Teclado en Vivo
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
                    enviarTeclaUDP("__BACKSPACE__");
                    resetearTeclado();
                } else if (actual.length() > 1) {
                    String nuevaLetra = actual.substring(1);
                    enviarTeclaUDP(nuevaLetra);
                    resetearTeclado();
                }
            }
        });
    }

    private void resetearTeclado() {
        ignorarCambio = true;
        txtTecladoEnVivo.setText(" ");
        txtTecladoEnVivo.setSelection(1);
        ignorarCambio = false;
    }

    // --- CONEXIÓN BIDIRECCIONAL CON LA PC ---
    private void toggleConexionPC() {
        if (conectadoConPC) {
            desconectarServidorLocal();
            return;
        }

        String ip = prefs.getString("pc_ip", "");
        if (ip.isEmpty()) {
            Toast.makeText(this, "Guarda la IP de la PC primero", Toast.LENGTH_SHORT).show();
            return;
        }

        btnConectar.setEnabled(false);
        btnConectar.setText("Solicitando...");

        new Thread(() -> {
            try {
                URL url = new URL("http://" + ip + ":8080/conectar");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(25000);
                conn.setReadTimeout(25000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    runOnUiThread(() -> {
                        conectadoConPC = true;
                        btnConectar.setText("● Conectado");
                        btnConectar.setBackgroundColor(Color.parseColor("#10B981"));
                        btnConectar.setEnabled(true);
                        Toast.makeText(this, "✓ Vinculado con la PC", Toast.LENGTH_SHORT).show();
                    });
                    iniciarServidorLocal();
                } else {
                    runOnUiThread(() -> {
                        btnConectar.setText("Conectar");
                        btnConectar.setBackgroundColor(Color.parseColor("#334155"));
                        btnConectar.setEnabled(true);
                        Toast.makeText(this, "Conexión rechazada en PC", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnConectar.setText("Conectar");
                    btnConectar.setBackgroundColor(Color.parseColor("#334155"));
                    btnConectar.setEnabled(true);
                    Toast.makeText(this, "No se pudo contactar con la PC", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void iniciarServidorLocal() {
        new Thread(() -> {
            try {
                serverSocketRecibir = new ServerSocket(PUERTO_ESCUCHA);
                while (conectadoConPC) {
                    Socket socket = serverSocketRecibir.accept();
                    manejarPeticionEntrante(socket);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void desconectarServidorLocal() {
        conectadoConPC = false;
        try {
            if (serverSocketRecibir != null) serverSocketRecibir.close();
        } catch (Exception ignored) {}
        btnConectar.setText("Conectar");
        btnConectar.setBackgroundColor(Color.parseColor("#334155"));
        Toast.makeText(this, "Desconectado", Toast.LENGTH_SHORT).show();
    }

    private void manejarPeticionEntrante(Socket socket) {
        new Thread(() -> {
            try {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                String ipRemota = socket.getInetAddress().getHostAddress();

                // Leer encabezados HTTP de forma segura
                ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
                int b, stage = 0;
                while ((b = in.read()) != -1) {
                    headerBytes.write(b);
                    if (stage == 0 && b == '\r') stage = 1;
                    else if (stage == 1 && b == '\n') stage = 2;
                    else if (stage == 2 && b == '\r') stage = 3;
                    else if (stage == 3 && b == '\n') break;
                    else stage = (b == '\r') ? 1 : 0;
                }

                String encabezado = headerBytes.toString("UTF-8");
                int contentLength = 0;
                String filename = "archivo_pc.bin";

                for (String linea : encabezado.split("\r\n")) {
                    String lower = linea.toLowerCase();
                    if (lower.startsWith("content-length:")) {
                        contentLength = Integer.parseInt(lineeaValor(linea));
                    } else if (lower.startsWith("x-filename:")) {
                        filename = URLDecoder.decode(lineeaValor(linea), "UTF-8");
                    }
                }

                // 1. TEXTO RECIBIDO DE LA PC
                if (encabezado.startsWith("POST /texto")) {
                    byte[] cuerpo = new byte[contentLength];
                    int leidos = 0;
                    while (leidos < contentLength) {
                        int r = in.read(cuerpo, leidos, contentLength - leidos);
                        if (r == -1) break;
                        leidos += r;
                    }
                    String textoRecibido = new String(cuerpo, "UTF-8");

                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".getBytes());
                    out.flush();
                    socket.close();

                    runOnUiThread(() -> mostrarTarjetaTextoRecibido(textoRecibido, ipRemota));
                    return;
                }

                // 2. ARCHIVO RECIBIDO DE LA PC
                if (encabezado.startsWith("POST /archivo")) {
                    File temp = new File(getCacheDir(), filename);
                    FileOutputStream fos = new FileOutputStream(temp);
                    byte[] buf = new byte[8192];
                    int len, restante = contentLength;
                    while (restante > 0 && (len = in.read(buf, 0, Math.min(buf.length, restante))) != -1) {
                        fos.write(buf, 0, len);
                        restante -= len;
                    }
                    fos.flush();
                    fos.close();

                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".getBytes());
                    out.flush();
                    socket.close();

                    String finalFilename = filename;
                    runOnUiThread(() -> mostrarTarjetaArchivoRecibido(temp, finalFilename, ipRemota));
                }

            } catch (Exception ignored) {}
        }).start();
    }

    private String lineeaValor(String linea) {
        int idx = linea.indexOf(':');
        return idx != -1 ? linea.substring(idx + 1).trim() : "";
    }

    private void mostrarTarjetaTextoRecibido(String texto, String ip) {
        txtNombreArchivo.setText("Texto recibido de: " + ip);
        txtTamanoArchivo.setText(texto);
        btnConfirmarEnvio.setText("📋 COPIAR");
        btnCancelarEnvio.setText("Descartar");
        cardConfirmacion.setVisibility(View.VISIBLE);

        btnConfirmarEnvio.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Texto de PC", texto);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "✓ Copiado al portapapeles", Toast.LENGTH_SHORT).show();
            cardConfirmacion.setVisibility(View.GONE);
            restaurarBotonesCard();
        });

        btnCancelarEnvio.setOnClickListener(v -> {
            cardConfirmacion.setVisibility(View.GONE);
            restaurarBotonesCard();
        });
    }

    private void mostrarTarjetaArchivoRecibido(File temp, String filename, String ip) {
        txtNombreArchivo.setText(filename);
        txtTamanoArchivo.setText("Peso: " + formatearTamano(temp.length()) + " | De: " + ip);
        btnConfirmarEnvio.setText("Aceptar");
        btnCancelarEnvio.setText("Rechazar");
        cardConfirmacion.setVisibility(View.VISIBLE);

        btnConfirmarEnvio.setOnClickListener(v -> {
            guardarArchivoDescargas(temp, filename);
            cardConfirmacion.setVisibility(View.GONE);
            restaurarBotonesCard();
        });

        btnCancelarEnvio.setOnClickListener(v -> {
            temp.delete();
            Toast.makeText(this, "Archivo rechazado", Toast.LENGTH_SHORT).show();
            cardConfirmacion.setVisibility(View.GONE);
            restaurarBotonesCard();
        });
    }

    private void guardarArchivoDescargas(File temp, String filename) {
        new Thread(() -> {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();

                File dest = new File(dir, filename);
                int count = 1;
                String base = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
                String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "";
                while (dest.exists()) {
                    dest = new File(dir, base + "_" + count + ext);
                    count++;
                }

                FileInputStream in = new FileInputStream(temp);
                FileOutputStream out = new FileOutputStream(dest);
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                in.close();
                out.close();
                temp.delete();

                String finalName = dest.getName();
                runOnUiThread(() -> Toast.makeText(this, "✓ Guardado en Descargas: " + finalName, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error al guardar archivo", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void restaurarBotonesCard() {
        btnConfirmarEnvio.setText("Enviar");
        btnCancelarEnvio.setText("Cancelar");
        btnConfirmarEnvio.setOnClickListener(v -> {
            if (archivoPendiente != null) enviarArchivo(archivoPendiente, false);
        });
        btnCancelarEnvio.setOnClickListener(v -> {
            archivoPendiente = null;
            cardConfirmacion.setVisibility(View.GONE);
        });
    }

    // --- MÉTODOS DE ENVÍO ORIGINALES A LA PC ---
    private void enviarTeclaUDP(String tecla) {
        String ip = prefs.getString("pc_ip", "");
        if (ip.isEmpty()) return;
        new Thread(() -> {
            try {
                byte[] datos = tecla.getBytes("UTF-8");
                InetAddress destino = InetAddress.getByName(ip);
                DatagramPacket paquete = new DatagramPacket(datos, datos.length, destino, 8081);
                if (udpSocket != null) {
                    udpSocket.send(paquete);
                } else {
                    DatagramSocket s = new DatagramSocket();
                    s.send(paquete);
                    s.close();
                }
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
        restaurarBotonesCard();
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
                if (in == null) throw new Exception("No se pudo leer");

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        desconectarServidorLocal();
    }
}
