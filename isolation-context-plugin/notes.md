so the sequence should be:

```bash


# host

# build overlay
sudo mkdir -p /var/jenkins/workspace/job@overlay/{upper,work,rootfs}
sudo mount -t overlay overlay \
    -o lowerdir=/,upperdir=/var/jenkins/workspace/job@overlay/upper,workdir=/var/jenkins/workspace/job@overlay/work \
    /var/jenkins/workspace/job@overlay/rootfs

# create namespace
#sudo setsid unshare --mount --propagation=slave --uts --ipc --net --pid --fork --mount-proc --wd=/var/jenkins/workspace/job/ -- switch_root /var/jenkins/iso/job/root /usr/bin/bash -c "exec tail -f /dev/null"

# enter
sudo nsenter --target=208477 --mount --uts --ipc --net --pid --wd -- setpriv --reuid=1000 --regid=1000 --groups=basti --inh-caps=-all --reset-env -- /usr/bin/bash

# cleanup
sudo umount /var/jenkins/workspace/job@overlay/rootfs
sudo rm -rf /var/jenkins/workspace/job@overlay/{upper,work,rootfs}

####

# so later I can do:

# host
mkdir -p /var/jenkins/iso/job/root/var/jenkins/workspace/job-3@extra
mount --bind \
  /var/jenkins/workspace/job-3@extra \
  /var/jenkins/iso/job/root/var/jenkins/workspace/job-3@extra

# cleanup
umount /var/jenkins/iso/job/root/var/jenkins/workspace/job-3@extra

# and it will appear as /extra inside the namespace!

# diagnostics
findmnt -o TARGET,PROPAGATION

```

bundle: config.json

```json
{
  "ociVersion": "1.0.2",

  "process": {
    "terminal": false,
    "user": { "uid": 1000, "gid": 1000 },
    "args": ["/usr/bin/tail", "-f", "/dev/null"],
    "env": [],
    "cwd": "/"
  },

  "root": {
    "path": "rootfs",
    "readonly": false
  },
  "mounts": [
    {
      "destination": "/proc",
      "type": "proc",
      "source": "proc"
    },
    {
      "destination": "/tmp",
      "type": "tmpfs",
      "source": "tmpfs",
      "options": [
        "nosuid",
        "nodev",
        "mode=1777",
        "size=1g"
      ]
    },
    {
      "destination": "/dev",
      "type": "tmpfs",
      "source": "tmpfs",
      "options": [
        "nosuid",
        "strictatime",
        "mode=755",
        "size=64m"
      ]
    },
    {
      "destination": "/dev/null",
      "type": "bind",
      "source": "/dev/null",
      "options": ["rbind","rw"]
    },
    {
      "destination": "/dev/zero",
      "type": "bind",
      "source": "/dev/zero",
      "options": ["rbind","rw"]
    },
    {
      "destination": "/dev/full",
      "type": "bind",
      "source": "/dev/full",
      "options": ["rbind","rw"]
    },
    {
      "destination": "/dev/random",
      "type": "bind",
      "source": "/dev/random",
      "options": ["rbind","rw"]
    },
    {
      "destination": "/dev/urandom",
      "type": "bind",
      "source": "/dev/urandom",
      "options": ["rbind","rw"]
    },
    {
      "destination": "/dev/shm",
      "type": "tmpfs",
      "source": "tmpfs",
      "options": [
        "nosuid",
        "nodev",
        "mode=1777",
        "size=64m"
      ]
    },
    {
      "destination": "/boot",
      "type": "tmpfs",
      "source": "tmpfs",
      "options": [
        "mode=700",
        "size=64k"
      ]
    },
    {
      "destination": "/var/jenkins",
      "type": "tmpfs",
      "source": "tmpfs",
      "options": [
        "mode=755",
        "size=64m"
      ]
    },
    {
      "destination": "/var/jenkins/workspace/job",
      "type": "bind",
      "source": "/var/jenkins/workspace/job",
      "options": [
        "bind",
        "rw",
        "rshared"
      ]
    },
    {
      "destination": "/var/jenkins/workspace/job@tmp",
      "type": "bind",
      "source": "/var/jenkins/workspace/job@tmp",
      "options": [
        "bind",
        "rw",
        "rshared"
      ]
    }
  ],

  "hostname": "jenkins-agent",

  "linux": {
    "namespaces": [
      { "type": "uts" },
      { "type": "ipc" },
      { "type": "network" },
      { "type": "pid" },
      { "type": "mount" }
    ]
  }
}

```

