【B860AV 机顶盒 → 家庭影院 KTV 点歌台】安装文件说明
====================================================

本目录文件：
  ktv.apk            点歌台 App（装到盒子上，作为开机桌面）
  一键安装.sh        在盒子上以 root 运行，自动装 App + 部署在线后端 + 配自启
  ktvnet.py          在线点歌后端（网易云；纯 Python 标准库，监听 127.0.0.1:8089）
  ktvnet-watch.sh    在线后端守护（端口探测保活，开机拉起）
  README-安装文件.txt 本文件

前置：盒子【已 root】、能开【ADB】或拿到 root shell（本项目实机 = B860AV1.1-T2 / Android 4.4.2 / armv7）。

快速安装（盒子 root shell 里）：
  1) 把整个目录拷到盒子，如 /sdcard/ktv-install/
  2) sh /sdcard/ktv-install/一键安装.sh
  3) 卸载旧桌面（Android 4.4 没有 pm set-home-activity）：
       pm list packages | grep -i launcher
       pm uninstall <旧桌面包名>
  4) 重启 → 开机直接进 KTV 点歌台

在线点歌需要盒子联网（后端走 127.0.0.1:8089，App 用本地代理避开 Android 4.4 直连网易云的 TLS 问题）。

曲库：/sdcard/ktv/，或 U 盘里任意位置（会自动识别可移动盘；插拔自动重扫）。
命名：编号-歌名-歌手.mp4 ；伴奏/原唱加 -伴奏 / -原唱 ；同名 .lrc 自动歌词。
U 盘里的电影/视频也会出现在「本地曲库」里，点开全屏播放。

手机点歌：首页右下角二维码 → 手机浏览器打开 → 本地/在线 点歌（手机需与盒子同一 WiFi）。

详见「安装教程（其它格式）」里的 安装教程.md / 常见问题.md。
