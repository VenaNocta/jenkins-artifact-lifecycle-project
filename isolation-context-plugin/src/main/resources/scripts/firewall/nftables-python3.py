import sys
import csv
import json
from nftables import Nftables
import subprocess

TABLE = "jenkins_ci_iso"
SET_HOSTIF = "host_ifs"
SET_V4_TCP = "input_allowed_v4_tcp"
SET_V4_UDP = "input_allowed_v4_udp"
SET_V6_TCP = "input_allowed_v6_tcp"
SET_V6_UDP = "input_allowed_v6_udp"


# ----------------------------
# CSV parsing
# ----------------------------
def load_port_csv(path):
    entries = set()
    with open(path, newline="") as f:
        reader = csv.reader(f, delimiter="|")
        for row in reader:
            if len(row) != 5:
                continue
            family, host, external, protocol, port = row
            entries.add((family.strip(), host.strip(), external.strip(), protocol.strip().lower(), int(port)))
    return entries


def load_hostif_csv(path):
    entries = set()
    with open(path, newline="") as f:
        reader = csv.reader(f, delimiter="|")
        for row in reader:
            if len(row) != 1:
                continue
            hostif = row[0]
            entries.add((hostif.strip()))
    return entries


# ----------------------------
# Helper
# ----------------------------
def to_elems(entries):
    return [
        {"concat": [extIp, port, hostIp]}
        for (hostIp, extIp, port) in entries
    ]


def resolve_set(family, proto):
    if family == "4":
        if proto == "tcp":
            return SET_V4_TCP
        if proto == "udp":
            return SET_V4_UDP

    if family == "6":
        if proto == "tcp":
            return SET_V6_TCP
        if proto == "udp":
            return SET_V6_UDP

    raise ValueError(f"invalid combination: {family}, {proto}")


def group_entries(entries):
    groups = {
        SET_V4_TCP: set(),
        SET_V4_UDP: set(),
        SET_V6_TCP: set(),
        SET_V6_UDP: set()
    }
    for family, host, external, proto, port in entries:
        set_name = resolve_set(family, proto)
        groups[set_name].add((host, external, port))
    return groups


# ----------------------------
# NFT interface
# ----------------------------
def run_json(nft, obj, ignoreError=False):
    rc, out, err = nft.json_cmd(obj)
    if ignoreError == False and rc != 0:
        print(obj)
        raise RuntimeError(err)
    return out


def run_string(nft, cmd):
    rc, out, err = nft.cmd(cmd)
    if rc != 0:
        raise RuntimeError(err)
    return out


# ----------------------------
# FirewallD interface
# ----------------------------

