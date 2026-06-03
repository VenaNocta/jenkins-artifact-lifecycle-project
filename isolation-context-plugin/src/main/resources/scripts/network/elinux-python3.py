import sys
import os
import subprocess

HOSTIF_PREFIX = "jciv-h"
UDEV_FILE = "/etc/udev/rules.d/99-jenkins-jci-unmanaged.rules"
SYSTEMD_LINK_FILE = "/etc/systemd/network/99-jenkins-jci.link"


# ----------------------------
# Setup/Destroy (idempotent)
# ----------------------------
def write_udev_rule():
    os.makedirs("/etc/udev/rules.d", exist_ok=True)
    with open(UDEV_FILE, "w") as f:
        f.write('SUBSYSTEM=="net", ACTION=="add", KERNEL=="' + HOSTIF_PREFIX + '*", ENV{NM_UNMANAGED}="1"\n')
    # reload udev rules
    subprocess.run(["udevadm", "control", "--reload-rules"], check=False)
    subprocess.run(["udevadm", "trigger"], check=False)


def remove_udev_rule():
    try:
        os.remove(UDEV_FILE)
        # reload udev rules
        subprocess.run(["udevadm", "control", "--reload-rules"], check=False)
        subprocess.run(["udevadm", "trigger"], check=False)
    except FileNotFoundError:
        pass


def write_systemd_link():
    os.makedirs("/etc/systemd/network", exist_ok=True)
    with open(SYSTEMD_LINK_FILE, "w") as f:
        f.write("[Match]\nOriginalName=" + HOSTIF_PREFIX + "*\n\n[Link]\nUnmanaged=yes\n")


def remove_systemd_link():
    try:
        os.remove(SYSTEMD_LINK_FILE)
    except FileNotFoundError:
        pass


def setup(nft):
    write_udev_rule()
    write_udev_rule()


def shutdown(nft):
    remove_udev_rule()
    remove_systemd_link()


# ----------------------------
# Main
# ----------------------------
def main():
    if len(sys.argv) < 2:
        print("""\
usage: script.py setup/shutdown\
        """)
        sys.exit(1)

    op = sys.argv[1]

    nft = Nftables()
    nft.set_json_output(True)

    if op == "setup":
        setup(nft)
    elif op == "shutdown":
        shutdown(nft)
    else:
        print("unknown op: " + op)
        sys.exit(1)


if __name__ == "__main__":
    main()
