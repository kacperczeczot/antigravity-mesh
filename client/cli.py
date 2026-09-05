#!/usr/bin/env python3
"""
Antigravity Mesh CLI Tool
"""

import sys
import json
import argparse

try:
    from .mesh_client import MeshClient
except ImportError:
    from mesh_client import MeshClient


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(prog="agy-mesh", description="Antigravity Mesh CLI")
    parser.add_argument("--node", default="local-mac", help="Node name configured in mesh_nodes.json (default: local-mac)")
    parser.add_argument("--host", default=None, help="Node host (overrides config)")
    parser.add_argument("--port", type=int, default=None, help="Node port (overrides config)")
    parser.add_argument("--token", default=None, help="Security token (overrides config)")

    subparsers = parser.add_subparsers(dest="command", help="Command to run")

    # ping
    subparsers.add_parser("ping", help="Ping node health")

    # system
    subparsers.add_parser("system", help="Get node system info")

    # query
    query_parser = subparsers.add_parser("query", help="Query filesystem path")
    query_parser.add_argument("path", default=".", nargs="?", help="Target directory path")
    query_parser.add_argument("--depth", type=int, default=2, help="Max walk depth (default: 2)")

    # exec
    exec_parser = subparsers.add_parser("exec", help="Execute remote shell command")
    exec_parser.add_argument("cmd", help="Command string to execute")

    # pair
    pair_parser = subparsers.add_parser("pair", help="Zero-Touch pair with a remote node on LAN")
    pair_parser.add_argument("remote_host", help="Remote node IP or hostname")
    pair_parser.add_argument("--port", type=int, default=8888, help="Remote node port (default: 8888)")
    pair_parser.add_argument("--name", default=None, help="My node name to send (optional)")

    # scan
    scan_parser = subparsers.add_parser("scan", help="Scan local network for Antigravity Mesh nodes")
    scan_parser.add_argument("--port", type=int, default=8888, help="Target port (default: 8888)")

    # nodes
    subparsers.add_parser("nodes", help="List configured nodes")

    args = parser.parse_args()

    if args.command == "nodes":
        nodes = MeshClient.load_nodes()
        print(json.dumps(nodes, indent=2))
        return

    if args.command == "pair":
        print(f"🤝 Pairing with {args.remote_host}:{args.port}...")
        res = MeshClient.pair_with(args.remote_host, remote_port=args.port, my_node_name=args.name)
        if "error" in res:
            print(f"❌ Pairing failed: {res['error']}", file=sys.stderr)
            sys.exit(1)
        print(f"✅ Successfully paired with '{res['paired_node']}'!")
        print(json.dumps(res, indent=2))
        return

    if args.command == "scan":
        import socket
        import concurrent.futures

        # Detect local subnet
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(("8.8.8.8", 80))
            local_ip = s.getsockname()[0]
        except Exception:
            local_ip = "127.0.0.1"
        finally:
            s.close()

        subnet_prefix = ".".join(local_ip.split(".")[:3])
        print(f"🔍 Scanning local subnet {subnet_prefix}.0/24 on port {args.port}...")
        found = []

        def check_host(ip):
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(0.3)
            try:
                if sock.connect_ex((ip, args.port)) == 0:
                    return ip
            except Exception:
                pass
            finally:
                sock.close()
            return None

        with concurrent.futures.ThreadPoolExecutor(max_workers=50) as executor:
            candidates = [f"{subnet_prefix}.{i}" for i in range(1, 255)]
            futures = [executor.submit(check_host, ip) for ip in candidates]
            for f in concurrent.futures.as_completed(futures):
                res = f.result()
                if res:
                    found.append(res)
                    print(f"✨ Found node candidate: {res}:{args.port}")

        print(f"Scan complete. Found {len(found)} node(s).")
        return


    # Initialize client
    if args.host:
        client = MeshClient(host=args.host, port=args.port or 8888, token=args.token)
    else:
        try:
            client = MeshClient.from_node(args.node)
        except Exception as e:
            print(f"Error loading node '{args.node}': {e}", file=sys.stderr)
            sys.exit(1)

    if args.command == "ping":
        res = client.ping()
        print(json.dumps(res, indent=2))
    elif args.command == "system":
        res = client.system_info()
        print(json.dumps(res, indent=2))
    elif args.command == "query":
        res = client.query_files(args.path, max_depth=args.depth)
        print(json.dumps(res, indent=2))
    elif args.command == "exec":
        res = client.run_cmd(args.cmd)
        if "stdout" in res and res["stdout"]:
            sys.stdout.write(res["stdout"])
        if "stderr" in res and res["stderr"]:
            sys.stderr.write(res["stderr"])
        if "error" in res:
            print(f"Error: {res['error']}", file=sys.stderr)
            sys.exit(1)
        sys.exit(res.get("returncode", 0))
    else:
        parser.print_help()

if __name__ == "__main__":
    main()
