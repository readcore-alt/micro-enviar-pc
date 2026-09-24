import os
import socket
import threading
import urllib.parse
import urllib.request
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from datetime import datetime
import tkinter as tk
from tkinter import filedialog, messagebox

# ==========================================
# CONFIGURACIÓN
PIN_SEGURIDAD = "1234"
CARPETA_DESTINO = os.path.join(os.path.expanduser("~"), "Downloads")
PUERTO_HTTP = 8080
PUERTO_UDP = 8081
PUERTO_MOVIL = 8082  # Puerto donde el móvil escuchará cuando esté conectado
# ==========================================

try:
    import pyautogui
    pyautogui.FAILSAFE = False
    TIENE_PYAUTOGUI = True
except ImportError:
    TIENE_PYAUTOGUI = False

# Variable global para guardar la IP del móvil cuando se conecte
ip_movil_conectado = None

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

# --- 2. SERVIDOR HTTP (Recepción y Conexión) ---
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        url_parsed = urllib.parse.urlparse(self.path)
        parametros = urllib.parse.parse_qs(url_parsed.query)
        pin_ingresado = parametros.get('pin', [''])[0]

        # Descarga directa del APK
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
                self.wfile.write(b"No se encontro app-debug.apk")
                return

        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(b"Servidor PC Activo.")

    def do_POST(self):
        global ip_movil_conectado
        length = int(self.headers.get('Content-Length', 0))
        cuerpo = self.rfile.read(length)

        # 1. SOLICITUD DE CONEXIÓN DESDE EL MÓVIL
        if self.path == "/conectar":
            ip_entrante = self.client_address[0]
            print(f" [CONEXION] Solicitud entrante desde {ip_entrante}")

            # Preguntarle al usuario de la PC mediante la interfaz gráfica
            evento = threading.Event()
            aceptado = [False]

            def preguntar():
                res = messagebox.askyesno("Solicitud de Conexión", f"¿Aceptar conexión del móvil con IP:\n{ip_entrante}?")
                aceptado[0] = res
                evento.set()

            root.after(0, preguntar)
            evento.wait(timeout=25)  # 25 segundos para responder

            if aceptado[0]:
                ip_movil_conectado = ip_entrante
                root.after(0, lambda: actualizar_estado_ui(f"Conectado: {ip_movil_conectado}", "#10B981"))
                print(f" [CONEXION] Vinculado con éxito a {ip_movil_conectado}")
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b"OK")
            else:
                print(" [CONEXION] Rechazada por el usuario de la PC.")
                self.send_response(403)
                self.end_headers()
                self.wfile.write(b"RECHAZADO")
            return

        # 2. TEXTO RECIBIDO DEL MÓVIL (Tipeo en PC)
        if self.path == "/texto":
            texto = cuerpo.decode("utf-8")
            if TIENE_PYAUTOGUI:
                pyautogui.write(texto, interval=0.01)
                print(f" [BLOQUE] {texto}")
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"OK")
            return

        # 3. ARCHIVOS RECIBIDOS DEL MÓVIL
        nombre_header = self.headers.get('X-Filename')
        nombre_archivo = urllib.parse.unquote(nombre_header) if nombre_header else f"archivo_{datetime.now().strftime('%Y%m%d_%H%M%S')}.bin"

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

def iniciar_servidor_http():
    servidor = ThreadingHTTPServer(("0.0.0.0", PUERTO_HTTP), Handler)
    servidor.serve_forever()

threading.Thread(target=iniciar_servidor_http, daemon=True).start()

# --- 3. FUNCIONES PARA ENVIAR COSAS AL MÓVIL ---
def enviar_texto():
    if not ip_movil_conectado:
        messagebox.showwarning("Sin conexión", "Primero debes conectar el móvil.")
        return

    texto = entrada_texto.get().strip()
    if not texto:
        return

    def tarea():
        try:
            url = f"http://{ip_movil_conectado}:{PUERTO_MOVIL}/texto"
            req = urllib.request.Request(url, data=texto.encode("utf-8"), method="POST")
            with urllib.request.urlopen(req, timeout=4) as res:
                if res.status == 200:
                    print(f" [ENVIADO A MOVIL] Texto: {texto}")
                    root.after(0, lambda: entrada_texto.delete(0, tk.END))
        except Exception as e:
            print(f" [ERROR] No se pudo enviar el texto al móvil: {e}")
            root.after(0, lambda: messagebox.showerror("Error", "El móvil no respondió."))

    threading.Thread(target=tarea, daemon=True).start()

