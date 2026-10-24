# KTV 点歌台 (com.openhardwarelab.ktv)

把机顶盒改成 **家庭影院 KTV 点歌台**：开机直进点歌界面，遥控器/键盘/鼠标都能操作。
**两种模式**：**本地曲库** + **在线点歌**（联网搜歌，走盒子本机代理）。**开机默认进「在线点歌」**（顶部可切「本地曲库」）。

当前部署机器：**B860AV1.1-T2**（ZTE ZX296716 / Android 4.4.2 / API19 / armeabi-v7a），盒子 IP `192.168.1.161`。

## 界面 / 操作

- **桌面背景 = 彩色动态星空**（彩色渐变星云 + 满天闪动彩星 + 流星），不用黑色；面板半透明以透出星空。

顶部一行两个 tab：**本地曲库 | 在线点歌**（↑ 到顶栏，←→ 选，确定 进入）。

**遥控器/键盘导航（四行区域）**：↑↓ 在 顶栏 / 快捷按钮行 / 曲库列表 / 已点 之间上下移动；←→ 在行内选择（顶栏切模式、快捷按钮行选 热门/戏曲/儿歌/歌手、列表↔已点）；确定=进入/点歌/播放。快捷按钮行**遥控器可达**。

| 键 | 作用 |
|---|---|
| ↑ / ↓ | 上下选择（在曲库顶部再按 ↑ 进顶栏） |
| ← → | 顶栏=切换本地/在线；列表=切「曲库 / 已点」两栏 |
| 确定 / 回车 / A | 点歌（入已点队列）；已点栏=播放该首 |
| 数字/字母 | 输入搜索，自动过滤（在线=联网搜索，有防抖） |
| 删除 | 退格 |
| 返回 | 清空搜索 / 退出播放回点歌台 |
| 菜单 | 本地=重扫曲库；在线=重新搜索 |

播放中：**确定=暂停/继续**，**←=重唱**，**→=切歌**，**↑↓ 或 遥控器音量键=音量**，菜单=原唱/伴奏（在线无此），返回=回点歌台。
- **歌词在屏幕正中放大显示**，并**按演唱进度用颜色逐字擦除同步染色**（卡拉OK效果），下方预告下一句。
- **音频播放（在线歌 / 本地音频）不黑屏**：显示**动态粒子动画**（粒子网 + 光晕球 + 流星）。
- **进度条**：屏幕中下部有一条**可拖动的进度条**（鼠标拖动=跳转）。
- **屏幕可调音量**：底部有 `🔉 ── 音量条 ── 🔊`，调音量时屏幕中间弹大号数字；遥控器 ↑↓ / VOL± 也可。
- 底部还有可点按钮 `⏯暂停 ⏮重唱 ⏭切歌 原/伴`（鼠标可点）。

## 本地曲库

歌曲/视频放 **`/sdcard/ktv/`**，或 **U 盘**里（U 盘根目录、或任意位置，App 会自动认；也支持 `ktv` 子目录）。
**U 盘里的电影/视频也直接在「本地曲库」里**（非 ktv 目录的视频按整条文件名当标题；ktv 目录里的视频按 `编号-歌名-歌手` 解析）。点开即全屏播放。

**U 盘识别**：App 会读 `/proc/mounts` 找出所有**可移动盘挂载点**（vfat/exfat/ntfs/tntfs/udf/… 或 `/dev/block/sd*`；本盒 vold 挂到 `/storage/sda|sdb|sdc`），把它们**整盘递归扫描**；同时扫 `/mnt`、`/storage` 下任何名为 `ktv` 的目录。**插/拔 U 盘会自动重扫**（监听 `MEDIA_MOUNTED/UNMOUNTED/EJECT` 广播），也可按 **菜单键** 手动重扫。

命名约定：

```
编号-歌名-歌手.mp4          # 普通一首（视频 MTV 或 mp3 等音频）
编号-歌名-歌手-伴奏.mp4      # 伴奏音轨
编号-歌名-歌手-原唱.mp4      # 原唱音轨（同编号的 伴奏+原唱 合成一首，播放中可切换）
编号-歌名-歌手.lrc          # 同名歌词，自动逐行滚动
```

## 在线点歌

