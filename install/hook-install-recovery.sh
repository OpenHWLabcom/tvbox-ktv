#!/system/bin/sh
BB=/system/bin/busybox
echo "hook $(date)" >> /data/ktvboot.log
/system/bin/setprop service.adb.tcp.port 5555
/system/bin/setprop persist.service.adb.enable 1
/system/bin/stop adbd 2>/dev/null
/system/bin/start adbd 2>/dev/null
$BB chmod 755 /data/ktvstart.sh 2>/dev/null
$BB sh /data/ktvstart.sh
