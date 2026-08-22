"""
WebSocket event listener client for MINI-AWS real-time notifications (/ws-events/tasks & /ws-events/metrics).
"""
import json
import queue
import threading
import time
from typing import Callable, Optional, List, Dict, Any
import requests

try:
    import websocket
except ImportError:
    websocket = None


class WebSocketEventListener:
    """Subscribes to MiniCloud WebSocket notifications and captures frames in a thread-safe queue."""

    def __init__(self, ws_url: str, endpoint: str = "/ws-events/tasks"):
        self.ws_url = ws_url.rstrip("/") + endpoint
        # Derive HTTP URL for polling fallback if WS is not active
        http_base = ws_url.replace("ws://", "http://").replace("wss://", "https://").rstrip("/")
        self.http_poll_url = http_base + endpoint
        self.endpoint = endpoint
        self.events_queue: queue.Queue = queue.Queue()
        self.all_events: List[Dict[str, Any]] = []
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._ws = None

    def start(self):
        """Starts listening for events asynchronously."""
        self._running = True
        self._thread = threading.Thread(target=self._run_listener, daemon=True)
        self._thread.start()
        # Brief pause to let connection establish
        time.sleep(0.1)

    def stop(self):
        """Stops the listener."""
        self._running = False
        if self._ws:
            try:
                self._ws.close()
            except Exception:
                pass

    def _run_listener(self):
        # Attempt WebSocket connection first if websocket library is available
        connected = False
        if websocket:
            try:
                def on_message(ws, message):
                    try:
                        data = json.loads(message)
                        self.events_queue.put(data)
                        self.all_events.append(data)
                    except Exception:
                        pass

                self._ws = websocket.WebSocketApp(
                    self.ws_url,
                    on_message=on_message
                )
                # Run WS in loop
                self._ws.run_forever(ping_interval=10, ping_timeout=5)
                connected = True
            except Exception:
                connected = False

        # If WS loop exits or was not established, use fallback event polling
        seen_count = 0
        while self._running:
            try:
                resp = requests.get(self.http_poll_url, timeout=1.0)
                if resp.status_code == 200:
                    payload = resp.json()
                    events = payload.get("events", []) if isinstance(payload, dict) else payload
                    if isinstance(events, list) and len(events) > seen_count:
                        new_events = events[seen_count:]
                        for ev in new_events:
                            self.events_queue.put(ev)
                            self.all_events.append(ev)
                        seen_count = len(events)
            except Exception:
                pass
            time.sleep(0.1)

    def wait_for_event(self, predicate: Callable[[Dict[str, Any]], bool], timeout: float = 5.0) -> Optional[Dict[str, Any]]:
        """Waits for an event that satisfies the predicate function."""
        start_time = time.time()
        # First check existing all_events
        for ev in self.all_events:
            try:
                if predicate(ev):
                    return ev
            except Exception:
                pass

        while time.time() - start_time < timeout:
            remaining = timeout - (time.time() - start_time)
            if remaining <= 0:
                break
            try:
                ev = self.events_queue.get(timeout=min(0.2, remaining))
                if predicate(ev):
                    return ev
            except queue.Empty:
                pass
        return None

    def get_all_events(self) -> List[Dict[str, Any]]:
        return list(self.all_events)

    def clear(self):
        self.all_events.clear()
        while not self.events_queue.empty():
            try:
                self.events_queue.get_nowait()
            except Exception:
                break
