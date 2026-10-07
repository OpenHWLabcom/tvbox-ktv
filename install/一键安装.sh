#!/system/bin/sh
# ================================================================
# B860AV 机顶盒 → 家庭影院 KTV 点歌台 · 一键安装
# 在【盒子上以 root/shell】运行（先开 ADB 或串口 root shell）。
# 用法：把整个安装目录拷到盒子，例如 /sdcard/ktv-install/，然后：
#     sh /sdcard/ktv-install/一键安装.sh
# ================================================================
D=$(cd "$(dirname "$0")" && pwd)
R=/data/linux            # Linux 子系统(chroot) 路径，按你的机器改
SL=/data/startlinux.sh   # 盒子开机脚本(可选) —— 有就往里加自启
BF=/data/bootfix.sh      # 开机兜底脚本(可选)

echo "== [1/4] 安装点歌台 App =="
pm install -r "$D/ktv.apk" || { echo "安装失败：确认已 root / 有 pm 权限"; exit 1; }

echo "== [2/4] 部署在线后端到子系统 $R =="
if [ -d "$R/root" ]; then
  cp -f "$D/ktvnet.py"        "$R/root/ktvnet.py"
  cp -f "$D/ktvnet-watch.sh"  "$R/root/ktvnet-watch.sh"
  chmod +x "$R/root/ktvnet-watch.sh"
  echo "   已放入 $R/root/"
else
  echo "   ⚠ 没找到子系统目录 $R —— 请手动把 ktvnet.py / ktvnet-watch.sh 拷进去"
fi

echo "== [3/4] 配开机自启 =="
if [ -f "$SL" ]; then
  if ! grep -q ktvnet "$SL"; then
    printf '\n# KTV 在线点歌后端 (:8089, 网易云)\nchroot %s /sbin/start-stop-daemon --start --background --make-pidfile --pidfile /run/ktvnet.pid --exec /bin/sh -- /root/ktvnet-watch.sh 2>/dev/null\n' "$R" >> "$SL"
    echo "   已写入 $SL"
  else
    echo "   $SL 已存在 ktvnet 配置，跳过"
  fi
else
  echo "   ⚠ 没找到 $SL —— 请手动在开机脚本里拉起 ktvnet-watch.sh"
fi
if [ -f "$BF" ]; then
  sed -i 's/com\.openhardwarelab\.desktop/com.openhardwarelab.ktv/g' "$BF" 2>/dev/null
  echo "   已把 $BF 的兜底包名改成本 App"
fi

echo "== [4/4] 设为唯一桌面(HOME) =="
echo "   Android 4.4 无 pm set-home-activity，做法是【卸载旧桌面】后本 App 即默认桌面："
echo "     pm list packages | grep -i launcher   # 找到旧桌面包名"
echo "     pm uninstall <旧桌面包名>             # 然后重启，开机直进点歌台"
echo
echo "完成。重启后应直接进入 KTV 点歌台；在线点歌需要盒子联网。"
echo "曲库目录：/sdcard/ktv/  或  U 盘里的 ktv 目录 / U 盘根目录。"