def has_firewalld_cmd():
    try:
        subprocess.run(
            ["firewall-cmd", "--version"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=True
        )
        return True
    except (FileNotFoundError, subprocess.CalledProcessError):
        return False


def zone_trusted_add(interfaces):
    for iface in interfaces:
        subprocess.run(["firewall-cmd", "--zone=trusted", "--add-interface=" + iface], check=True)


def zone_trusted_del(interfaces):
    for iface in interfaces:
        subprocess.run(["firewall-cmd", "--zone=trusted", "--remove-interface=" + iface], check=True)


def zone_trusted_clear():
    result = subprocess.run(
        ["firewall-cmd", "--zone=trusted", "--list-interfaces"],
        capture_output=True,
        text=True,
        check=True,
    )
    for iface in result.stdout.split():
        if iface.startswith("jciv-"):
            subprocess.run(
                [
                    "firewall-cmd",
                    "--zone=trusted",
                    "--remove-interface", iface,
                ],
                check=True,
            )


# ----------------------------
# Setup/Destroy (idempotent)
# ----------------------------
def setup(nft):
    cmd = {
        "nftables": [
            {
                "add": {
                    "table": {
                        "family": "inet",
                        "name": TABLE
                    }
                }
            },
            {
                "add": {
                    "set": {
                        "family": "inet",
                        "table": TABLE,
                        "name": SET_HOSTIF,
                        "type": "ifname"
                    }
                }
            },
            {
                "add": {
                    "set": {
                        "family": "inet",
                        "table": TABLE,
                        "name": SET_V4_TCP,
                        "type": [
                            "ipv4_addr",
                            "inet_service",
                            "ipv4_addr"
                        ],
                        "flags": ["interval"]
                    }
                }
            },
            {
                "add": {
                    "set": {
                        "family": "inet",
                        "table": TABLE,
                        "name": SET_V4_UDP,
                        "type": [
                            "ipv4_addr",
                            "inet_service",
                            "ipv4_addr"
                        ],
                        "flags": ["interval"]
                    }
                }
            },
            {
                "add": {
                    "set": {
                        "family": "inet",
                        "table": TABLE,
                        "name": SET_V6_TCP,
                        "type": [
                            "ipv6_addr",
                            "inet_service",
                            "ipv6_addr"
                        ],
                        "flags": ["interval"]
                    }
                }
            },
            {
                "add": {
                    "set": {
                        "family": "inet",
                        "table": TABLE,
                        "name": SET_V6_UDP,
                        "type": [
                            "ipv6_addr",
                            "inet_service",
                            "ipv6_addr"
                        ],
                        "flags": ["interval"]
                    }
                }
            },
            {
                "add": {
                    "chain": {
                        "family": "inet",
                        "table": TABLE,
                        "name": "filter_FORWARD",
                        "type": "filter",
                        "hook": "forward",
                        "prio": 0
                    }
                }
            },
            {
                "rule": {
                    "family": "inet",
                    "table": TABLE,
                    "chain": "filter_FORWARD",
                    "expr": [
                        {
                            "match": {
                                "op": "in",
                                "left": {"meta": {"key": "iifname"}},
                                "right": "@" + SET_HOSTIF
                            }
                        },
                        {
                            "reject": None
                        }
                    ]
                }
            },
            {
                "add": {
                    "chain": {
                        "family": "inet",
                        "table": TABLE,
                        "name": "filter_INPUT",
                        "type": "filter",
                        "hook": "input",
                        "prio": 0
                    }
                }
            },
            {
                "add": {
                    "chain": {
                        "family": "inet",
                        "table": TABLE,
                        "name": "host_only_IN_allow",
                        "type": "filter",
                    }
                }
            },
            {
                "rule": {
                    "family": "inet",
                    "table": TABLE,
                    "chain": "filter_INPUT",
                    "expr": [
                        {
                            "match": {
                                "op": "in",
                                "left": {"meta": {"key": "iifname"}},
                                "right": "@" + SET_HOSTIF
                            }
                        },
                        {
                            "goto": {
                                "target": "host_only_IN_allow"
                            }
                        }
                    ]
                }
            },
        ]
    }
    run_json(nft, {"nftables": [{"delete": {"table": {"family": "inet", "name": TABLE}}}]}, True)
    run_json(nft, cmd)
    run_string(nft,
               "add rule inet " + TABLE + " host_only_IN_allow ip saddr . tcp dport . ip daddr @" + SET_V4_TCP + " accept")
    run_string(nft,
               "add rule inet " + TABLE + " host_only_IN_allow ip saddr . udp dport . ip daddr @" + SET_V4_UDP + " accept")
    run_string(nft,
               "add rule inet " + TABLE + " host_only_IN_allow ip6 saddr . tcp dport . ip6 daddr @" + SET_V6_TCP + " accept")
    run_string(nft,
               "add rule inet " + TABLE + " host_only_IN_allow ip6 saddr . udp dport . ip6 daddr @" + SET_V6_UDP + " accept")
    run_string(nft,
               "add rule inet " + TABLE + " host_only_IN_allow reject")


def shutdown(nft):
    cmd = {
        "nftables": [
            {
                "delete": {
                    "table": {
                        "family": "inet",
                        "name": TABLE
                    }
                }
            }
        ]
    }
    run_json(nft, cmd, True)
    if has_firewalld_cmd():
        zone_trusted_clear()


# ----------------------------
# Add
# ----------------------------
def port_add(nft, entries, cmdCol=[]):
    groups = group_entries(entries)

    for setName, entries in groups.items():
        if not entries:
            continue

        cmdCol.append({
            "add": {
                "element": {
                    "family": "inet",
                    "table": TABLE,
                    "name": setName,
                    "elem": to_elems(entries)
                }
            }
        })

    if not cmdCol:
        return

    run_json(nft, {"nftables": cmdCol})


def hostif_add(nft, interfaces, cmdCol=[]):
    if not interfaces:
        return

    cmdCol.append({
        "add": {
            "element": {
                "family": "inet",
                "table": TABLE,
                "name": SET_HOSTIF,
                "elem": list(interfaces)
            }
        }
    })

    run_json(nft, {"nftables": cmdCol})
    if has_firewalld_cmd():
        zone_trusted_add(interfaces)


# ----------------------------
# Delete
# ----------------------------
def port_del(nft, entries):
    groups = group_entries(entries)
    cmdCol = []

    for setName, entries in groups.items():
        if not entries:
            continue

        cmdCol.append({
            "delete": {
                "element": {
                    "family": "inet",
                    "table": TABLE,
                    "name": setName,
                    "elem": to_elems(entries)
                }
            }
        })

    if not cmdCol:
        return

    cmd = {
        "nftables": cmdCol
    }
    run_json(nft, cmd)


def hostif_del(nft, interfaces):
    if not interfaces:
        return

    cmd = {
        "nftables": [
            {
                "delete": {
                    "element": {
                        "family": "inet",
                        "table": TABLE,
                        "name": SET_HOSTIF,
                        "elem": list(interfaces)
                    }
                }
            }
        ]
    }

    run_json(nft, cmd)
    if has_firewalld_cmd():
        zone_trusted_del(interfaces)


# ----------------------------
# Retain
# ----------------------------
def port_set(nft, entries):
    cmdCol = []
    # flush then re-add
    for setName in [SET_V4_TCP, SET_V4_UDP, SET_V6_TCP, SET_V6_UDP]:
        cmdCol.append({
            "flush": {
                "set": {
                    "family": "inet",
                    "table": TABLE,
                    "name": setName
                }
            }
        })
    # pass flush cmds to port_add function
    port_add(nft, entries, cmdCol)


def hostif_set(nft, interfaces):
    cmdCol = []
    # flush then re-add
    cmdCol.append({
        "flush": {
            "set": {
                "family": "inet",
                "table": TABLE,
                "name": SET_HOSTIF
            }
        }
    })

    # pass flush cmds to hostif_add function
    hostif_add(nft, interfaces, cmdCol)
    if has_firewalld_cmd():
        zone_trusted_clear()
        zone_trusted_add(interfaces)


# ----------------------------
# Clear
# ----------------------------
def port_clear(nft):
    cmdCol = []
    for setName in [SET_V4_TCP, SET_V4_UDP, SET_V6_TCP, SET_V6_UDP]:
        cmdCol.append({
            "flush": {
                "set": {
                    "family": "inet",
                    "table": TABLE,
                    "name": setName
                }
            }
        })
    run_json(nft, {"nftables": cmdCol})


def hostif_clear(nft):
    run_json(nft, {
        "nftables": [
            {
                "flush": {
                    "set": {
                        "family": "inet",
                        "table": TABLE,
                        "name": SET_HOSTIF
                    }
                }
            }
        ]
    })
    if has_firewalld_cmd():
        zone_trusted_clear()


# ----------------------------
# Main
# ----------------------------
def main():
    if len(sys.argv) < 2:
        print("""\
usage: script.py setup/shutdown\
                 set traffic-port/hostif add/del/set/clear [csv-path]\
        """)
        sys.exit(1)

    op = sys.argv[1]

    nft = Nftables()
    nft.set_json_output(True)

    if op == "setup":
        setup(nft)
    elif op == "shutdown":
        shutdown(nft)
    elif op == "set":
        main_set(nft, sys.argv)
    else:
        print("unknown op: " + op)
        sys.exit(1)


def main_set(nft, argv):
    op = argv[2]

    if op == "traffic-port":
        op = argv[3]
        if op == "clear":
            port_clear(nft)
            return

        if len(argv) != 5:
            print("csv required for this operation")
            sys.exit(1)

        entries = load_port_csv(argv[4])

        if op == "add":
            port_add(nft, entries)
        elif op == "del":
            port_del(nft, entries)
        elif op == "set":
            port_set(nft, entries)

    elif op == "hostif":
        op = argv[3]
        if op == "clear":
            hostif_clear(nft)
            return

        if len(argv) != 5:
            print("csv required for this operation")
            sys.exit(1)

        entries = load_hostif_csv(argv[4])

        if op == "add":
            hostif_add(nft, entries)
        elif op == "del":
            hostif_del(nft, entries)
        elif op == "set":
            hostif_set(nft, entries)

    else:
        print("unknown op: " + op)
        sys.exit(1)


if __name__ == "__main__":
    main()
