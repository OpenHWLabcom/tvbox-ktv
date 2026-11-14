#!/usr/bin/env bash
# OpenHW Lab · KTV 点歌台 标准版 v2.0 —— 一键安装（电脑侧，需要 adb）
# 用法: ./一键安装.sh [盒子IP:5555]
set -e
cd "$(dirname "$0")/.."
BOX="${1:-}"
ADB="adb"
[ -n "$BOX" ] && ADB="adb -s $BOX"
echo "==> 1/6 获取 root"
$ADB root || true; sleep 3
[ -n "$BOX" ] && adb connect "$BOX" >/dev/null 2>&1 || true; sleep 1
echo "==> 2/6 挂载 /system 可写"
$ADB shell mount -o remount,rw /system
echo "==> 3/6 备份原桌面并安装 KTV 为主屏"
$ADB shell "busybox mkdir -p /system/app_disabled; for f in /system/app/*auncher*.apk; do [ -f \"\$f\" ] && busybox mv \"\$f\" /system/app_disabled/; done; true"
$ADB push apk/ktv.apk /system/app/KTV.apk
$ADB shell "chmod 644 /system/app/KTV.apk; busybox chown 0:0 /system/app/KTV.apk"
echo "==> 4/6 装拼音词库 + 在线点歌代理"
$ADB push install/py.txt /data/local/py.txt
$ADB push install/ktvnet.py /data/linux/root/ktvnet.py
$ADB push install/ktvstart.sh /data/ktvstart.sh
$ADB shell "chmod 644 /data/local/py.txt; chmod 755 /data/ktvstart.sh; chmod 755 /data/linux/root/ktvnet.py"
echo "==> 5/6 开机自启钩子"
$ADB shell "busybox grep -q 'KTV / ADB hook' /system/etc/init.bigfish.sh || busybox cat /sdcard/hook.sh >> /system/etc/init.bigfish.sh"
$ADB push install/hook-install-recovery.sh /sdcard/hook.sh 2>/dev/null || true
$ADB shell "sync"
echo "==> 6/6 重启生效"
$ADB reboot
echo "完成。重启后盒子会直接进入 KTV 点歌台，右下角二维码可手机扫码点歌。"
