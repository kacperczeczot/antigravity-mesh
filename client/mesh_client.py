"""
Antigravity Mesh Client
Moduł używany przez agenta Antigravity do komunikacji z węzłami sieciowymi.
"""

import json
import urllib.request
import urllib.error

class MeshClient:
    def __init__(self, host="127.0.0.1", port=8888, token=None):
        self.base_url = f"http://{host}:{port}"
        self.token = token

    def _request(self, endpoint, data=None):
        url = f"{self.base_url}{endpoint}"
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["X-Mesh-Token"] = self.token

        req_data = json.dumps(data).encode("utf-8") if data else None
        req = urllib.request.Request(url, data=req_data, headers=headers)
        
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            return {"error": f"HTTP {e.code}: {e.read().decode('utf-8')}"}
        except Exception as e:
            return {"error": str(e)}

    @classmethod
    def load_nodes(cls, config_path=None):
        import os
        paths = [
            config_path,
            os.path.expanduser("~/.gemini/mesh_nodes.json"),
            os.path.expanduser("~/.gemini/config/mesh_nodes.json"),
            os.path.join(os.path.dirname(__file__), "..", "config", "nodes.json")
        ]
        for p in paths:
            if p and os.path.isfile(p):
                with open(p, "r", encoding="utf-8") as f:
                    return json.load(f)
        return {}

    @classmethod
    def from_node(cls, name="local-mac", config_path=None):
        nodes = cls.load_nodes(config_path)
        if name not in nodes:
            raise ValueError(f"Node '{name}' not found in configuration. Available nodes: {list(nodes.keys())}")
        cfg = nodes[name]
        return cls(host=cfg.get("host", "127.0.0.1"), port=cfg.get("port", 8888), token=cfg.get("token"))

    def ping(self):
        return self._request("/health")

    def system_info(self):
        return self._request("/system")

    def query_files(self, path, max_depth=2):
        return self._request("/query", {"path": path, "max_depth": max_depth})

    def run_cmd(self, cmd):
        return self._request("/exec", {"cmd": cmd})

