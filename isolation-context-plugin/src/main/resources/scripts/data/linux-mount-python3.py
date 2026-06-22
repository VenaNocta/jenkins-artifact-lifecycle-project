import sys
import os
import shutil
import subprocess


def mkdir(path):
    path = os.path.abspath(path)
    uid = int(os.environ.get("SUDO_UID", os.getuid()))
    gid = int(os.environ.get("SUDO_GID", os.getgid()))
    # walk FS tree until path is reached, chown all
    current = "/"
    for component in path.strip("/").split("/"):
        current = os.path.join(current, component)
        try:
            os.mkdir(current)
            os.chown(current, uid, gid)
        except FileExistsError:
            pass


# rootPath=/
# overlayRootPath=/var/jenkins/iso/<job>/
# mountPath=/var/jenkins/iso/<job>/rootfs
def mount_overlay_create(rootPath, overlayRootPath, mountPath):
    overlayUpperPath = overlayRootPath + "/upper"
    overlayWorkPath = overlayRootPath + "/work"

    mkdir(overlayUpperPath)
    mkdir(overlayWorkPath)
    mkdir(mountPath)

    subprocess.run(
        [
            "mount", "-t", "overlay", "overlay",
            "--options", "lowerdir=" + rootPath + ",upperdir=" + overlayUpperPath + ",workdir=" + overlayWorkPath,
            mountPath
        ],
        check=True,
    )


# overlayRootPath=/var/jenkins/iso/<job>/
# mountPath=/var/jenkins/iso/<job>/rootfs
def mount_overlay_cleanup(overlayRootPath, mountPath):
    subprocess.run(
        [
            "umount", "--recursive", "--lazy", mountPath
        ],
        check=True,
    )
    shutil.rmtree(overlayRootPath)


# ----------------------------
# Main
# ----------------------------
def main():
    if len(sys.argv) < 2:
        print("""\
usage: script.py setup/shutdown\
                 mount overlay create/cleanup <basePath>\
        """)
        sys.exit(1)

    op = sys.argv[1]

    if op == "setup":
        # selftest?
        sys.exit(0)
    elif op == "shutdown":
        # noop
        sys.exit(0)
    elif op == "mount":
        op = sys.argv[2]

        if op == "overlay":
            if len(sys.argv) != 5:
                print("<basePath> required!")
                sys.exit(1)
            op = sys.argv[3]
            if op == "create":
                mount_overlay_create("/", sys.argv[4].strip(), sys.argv[4].strip() + "/rootfs")
            elif op == "cleanup":
                mount_overlay_cleanup(sys.argv[4].strip(), sys.argv[4].strip() + "/rootfs")
            else:
                print("unknown op » mount overlay " + op)
                sys.exit(1)

        else:
            print("unknown op » mount " + op)
            sys.exit(1)
    else:
        print("unknown op » " + op)
        sys.exit(1)


if __name__ == "__main__":
    main()
