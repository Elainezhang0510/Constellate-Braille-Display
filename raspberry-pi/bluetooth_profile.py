"""BlueZ SPP server. D-Bus and socket I/O run off the GPIO main loop."""
import queue
import socket
import threading

from board_protocol import LineMessages, reply_bytes

SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"
PROFILE_PATH = "/org/braillio/SerialProfile"


class BluetoothProfile:
    def __init__(self):
        self.inbox = queue.Queue(maxsize=32)
        self._ready = threading.Event()
        self._lock = threading.RLock()
        self._client = None
        self._outbox = None
        self._thread = None
        self._error = None
        self._loop = None
        self._glib = None

    def is_current(self, client):
        with self._lock:
            return self._client is client

    def start(self):
        self._thread = threading.Thread(target=self._run, name="bluez-profile", daemon=True)
        self._thread.start()
        if not self._ready.wait(10):
            raise RuntimeError("Timed out registering Bluetooth SPP profile")
        if self._error is not None:
            raise RuntimeError(f"Bluetooth SPP registration failed: {self._error}") from self._error

    def _run(self):
        try:
            import dbus
            import dbus.service
            import dbus.mainloop.glib
            from gi.repository import GLib

            dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
            bus = dbus.SystemBus()
            server = self

            class Profile(dbus.service.Object):
                @dbus.service.method("org.bluez.Profile1", in_signature="", out_signature="")
                def Release(self):
                    server.close_client()
                    server._loop.quit()

                @dbus.service.method("org.bluez.Profile1", in_signature="", out_signature="")
                def Cancel(self):
                    pass

                @dbus.service.method("org.bluez.Profile1", in_signature="oha{sv}", out_signature="")
                def NewConnection(self, device, fd, properties):
                    client = socket.socket(fileno=fd.take())
                    client.settimeout(0.5)
                    with server._lock:
                        if server._client is not None:
                            client.close()
                            return
                        server._client = client
                        server._outbox = queue.Queue(maxsize=32)
                    threading.Thread(target=server._read_client, args=(client,), daemon=True).start()
                    threading.Thread(target=server._write_client, args=(client,), daemon=True).start()

                @dbus.service.method("org.bluez.Profile1", in_signature="o", out_signature="")
                def RequestDisconnection(self, device):
                    server.close_client()

            profile = Profile(bus, PROFILE_PATH)
            manager = dbus.Interface(bus.get_object("org.bluez", "/org/bluez"),
                                     "org.bluez.ProfileManager1")
            self._loop, self._glib = GLib.MainLoop(), GLib
            manager.RegisterProfile(PROFILE_PATH, SPP_UUID,
                                    {"Name": "Braillio Braille Board", "Role": "server",
                                     "RequireAuthentication": True})
            print("Bluetooth SPP profile ready", flush=True)
            self._ready.set()
            try:
                self._loop.run()
            finally:
                self.close_client()
                try:
                    manager.UnregisterProfile(PROFILE_PATH)
                except dbus.DBusException:
                    pass
                profile.remove_from_connection()
        except Exception as exc:
            self._error = exc
            self._ready.set()

    def _read_client(self, client):
        lines = LineMessages()
        try:
            while True:
                try:
                    chunk = client.recv(1024)
                except socket.timeout:
                    continue
                if not chunk:
                    break
                try:
                    messages = lines.feed(chunk)
                except ValueError as exc:
                    self.send({"ok": False, "error": str(exc)}, client)
                    lines = LineMessages()
                    continue
                for message in messages:
                    try:
                        self.inbox.put_nowait((client, message))
                    except queue.Full:
                        self.send({"ok": False, "error": "Board busy"}, client)
        except OSError:
            pass
        finally:
            self.close_client(client)

    def _write_client(self, client):
        while True:
            with self._lock:
                if self._client is not client:
                    return
                outbox = self._outbox
            try:
                data = outbox.get(timeout=0.5)
            except queue.Empty:
                continue
            try:
                client.sendall(data)
            except (OSError, socket.timeout):
                self.close_client(client)
                return

    def send(self, reply, client=None):
        with self._lock:
            if self._client is None or (client is not None and self._client is not client):
                return
            try:
                self._outbox.put_nowait(reply_bytes(reply))
            except queue.Full:
                self.close_client(client)

    def close_client(self, client=None):
        with self._lock:
            if self._client is not None and (client is None or self._client is client):
                self._client.close()
                self._client = None
                self._outbox = None

    def close(self):
        self.close_client()
        if self._loop is not None:
            self._glib.idle_add(self._loop.quit)
        if self._thread is not None:
            self._thread.join(timeout=3)