def enviar_archivo():
    if not ip_movil_conectado:
        messagebox.showwarning("Sin conexión", "Primero debes conectar el móvil.")
        return

    ruta_archivo = filedialog.askopenfilename()
    if not ruta_archivo:
        return

    nombre_archivo = os.path.basename(ruta_archivo)
    tamano_bytes = os.path.getsize(ruta_archivo)
    tamano_mb = tamano_bytes / (1024 * 1024)

    def tarea():
        try:
            print(f" [ENVIANDO A MOVIL] {nombre_archivo} ({tamano_mb:.2f} MB)...")
            url = f"http://{ip_movil_conectado}:{PUERTO_MOVIL}/archivo"
            
            with open(ruta_archivo, "rb") as f:
                contenido = f.read()

            req = urllib.request.Request(url, data=contenido, method="POST")
            req.add_header("X-Filename", urllib.parse.quote(nombre_archivo))
            req.add_header("X-Filesize", str(tamano_bytes))

            with urllib.request.urlopen(req, timeout=10) as res:
                if res.status == 200:
                    print(f" [ARCHIVO ENVIADO CON ÉXITO]")
                    root.after(0, lambda: messagebox.showinfo("Éxito", f"Archivo {nombre_archivo} enviado al móvil."))
        except Exception as e:
            print(f" [ERROR] Falló el envío del archivo: {e}")
            root.after(0, lambda: messagebox.showerror("Error", "El móvil rechazó o no recibió el archivo."))

    threading.Thread(target=tarea, daemon=True).start()

# --- 4. INTERFAZ GRÁFICA DE LA PC (Tkinter) ---
root = tk.Tk()
root.title("Servidor PC - Mandar A PC")
root.geometry("340x360")
root.configure(bg="#0F172A")
root.resizable(False, False)

# Estado
lbl_titulo = tk.Label(root, text="Mandar-A-PC (Servidor)", fg="#38BDF8", bg="#0F172A", font=("Segoe UI", 13, "bold"))
lbl_titulo.pack(pady=(16, 4))

lbl_estado = tk.Label(root, text="Esperando conexión del móvil...", fg="#EF4444", bg="#0F172A", font=("Segoe UI", 10))
lbl_estado.pack(pady=(0, 16))

def actualizar_estado_ui(texto, color):
    lbl_estado.config(text=texto, fg=color)

# Marco Enviar Archivo
frame_archivo = tk.LabelFrame(root, text=" Archivo para el Móvil ", fg="#94A3B8", bg="#1E293B", font=("Segoe UI", 9), padx=10, pady=10)
frame_archivo.pack(fill="x", padx=16, pady=6)

btn_enviar_archivo = tk.Button(frame_archivo, text="📁 Seleccionar y Enviar Archivo", bg="#2563EB", fg="#FFFFFF", font=("Segoe UI", 10, "bold"), relief="flat", cursor="hand2", command=enviar_archivo)
btn_enviar_archivo.pack(fill="x")

# Marco Enviar Texto
frame_texto = tk.LabelFrame(root, text=" Texto o Enlace para el Móvil ", fg="#94A3B8", bg="#1E293B", font=("Segoe UI", 9), padx=10, pady=10)
frame_texto.pack(fill="x", padx=16, pady=10)

entrada_texto = tk.Entry(frame_texto, bg="#0F172A", fg="#FFFFFF", font=("Segoe UI", 10), insertbackground="white", relief="solid", bd=1)
entrada_texto.pack(fill="x", pady=(0, 8), ipady=4)

btn_enviar_texto = tk.Button(frame_texto, text="📋 Enviar Texto al Móvil", bg="#334155", fg="#FFFFFF", font=("Segoe UI", 9, "bold"), relief="flat", cursor="hand2", command=enviar_texto)
btn_enviar_texto.pack(fill="x")

print("--- Servidor con Interfaz Iniciado ---")
print(f"Puerto HTTP: {PUERTO_HTTP} | Puerto Teclado UDP: {PUERTO_UDP}")
root.mainloop()