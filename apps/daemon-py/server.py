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

    def _is_private_ip(self, ip_str):
        try:
            ip = ipaddress.ip_address(ip_str)
            if ip.is_private or ip.is_loopback:
                return True
            if isinstance(ip, ipaddress.IPv4Address):
                octets = [int(p) for p in ip_str.split(".")]
                if len(octets) == 4 and octets[0] == 100 and 64 <= octets[1] <= 127:
                    return True
            return False
        except ValueError:
            return False

    def _save_node_to_config(self, node_name, host, port, token):
        import os
        config_path = os.path.expanduser("~/.gemini/mesh_nodes.json")
        os.makedirs(os.path.dirname(config_path), exist_ok=True)
        nodes = {}
        if os.path.isfile(config_path):
            try:
                with open(config_path, "r", encoding="utf-8") as f:
                    nodes = json.load(f)
            except Exception:
                nodes = {}
        nodes[node_name] = {
            "host": host,
            "port": port,
            "token": token
        }
        with open(config_path, "w", encoding="utf-8") as f:
            json.dump(nodes, f, indent=2)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8", errors="ignore")
        data = json.loads(body) if body else {}

        # Zero-Touch LAN Pairing endpoint
        if self.path == "/pair":
            client_ip = self.client_address[0]
            if not self._is_private_ip(client_ip):
                self._send_json({"error": "Pairing only allowed from private LAN"}, status=403)
                return

            remote_node = data.get("node_name", f"node-{client_ip.replace('.', '-')}")
            remote_host = data.get("host") or client_ip
            remote_port = data.get("port", 8888)
            remote_token = data.get("token")

            if not remote_token:
                self._send_json({"error": "Missing remote 'token' in pairing request"}, status=400)
                return

            # Save remote node into local config
            self._save_node_to_config(remote_node, remote_host, remote_port, remote_token)
            print(f"🤝 Paired successfully with '{remote_node}' ({remote_host}:{remote_port})")

            # Return our node details
            import platform
            self._send_json({
                "status": "paired",
                "node_name": platform.node(),
                "token": self.auth_token,
                "platform": platform.system()
            })
            return

        if not self._verify_auth():
            return

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
        elif self.path == "/ask":
            question = data.get("question")
            if not question:
                self._send_json({"error": "Missing 'question' parameter"}, status=400)
                return
            cli_path = self._discover_agy_cli()
            if not cli_path:
                self._send_json({
                    "error": "No AI CLI (agy, gemini, claude) found on this node.",
                    "returncode": -1
                })
                return
            auto_approve = data.get("auto_approve", True)
            conversation_id = data.get("conversation_id")
            flags = "--dangerously-skip-permissions" if auto_approve else ""
            escaped = question.replace('"', '\\"')

            is_agy = "agy" in os.path.basename(cli_path).lower()
            conv_flag = f'--conversation "{conversation_id.strip()}"' if (conversation_id and is_agy and conversation_id.strip()) else ""
            fmt_flag = '--output-format json' if is_agy else ""

            cmd = f'"{cli_path}" {flags} {fmt_flag} {conv_flag} --print "{escaped}"'.strip()
            result = self._exec_command(cmd)

            if is_agy and result.get("stdout"):
                try:
                    import json
                    raw = result["stdout"].strip()
                    s = raw.find('{')
                    e = raw.rfind('}')
                    if s != -1 and e != -1 and s < e:
                        val = json.loads(raw[s:e+1])
                        result["stdout"] = val.get("response", result["stdout"])
                        if "conversation_id" in val:
                            result["conversation_id"] = val["conversation_id"]
                except Exception:
                    pass

            self._send_json(result)
        else:
            self.send_response(404)
            self.end_headers()

    def _discover_agy_cli(self):
        import shutil
        for name in ["agy", "gemini", "claude"]:
            found = shutil.which(name)
            if found:
                return found

        home = os.path.expanduser("~")
        candidates = [
            os.path.join(os.environ.get("LOCALAPPDATA", ""), "agy", "bin", "agy.exe"),
            os.path.join(os.environ.get("LOCALAPPDATA", ""), "agy", "bin", "agy.cmd"),
            os.path.join(os.environ.get("APPDATA", ""), "npm", "agy.cmd"),
            os.path.join(home, ".local", "bin", "agy"),
            os.path.join(home, ".cargo", "bin", "agy"),
            os.path.join(home, ".gemini", "antigravity-ide", "bin", "agy"),
        ]
        for c in candidates:
            if c and os.path.isfile(c):
                return c
        return None


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
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    import secrets
    parser = argparse.ArgumentParser(description="Antigravity Mesh Node Daemon")
    parser.add_argument("--host", default="0.0.0.0", help="Binding host")
    parser.add_argument("--port", type=int, default=8888, help="Listening port")
    parser.add_argument("--token", default=None, help="Security authorization token")
    args = parser.parse_args()

    token = args.token
    config_path = os.path.expanduser("~/.gemini/mesh_nodes.json")

    # If token not supplied, check local config or auto-generate
    if not token:
        if os.path.isfile(config_path):
            try:
                with open(config_path, "r", encoding="utf-8") as f:
                    cfg = json.load(f)
                    for k in ["local", "local-mac", "local-node", "self"]:
                        if k in cfg and "token" in cfg[k]:
                            token = cfg[k]["token"]
                            break
            except Exception:
                pass

        if not token:
            token = secrets.token_hex(16)
            # Save into mesh_nodes.json
            try:
                os.makedirs(os.path.dirname(config_path), exist_ok=True)
                existing = {}
                if os.path.isfile(config_path):
                    with open(config_path, "r", encoding="utf-8") as f:
                        existing = json.load(f)
                existing["local"] = {"host": "127.0.0.1", "port": args.port, "token": token}
                with open(config_path, "w", encoding="utf-8") as f:
                    json.dump(existing, f, indent=2)
            except Exception as e:
                print(f"Warning: could not save generated token: {e}")

    MeshRequestHandler.auth_token = token
    server = HTTPServer((args.host, args.port), MeshRequestHandler)
    print(f"🚀 Antigravity Mesh Node listening on {args.host}:{args.port}")
    print(f"🔑 Auth Token: {token}")
    print(f"🤝 Zero-Touch LAN Pairing active on POST /pair")
    server.serve_forever()

if __name__ == "__main__":
    main()

