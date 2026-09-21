import os
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime

try:
    import pyautogui
    pyautogui.FAILSAFE = False
    TIENE_PYAUTOGUI = True
except ImportError:
    TIENE_PYAUTOGUI = False

CARPETA_DESTINO = os.path.join(os.path.expanduser("~"), "Downloads")
PUERTO = 8080

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        cuerpo = self.rfile.read(length)

        # 1. TECLADO EN VIVO (Tecla por tecla)
        if self.path == "/tecla":
            texto = cuerpo.decode("utf-8")
            if TIENE_PYAUTOGUI:
                if texto == "__BACKSPACE__":
                    pyautogui.press('backspace')
                    print(" [TECLA] ⌫ Borrar")
                elif texto == "__ENTER__":
                    pyautogui.press('enter')
                    print(" [TECLA] ↵ Enter")
                else:
                    pyautogui.write(texto)
                    print(f" [TECLA] {texto}")
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"OK")
            return

        # 2. TEXTO EN BLOQUE
        if self.path == "/texto":
            texto = cuerpo.decode("utf-8")
            if TIENE_PYAUTOGUI:
                pyautogui.write(texto, interval=0.01)
                print(f" [BLOQUE] {texto}")
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"OK")
            return

        # 3. ARCHIVOS
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
        print(f" [ARCHIVO] {os.path.basename(ruta_guardado)} ({peso_mb:.2f} MB)")

        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"OK")

    def do_GET(self):
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"Servidor Activo")

    def log_message(self, format, *args):
        pass

if __name__ == "__main__":
    print(f"--- Servidor listo en puerto {PUERTO} ---")
    HTTPServer(("0.0.0.0", PUERTO), Handler).serve_forever()