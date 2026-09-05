---
name: antigravity-mesh
description: Enables Antigravity to communicate with remote machines (Windows, Mac, Linux) running the antigravity-mesh daemon to query files, inspect directories, or trigger remote jobs.
---

# Antigravity Mesh Skill

Use this skill when the user asks about the state of another machine, wants to inspect files on their secondary PC (e.g. Windows workstation), or needs cross-machine synchronization.

## Available Nodes
Configured nodes are saved in `~/.gemini/mesh_nodes.json`:
```json
{
  "local-mac": {
    "host": "127.0.0.1",
    "port": 8888,
    "token": "secret"
  },
  "windows-pc": {
    "host": "192.168.68.51",
    "port": 8888,
    "token": "secret"
  }
}
```

## How to Query Nodes

### 1. Python Client (Direct)
```python
from client.mesh_client import MeshClient

# Load from configured node name:
client = MeshClient.from_node("local-mac") # or "windows-pc"

# Query health
health = client.ping()

# Query hardware/system info
sys_info = client.system_info()

# Query filesystem
files = client.query_files("/Volumes/MAC_STORAGE_APFS", max_depth=2)

# Execute command
res = client.run_cmd("ls -la")
```

### 2. CLI
```bash
python3 client/cli.py ping --node local-mac
python3 client/cli.py system --node local-mac
python3 client/cli.py query /path/to/dir --depth 2 --node windows-pc
python3 client/cli.py exec "nvidia-smi" --node windows-pc
```

