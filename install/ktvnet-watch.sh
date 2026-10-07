#!/bin/sh
# KTV 在线点歌后端守护: 端口探测(不用 pgrep), 每 25s 确认 :8089 在听, 否则拉起。
# ⚠ 必须用绝对路径: 开机由 start-stop-daemon 拉起时 PATH 不含 /usr/bin。
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
PY=/usr/bin/python3
while true; do
  if ! $PY -c "import socket;s=socket.socket();s.settimeout(3);s.connect(('127.0.0.1',8089))" >/dev/null 2>&1; then
    nohup $PY /root/ktvnet.py >>/var/log/ktvnet.log 2>&1 </dev/null &
    sleep 4
  fi
  sleep 25
done
