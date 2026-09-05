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

    # nodes
    subparsers.add_parser("nodes", help="List configured nodes")

    args = parser.parse_args()

    if args.command == "nodes":
        nodes = MeshClient.load_nodes()
        print(json.dumps(nodes, indent=2))
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
