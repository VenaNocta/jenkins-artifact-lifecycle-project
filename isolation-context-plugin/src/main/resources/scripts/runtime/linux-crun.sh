set -e

uid="${SUDO_UID:-$(id -u)}"
gid="${SUDO_GID:-$(id -g)}"

setup() {
  # NOOP
  return 0
}

shutdown() {
  # NOOP
  return 0
}

# RUN
# cnt_run <cntId> <bundlePath> <pidFilePath>
cnt_run() {
# crun run --bundle=/var/jenkins/workspace/job@overlay -d --pid-file=/var/jenkins/workspace/job@overlay/handle.pid jenkins_job
  crun run --bundle="${2}" -d --pid-file="${3}" "${1}"
  result=$?
  chown "$uid:$gid" "${3}"
  return $result
}

# KILL
# cnt_kill <pId> <cntId>
cnt_kill() {
# kill -KILL -$(cat /var/jenkins/workspace/job@overlay/handle.pid)
  kill -KILL -${1}
# crun delete jenkins_job
  crun delete "${2}"
  return $?
}

# cnt_mounts_add <pId> <cntId> <mounts.config> 
cnt_mounts_add() {
  crun mounts add "${2}" "${3}"
  return $?
}

# cnt_mounts_remove <pId> <cntId> <mounts.config> 
cnt_mounts_remove() {
  crun mounts remove "${2}" "${3}"
  return $?
}


#------------------------------------------------------------------------------
# START EXEC
#------------------------------------------------------------------------------

if [ "$1" = "setup" ]; then
  shift 1
  setup $@
  exit $?
elif [ "$1" = "shutdown" ]; then
  shift 1
  shutdown $@
  exit $?
elif [ "$1" = "run" ]; then
  shift 1
  cnt_run $@
  exit $?
elif [ "$1" = "kill" ]; then
  shift 1
  cnt_kill $@
  exit $?
elif [ "$1" = "mounts" ]; then
  if [ "$2" = "add" ]; then
    shift 2
    cnt_mounts_add $@
    exit $?
  elif [ "$1" = "remove" ]; then
    shift 2
    cnt_mounts_remove $@
    exit $?
  fi
fi
exit 1

