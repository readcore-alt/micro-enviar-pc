import os
import socket
import threading
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime

# ==========================================
# TU CLAVE DE SEGURIDAD (Cámbiala por la que quieras)
PIN_SEGURIDAD = "1234"
# ==========================================

try:
    import pyautogui
    pyautogui.FAILSAFE = False
    TIENE_PYAUTOGUI = True
except ImportError:
    TIENE_PYAUTOGUI = False

CARPETA_DESTINO = os.path.join(os.path.expanduser("~"), "Downloads")
PUERTO_HTTP = 8080
PUERTO_UDP = 8081

# --- 1. TECLADO ULTRA RÁPIDO (UDP 1ms) ---
def servidor_teclado_udp():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("0.0.0.0", PUERTO_UDP))
    while True:
        try:
            datos, _ = sock.recvfrom(1024)
            texto = datos.decode("utf-8")
            if TIENE_PYAUTOGUI:
                if texto == "__BACKSPACE__":
                    pyautogui.press('backspace')
                elif texto == "__ENTER__":
                    pyautogui.press('enter')
                else:
                    pyautogui.write(texto)
        except Exception:
            pass

threading.Thread(target=servidor_teclado_udp, daemon=True).start()

# --- 2. SERVIDOR WEB PROTEGIDO (HTTP) ---
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        url_parsed = urllib.parse.urlparse(self.path)
        parametros = urllib.parse.parse_qs(url_parsed.query)
        pin_ingresado = parametros.get('pin', [''])[0]

        # Descarga directa del APK (Solo si tiene el PIN correcto)
        if url_parsed.path == "/descargar-apk":
            if pin_ingresado != PIN_SEGURIDAD:
                self.send_response(403)
                self.end_headers()
                self.wfile.write(b"Acceso Denegado: PIN incorrecto.")
                return

            apk_path = os.path.join(os.path.dirname(__file__), "app-debug.apk")
            if os.path.exists(apk_path):
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Disposition", 'attachment; filename="Mandar-A-PC.apk"')
                self.end_headers()
                with open(apk_path, "rb") as f:
                    self.wfile.write(f.read())
                return
            else:
                self.send_response(404)
                self.end_headers()
                self.wfile.write(b"No se encontro app-debug.apk en la carpeta.")
                return

        # Página web con pantalla de bloqueo por PIN
        autorizado = (pin_ingresado == PIN_SEGURIDAD)

        if autorizado:
            contenido_card = f"""
                <h2 style='color:#38bdf8;'>Acceso Autorizado</h2>
                <p>Tu PC est&aacute; conectada y lista.</p>
                <a href='/descargar-apk?pin={PIN_SEGURIDAD}' class='btn'>&#11015; Descargar &Uacute;ltimo APK</a>
            """
        else:
            contenido_card = f"""
                <h2>&#128274; Servidor Privado</h2>
                <p>Ingresa el PIN de seguridad para acceder:</p>
                <form method='GET' action='/'>
                    <input type='password' name='pin' placeholder='PIN de 4 dígitos' class='input-pin' maxlength='10' autofocus>
                    <button type='submit' class='btn'>Desbloquear</button>
                </form>
            """

        html = f"""<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Servidor Privado</title>
    <style>
        body {{ font-family: system-ui, sans-serif; background: #0b0f19; color: #fff; text-align: center; padding: 40px 20px; margin: 0; }}
        .card {{ background: #1e293b; border-radius: 16px; padding: 24px; max-width: 340px; margin: auto; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }}
        h2 {{ margin-top: 0; }}
        p {{ color: #94a3b8; font-size: 14px; margin-bottom: 20px; }}
        .input-pin {{ width: 85%; padding: 12px; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #fff; font-size: 16px; text-align: center; margin-bottom: 12px; }}
        .btn {{ display: block; width: 90%; background: #2563eb; color: #fff; padding: 12px; border-radius: 8px; text-decoration: none; font-weight: bold; margin: auto; border: none; font-size: 15px; cursor: pointer; }}
    </style>
</head>
<body>
    <div class="card">{contenido_card}</div>
</body>
</html>"""

        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(html.encode("utf-8"))

    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        cuerpo = self.rfile.read(length)

        # 1. TEXTO EN BLOQUE
        if self.path == "/texto":
            texto = cuerpo.decode("utf-8")
            if TIENE_PYAUTOGUI:
                pyautogui.write(texto, interval=0.01)
                print(f" [BLOQUE] {texto}")
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"OK")
            return

        # 2. ARCHIVOS (Fotos, PDFs, ZIPs)
        nombre_header = self.headers.get('X-Filename')
        if nombre_header:
            nombre_archivo = urllib.parse.unquote(nombre_header)
        else:
            nombre_archivo = f"archivo_{datetime.now().strftime('%Y%m%d_%H%M%S')}.bin"

        ruta_guardado = os.path.join(CARPETA_DESTINO, nombre_archivo)
        contador = 1
        base, extension = os.path.splitext(nombre_archivo)
        while os.path.exists(ruta_guardado):
            ruta_guardado = os.path.join(CARPETA_DESTINO, f"{base}_{contador}{extension}")
            contador += 1

        with open(ruta_guardado, "wb") as f:
            f.write(cuerpo)

        peso_mb = len(cuerpo) / (1024 * 1024)
        print(f" [ARCHIVO RECIBIDO] {os.path.basename(ruta_guardado)} ({peso_mb:.2f} MB)")

        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"OK")

    def log_message(self, format, *args):
        pass

if __name__ == "__main__":
    print(f"--- Servidor Seguro Activo ---")
    print(f"PIN configurado: {PIN_SEGURIDAD}")
    print(f"Puerto HTTP: {PUERTO_HTTP} | Puerto Teclado UDP: {PUERTO_UDP}")
    HTTPServer(("0.0.0.0", PUERTO_HTTP), Handler).serve_forever()