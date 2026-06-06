so the sequence should be:

```bash


# host

# build overlay - make it private
mkdir -p /var/jenkins/iso/job/{upper,work,root}
mount -t overlay overlay \
    -o lowerdir=/,upperdir=/var/jenkins/iso/job/upper,workdir=/var/jenkins/iso/job/work \
    /var/jenkins/iso/job/root
#mount --make-rprivate /var/jenkins/iso/job/root

# build export
mkdir -p /run/jenkins/workspace-export/job /var/jenkins/iso/job/root/var/jenkins/workspace
mount --bind /run/jenkins/workspace-export/job /var/jenkins/iso/job/root/var/jenkins/workspace
mount --make-rshared /var/jenkins/iso/job/root/var/jenkins/workspace

# create namespace
sudo setsid unshare --mount --propagation=slave --uts --ipc --net --pid --fork --mount-proc --wd=/var/jenkins/workspace/job/ -- switch_root /var/jenkins/iso/job/root /usr/bin/bash -c "exec tail -f /dev/null"

# enter
sudo nsenter --target=208477 --mount --uts --ipc --net --pid -- setpriv --reuid=1000 --regid=1000 --groups=basti --inh-caps=-all --reset-env -- /usr/bin/bash

# cleanup
umount /var/jenkins/iso/job/root/var/jenkins/workspace
umount /var/jenkins/iso/job/root
rm -rf /var/jenkins/iso/job/{upper,work,root}

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

```

