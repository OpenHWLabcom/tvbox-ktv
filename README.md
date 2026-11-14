# KTV 点歌台 · 标准版 v2.0（OpenHW Lab）

把**任意已 root 的安卓机顶盒**（Android 4.4 / armv7 实测最佳）做成**家庭 KTV 点歌台**：
开机直进点歌大屏，本地 + 在线双曲库，遥控器 / 鼠标 / USB 键盘 / **手机扫码点歌** 全部可用。

> 研发：**智能硬件开放实验室** · 官网 https://www.openhwlab.com · 联系 hi@openhwlab.com

## 功能（v2.0 标准版）

- **开机直进**：App 注册 HOME + BOOT_COMPLETED，替换原桌面，开机 5 秒进点歌台
- **本地曲库**：内部存储 / U 盘（`/sdcard/ktv` 或 U 盘任意目录），支持 `编号-歌名-歌手.mp4/.mp3`、`-伴奏`、`-原唱`、`.lrc`
- **在线点歌（多音源）**：网易云 + 酷狗双源，**只用能播的免费曲**（自动探测过滤）；首页合并 4 个榜单 + 酷狗 TOP500 → 一次 70+ 首
- **首屏 77 首、搜索 40 条**，列表只显示歌名 · 歌手（不显示音源）
- **拼音输入**：内置 9.3 万条词库（全拼/首字母），`zjl` → 周杰伦；候选条实时联想，无网也快
- **手机扫码点歌**：电视右下角二维码 → 手机浏览器（用手机自己的中文输入法）
  - 搜索 / 热歌 / 本地曲库
  - **已点队列**：查看、播放某首、删除
  - **切歌**、当前播放实时同步（4 秒轮询）
- **卡拉 OK 歌词**：逐字进度染色、下一句预告
- **不黑屏**：在线音频配粒子动画；进度条可拖
- **音量**：屏幕音量条 / 遥控器音量键（走系统 STREAM_MUSIC）

## 安装

### 方式 A：adb（推荐）

```bash
cd openhwlab-ktv
./install/一键安装.sh                # USB 连接
./install/一键安装.sh 192.168.1.20:5555   # 或走网络 adb
```

脚本会：备份原桌面 → 装 KTV 到 `/system/app/KTV.apk` → 放拼音词库 `/data/local/py.txt` → 放代理 `/data/linux/root/ktvnet.py` → 写开机钩子 → 重启。

### 方式 B：无 adb（串口 / 已 root 的任何手段）

1. 打开 `install/一键安装.sh`，按里面 6 个步骤逐条在盒子上执行
2. 关键路径：
   - APK → `/system/app/KTV.apk`（`chmod 644`、owner root）
   - 词库 → `/data/local/py.txt`
   - 代理 → `/data/linux/root/ktvnet.py`
   - 钩子 → 追加到 `/system/etc/init.bigfish.sh`（见 `install/hook-install-recovery.sh`）

### 在线点歌依赖（可选）

在线功能需要一个 **armhf Linux chroot**（Ubuntu 20.04 base + `python3`）在 `/data/linux`：
`ktvnet.py` 在里面跑，监听 `127.0.0.1:8089`，App 通过它搜歌/取歌词/取直链。
没装 chroot 则只能用**本地曲库**（U 盘歌曲），其余界面照常。

## 编译

```bash
# 需要 jdk8 + aapt + android-4.4.2/android.jar + d8
cd src && ./build.sh        # 产物 ktv.apk
```

## 已知坑（都踩过）

1. **运营商 ROM 禁 `pm install`**（`INSTALL_FAILED_NOT_SUPPORT`）→ 只能 root 下直接替换 `/system/app/*.apk`
2. **替换 APK 后必须重启**：立刻 `am start` 会因 dalvik 缓存没建好而 `ClassNotFoundException`（表现：界面卡住、8081 打不开）
3. **Android 4.4 的 OpenSSL 握手不了现代 HTTPS** → 播放地址必须用 **http**，并由 App 自己跟随 302（别交给 MediaPlayer）
4. **酷我 `anti.s` 只返回 11 秒试听**（固定 181521 字节）→ 不能用于播放；**咪咕**公开接口已失效
5. **二维码别画太小**：< 100px 时手机扫出来是残缺 URL，建议 ≥ 200dp 并在下面印地址文字
6. 手机与盒子必须**同一局域网**（路由器开了 AP 隔离就会打不开）

## 版本

- **v2.0（2026-11-14）标准版**：多音源、拼音输入、手机已点/切歌、首屏 77 首、二维码修正
- v1.0：单音源（网易云）、手机仅能点歌

## 许可

MIT。仅供个人/家庭学习使用；在线音源版权归各平台所有，请勿商用。
