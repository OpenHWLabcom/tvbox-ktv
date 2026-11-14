#!/system/bin/sh
# 开机拉起「在线点歌」代理（chroot 内 python3）
# 需要先在 /data/linux 装好 armhf Linux + python3，并放好 /data/linux/root/ktvnet.py
BB=/system/bin/busybox
L=/data/linux
$BB mkdir -p $L/proc $L/sys $L/dev
$BB mount -t proc proc $L/proc 2>/dev/null
$BB mount -t sysfs sys $L/sys 2>/dev/null
$BB mount -o bind /dev $L/dev 2>/dev/null
printf 'nameserver 223.5.5.5\nnameserver 114.114.114.114\n' > $L/etc/resolv.conf
$BB pkill -f ktvnet.py 2>/dev/null
$BB chroot $L /usr/bin/python3 /root/ktvnet.py >>/data/ktvnet.log 2>&1 </dev/null &
echo "ktvnet started $(date)" >> /data/ktvboot.log
