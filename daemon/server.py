"""
Antigravity Mesh Node Daemon
Lekki serwer węzła umożliwiający komunikację agentów i inspekcję maszyny.
"""

import os
import sys
import json
import argparse
import platform
import subprocess
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import TCPServer

TCPServer.allow_reuse_address = True

class MeshRequestHandler(BaseHTTPRequestHandler):
    auth_token = None

    def _verify_auth(self):
        if not self.auth_token:
            return True
        token = self.headers.get("X-Mesh-Token") or self.headers.get("Authorization")
        if token and token.replace("Bearer ", "").strip() == self.auth_token:
            return True
        self.send_response(401)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps({"error": "Unauthorized"}).encode())
        return False

    def do_GET(self):
        if not self._verify_auth():
            return
        
        if self.path == "/health":
            self._send_json({"status": "ok", "platform": platform.system(), "node": platform.node()})
        elif self.path == "/system":
            self._send_json(self._get_system_info())
        else:
            self._send_json({"message": "Antigravity Mesh Node is running", "endpoints": ["/health", "/system", "POST /query", "POST /exec"]})

    def do_POST(self):
        if not self._verify_auth():
            return
        
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8", errors="ignore")
        data = json.loads(body) if body else {}

        if self.path == "/query":
            # Wyszukiwanie / inspekcja plików
            path = data.get("path", ".")
            result = self._query_path(path, max_depth=data.get("max_depth", 2))
            self._send_json(result)
        elif self.path == "/exec":
            # Wykonanie komendy shellowej w bezpiecznym kontekście
            cmd = data.get("cmd")
            if not cmd:
                self._send_json({"error": "Missing 'cmd' parameter"}, status=400)
                return
            result = self._exec_command(cmd)
            self._send_json(result)
        else:
            self.send_response(404)
            self.end_headers()

    def _get_system_info(self):
        return {
            "platform": platform.platform(),
            "processor": platform.processor(),
            "python_version": sys.version,
            "cwd": os.getcwd()
        }

    def _query_path(self, target_path, max_depth=2):
        target_path = os.path.expanduser(os.path.expandvars(target_path))
        if not os.path.exists(target_path):
            return {"error": f"Path '{target_path}' does not exist"}

        items = []
        base_depth = target_path.rstrip(os.path.sep).count(os.path.sep)

        for root, dirs, files in os.walk(target_path):
            depth = root.rstrip(os.path.sep).count(os.path.sep) - base_depth
            if depth >= max_depth:
                dirs.clear()
                continue
            for d in dirs:
                full_p = os.path.join(root, d)
                items.append({"name": d, "type": "dir", "path": full_p})
            for f in files:
                full_p = os.path.join(root, f)
                size = os.path.getsize(full_p) if os.path.exists(full_p) else 0
                items.append({"name": f, "type": "file", "size": size, "path": full_p})

        return {"path": target_path, "count": len(items), "items": items[:200]}

    def _exec_command(self, cmd):
        try:
            res = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=60)
            return {"returncode": res.returncode, "stdout": res.stdout, "stderr": res.stderr}
        except Exception as e:
            return {"error": str(e)}

    def _send_json(self, data, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write(json.dumps(data, indent=2).encode("utf-8"))

def main():
    parser = argparse.ArgumentParser(description="Antigravity Mesh Node Daemon")
    parser.add_argument("--host", default="0.0.0.0", help="Binding host")
    parser.add_argument("--port", type=int, default=8888, help="Listening port")
    parser.add_argument("--token", default=None, help="Security authorization token")
    args = parser.parse_args()

    MeshRequestHandler.auth_token = args.token
    server = HTTPServer((args.host, args.port), MeshRequestHandler)
    print(f"🚀 Antigravity Mesh Node listening on {args.host}:{args.port}")
    server.serve_forever()

if __name__ == "__main__":
    main()
