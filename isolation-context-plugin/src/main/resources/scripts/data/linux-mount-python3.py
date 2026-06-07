import sys
import os
import shutil
import subprocess


def mkdir(path):
    try:
        os.makedirs(path)
        # TODO chown to user who executed this scrip (via sudo!)
    except FileExistsError:
        pass


def umount(mountPath):
    subprocess.run(
        [
            "umount", mountPath
        ],
        check=True,
    )


# rootPath=/
# overlayRootPath=/var/jenkins/iso/<job>/
# mountPath=/var/jenkins/iso/<job>/root
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
# mountPath=/var/jenkins/iso/<job>/root
def mount_overlay_cleanup(overlayRootPath, mountPath):
    subprocess.run(
        [
            "umount", "--recursive", "--lazy", mountPath
        ],
        check=True,
    )
    shutil.rmtree(overlayRootPath)


# bindPath=/var/jenkins/workspace/<job>@extra
# mountPath=/var/jenkins/iso/<job>/root/var/jenkins/workspace/<job>@extra
def mount_bind(bindPath, mountPath):
    mkdir(bindPath)
    mkdir(mountPath)

    subprocess.run(
        [
            "mount", "--bind", bindPath, mountPath
        ],
        check=True,
    )


# mountPath=/var/jenkins/iso/<job>/root/var/jenkins/workspace/<job>@extra
def mount_unbind(mountPath):
    umount(mountPath)


# exportPath=/run/jenkins/workspace-export/<job>
# mountPath=/var/jenkins/iso/<job>/root/var/jenkins/workspace
def mount_export_create(exportPath, mountPath):
    mount_bind(exportPath, mountPath)
    subprocess.run(
        [
            "mount", "--make-rshared", mountPath
        ],
        check=True,
    )


# ----------------------------
# Main
# ----------------------------
def main():
    if len(sys.argv) < 2:
        print("""\
usage: script.py setup/shutdown\
                 mount overlay create/cleanup <basePath>\
                 mount export <exportPath> <mountPath>\
                 mount bind <bindPath> <mountPath>\
                 mount unbind <mountPath>
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
                mount_overlay_create("/", sys.argv[4].strip(), sys.argv[4].strip() + "/root")
            elif op == "cleanup":
                mount_overlay_cleanup(sys.argv[4].strip(), sys.argv[4].strip() + "/root")
            else:
                print("unknown op » mount overlay " + op)
                sys.exit(1)

        elif op == "export":
            if len(sys.argv) != 5:
                print("<exportPath> & <mountPath> required!")
                sys.exit(1)
            mount_export_create(sys.argv[3].strip(), sys.argv[4].strip())

        elif op == "bind":
            if len(sys.argv) != 5:
                print("<bindPath> & <mountPath> required!")
                sys.exit(1)
            mount_bind(sys.argv[3].strip(), sys.argv[4].strip())

        elif op == "unbind":
            if len(sys.argv) != 4:
                print("<mountPath> required!")
                sys.exit(1)
            mount_bind(sys.argv[3].strip())

        else:
            print("unknown op » mount " + op)
            sys.exit(1)
    else:
        print("unknown op » " + op)
        sys.exit(1)


if __name__ == "__main__":
    main()
