import os
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime

# Intentar importar pyautogui para el teclado virtual
try:
    import pyautogui
    pyautogui.FAILSAFE = False
    TIENE_PYAUTOGUI = True
except ImportError:
    TIENE_PYAUTOGUI = False

CARPETA_DESTINO = os.path.join(os.path.expanduser("~"), "Downloads")
PUERTO = 8080

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        # Servir el APK directamente si está en la misma carpeta
        if self.path == "/descargar-apk":
            apk_path = os.path.join(os.path.dirname(__file__), "app-debug.apk")
            if os.path.exists(apk_path):
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Disposition", 'attachment; filename="Mandar-A-PC.apk"')
                self.end_headers()
                with open(apk_path, "rb") as f:
                    self.wfile.write(f.read())
                return

        # Web simple para el móvil
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(b"<h2>Servidor PC Activo</h2><a href='/descargar-apk'>Descargar APK</a>")

    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        cuerpo = self.rfile.read(length)

        # 1. Caso: Teclado remoto
        if self.path == "/texto":
            texto = cuerpo.decode("utf-8")
            print(f" [TECLADO] Escribiendo: {texto}")
            if TIENE_PYAUTOGUI:
                pyautogui.write(texto, interval=0.01)
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"OK")
            return

        # 2. Caso: Archivos (cualquier formato)
        # Leer el nombre original que envía el teléfono
        nombre_header = self.headers.get('X-Filename')
        if nombre_header:
            nombre_archivo = urllib.parse.unquote(nombre_header)
        else:
            nombre_archivo = f"archivo_{datetime.now().strftime('%Y%m%d_%H%M%S')}.bin"

        ruta_guardado = os.path.join(CARPETA_DESTINO, nombre_archivo)
        
        # Evitar sobrescribir si ya existe uno igual
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
    print(f"--- Servidor listo en puerto {PUERTO} ---")
    if not TIENE_PYAUTOGUI:
        print(" [AVISO] Ejecuta 'pip install pyautogui' para que escriba en tu teclado.")
    HTTPServer(("0.0.0.0", PUERTO), Handler).serve_forever()