联网搜索（网易云音乐），选歌即在线播放 + 显示歌词。**纯音频**（`在线` 标记）。
进入在线模式**默认显示「热歌榜」**，顶部一排快捷按钮：**热门 · 戏曲 · 儿歌 · 歌手…**（点「戏曲」展开**各剧种**：京剧/黄梅戏/豫剧/越剧/评剧/昆曲/川剧/粤剧/秦腔/花鼓戏/二人转/河北梆子/吕剧/沪剧/评书/相声…，点剧种出该剧种曲目；‹返回 回上级）。也可打字母直接搜索。

- **后端**：盒子 chroot `/root/ktvnet.py`，监听 `127.0.0.1:8089`；守护 `/root/ktvnet-watch.sh`（端口探测，每 25s 保活）。开机由 `/data/startlinux.sh` 用 `start-stop-daemon --exec /bin/sh -- /root/ktvnet-watch.sh` 拉起。
  - `/hot?n=`(热歌榜, 默认内容) · `/search?q=&n=`(搜索, 带可播性过滤) · `/lrc?id=`(歌词) · `/play?id=`(302 到音频直链) · `/ping`。
  - App 走 `http://127.0.0.1:8089`（避开 Android 4.4 直连网易云的 TLS 问题）。
- ⚠ 在线需要盒子**联网**；搜不到可播结果时 App 会提示。
- ⚠ **坑**：开机由 start-stop-daemon 拉起时 **PATH 不含 /usr/bin** → 脚本里一律用绝对路径 `/usr/bin/python3`（否则 `nohup: can't execute 'python3'`）。

## 构建 / 部署

```bash
cd ~/android-build && ./build-ktv.sh     # 需 jdk8/aapt81/android-4.4.2 工具链
~/ktv/deploy.sh      # 一键 构建+推送+安装+重开
```

源码：`~/ktv/android/ktv/`（包 `com.openhardwarelab.ktv`）。

## 盒子侧改造记录（2026-10-07）

- 本 App 设为 **唯一 HOME**（`pm uninstall com.openhardwarelab.desktop` 旧桌面后即默认）。`/data/bootfix.sh` 兜底包名 desktop→ktv。
- `/data/startlinux.sh`：注释掉其它自启（transfer/boxctl/parcel/parcelguard/nasstart(NAS+aria2+Samba+AdGuard)/rdesk/tunnel）；**保留 sshd / adb(5555) / 挂载**；新增 **ktvnet 守护**自启。
- 删除 chroot 旧程序（AdGuard/aria2/nasd/cam/det/look/monitor/parcel/transfer/boxctl/rdesk/tunnel/yolov8 模型…）。备份 `/sdcard/_ktv_backup/root-programs.tar.gz`；旧桌面 APK `/sdcard/desktop-backup.apk`。
- **回滚**：还原 `/data/startlinux.sh.bak-ktv20261007`、`/data/bootfix.sh.bak-ktv20261007`；`pm install /sdcard/desktop-backup.apk`。

## 待办 / 可加

- 手机扫码点歌（手机浏览器搜歌/点歌）。
- 拼音首字母搜索、分类浏览（按歌手）。
- 在线支持 MV（视频）而不只是音频。
- ⚠ 内部 `/sdcard` 仅 ~580M 空闲 → 本地歌曲建议放 U 盘。

## 开机自启动（已配置）

- 本 App 是**唯一 HOME**（`pm uninstall com.openhardwarelab.desktop` 旧桌面后即默认；Android 4.4 无 `pm set-home-activity`）→ 开机由系统直接拉起。
- `/data/bootfix.sh` 兜底：开机后反复「关系统弹窗 + `am start` 本 App」直到它在最前（对付这 ROM 的开机 bootscreen 卡住）。包名=com.openhardwarelab.ktv。
- 在线后端 `/root/ktvnet.py` 由 `/data/startlinux.sh` 开机拉起（`ktvnet-watch.sh` 守护, 端口探测保活）。
- App 进在线模式时若首拉热歌榜失败（开机瞬间服务未就绪）会**每 3s 重试**（最多 8 次）。
- 实测：`reboot` 后焦点=KTV 点歌台、:8089 在听、热歌榜一次加载成功。
