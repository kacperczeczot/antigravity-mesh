"""
Antigravity Mesh Client
Moduł używany przez agenta Antigravity do komunikacji z węzłami sieciowymi.
"""

import json
import urllib.request
import urllib.error

__version__ = "1.3.6"

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

    @classmethod
    def pair_with(cls, remote_host, remote_port=8888, my_node_name=None, my_port=8888, config_path=None):
        import os
        import platform
        import secrets

        cfg_file = config_path or os.path.expanduser("~/.gemini/mesh_nodes.json")
        os.makedirs(os.path.dirname(cfg_file), exist_ok=True)
        nodes = cls.load_nodes(cfg_file)

        # Get or generate our local token
        my_token = None
        for k in ["local", "local-mac", "self"]:
            if k in nodes and "token" in nodes[k]:
                my_token = nodes[k]["token"]
                break
        if not my_token:
            my_token = secrets.token_hex(16)
            nodes["local"] = {"host": "127.0.0.1", "port": my_port, "token": my_token}

        my_name = my_node_name or platform.node()

        temp_client = cls(host=remote_host, port=remote_port)
        res = temp_client._request("/pair", {
            "node_name": my_name,
            "port": my_port,
            "token": my_token
        })

        if "error" in res:
            return res

        remote_name = res.get("node_name") or f"node-{remote_host.replace('.', '-')}"
        remote_token = res.get("token")

        nodes[remote_name] = {
            "host": remote_host,
            "port": remote_port,
            "token": remote_token
        }

        with open(cfg_file, "w", encoding="utf-8") as f:
            json.dump(nodes, f, indent=2)

        return {
            "status": "success",
            "paired_node": remote_name,
            "host": remote_host,
            "port": remote_port,
            "token": remote_token
        }

    def ping(self):
        return self._request("/health")

    def system_info(self):
        return self._request("/system")

    def query_files(self, path, max_depth=2):
        return self._request("/query", {"path": path, "max_depth": max_depth})

    def run_cmd(self, cmd):
        return self._request("/exec", {"cmd": cmd})

    def ask_agent(self, prompt, auto_approve=True):
        return self._request("/ask", {"question": prompt, "auto_approve": auto_approve})



