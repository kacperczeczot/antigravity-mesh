---
name: antigravity-mesh
description: Enables Antigravity to communicate with remote machines (Windows, Mac, Linux) running the antigravity-mesh daemon to query files, inspect directories, or trigger remote jobs.
---

# Antigravity Mesh Skill

Use this skill when the user asks about the state of another machine, wants to inspect files on their secondary PC (e.g. Windows workstation), or needs cross-machine synchronization.

## Available Nodes
Configure nodes in `~/.gemini/mesh_nodes.json`:
```json
{
  "windows-pc": {
    "host": "192.168.68.51",
    "port": 8888,
    "token": "secret"
  }
}
```

## How to Query Remote Node
```python
from client.mesh_client import MeshClient

client = MeshClient(host="192.168.68.51", port=8888)
res = client.query_files("C:\\Projects")
print(res)
```
