# Isolation Context

## Basic - Linux Isolation Context

### Setup

```bash
# required capability changes
sudo setcap cap_sys_admin+ep /usr/bin/unshare
```

```bash
cat /etc/sudoers.d/jenkins-isolation-context 
jenkins ALL=(root) NOPASSWD: \
  /usr/bin/nsenter --target=* --mount --uts --ipc --net --pid --wd -- *, \
  /usr/bin/nsenter --target=* --net -- *, \
  /usr/sbin/ip link add *, \
  /usr/sbin/ip link set * netns *, \
  /usr/sbin/ip link del *, \
  /usr/sbin/ip addr add *, \
  /usr/sbin/ip link set * up, \
  /usr/bin/python3 *
```

