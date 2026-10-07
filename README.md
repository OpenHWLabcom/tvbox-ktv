# B860AV 机顶盒 → 家庭影院 KTV 点歌台

把吃灰的机顶盒变成一台**家庭 KTV 点歌台**：**开机直进点歌大屏**，**本地曲库 + 在线点歌**，**遥控器 / 鼠标 / 手机扫码**都能点歌。

> OpenHW Lab · 让闲置硬件重获新生 — https://www.openhwlab.com

---

## 功能

- **开机直进点歌台**（App 为唯一桌面 / HOME）。
- **本地曲库**：扫 `/sdcard/ktv/` 与 **U 盘**里的歌/视频（读系统挂载表动态识别可移动盘；插拔自动重扫）。
  - 命名 `编号-歌名-歌手.mp4`；`-伴奏`/`-原唱` 同编号合成一首可切换；同名 `.lrc` 自动当歌词。
  - **U 盘里的电影/视频**也在本地库里，点开全屏播放。
- **在线点歌**（网易云）：搜歌/歌手、**热歌榜**、**戏曲**（各剧种：京剧/黄梅戏/豫剧/越剧/评剧/昆曲/川剧/粤剧…）、**儿歌**；在线播放 + 滚动歌词。
- **播放页**：居中大字歌词 + **卡拉OK 进度染色**；音频播放时是**动态粒子画面（不黑屏）**；**可拖动进度条**；屏幕音量条 + 大号数字。
- **遥控器/键盘/鼠标**：四行导航（顶栏 / 快捷按钮 / 曲库 / 已点）；鼠标点击点歌；遥控媒体键；音量键。
- **手机扫码点歌**：首页右下角二维码，手机浏览器打开即点歌（本地曲库 + 在线点歌 + 正在播放/已点）。内置 HTTP 服务 `:8081`。
- **彩色动态星空桌面**（非黑）。

## English (short)

Turn a spare TV box into a **home karaoke (KTV) song-request machine**. Boots straight into the karaoke UI. **Local library** (internal storage / USB drives, karaoke videos, audio + `.lrc` lyrics) **+ online search** (NetEase Cloud Music: search / hot chart / Chinese opera / kids songs). Karaoke lyrics with **per-line progress coloring**, **particle animation** instead of a black screen, **draggable progress bar**, on-screen volume. Control via **remote / keyboard / mouse**, or **scan the QR code on the home screen** to control from a phone (built-in HTTP server on `:8081`).

---

## 盒子要求

- 安卓机顶盒（本项目实机 = **B860AV1.1-T2**，ZTE ZX296716 / Android 4.4.2 / armv7），**已 root**，能开 **ADB** 或 root shell。
- **不刷机**；随时可卸载还原。

## 目录

```
install/    安装文件：ktv.apk + ktvnet.py(在线后端) + ktvnet-watch.sh(守护) + 一键安装.sh + README
docs/       安装教程 / 常见问题 / 关键坑与经验  (md · html · txt)
src/        源码：Android 工程(android/) + 在线后端(ktvnet.py) + 构建脚本 + 系统改造手册
```

## 怎么装

见 `docs/安装教程.md`（或 zip 里的「安装教程」）。三步：

1. 装 `ktv.apk` 并**设为唯一桌面**（`pm uninstall` 旧桌面后本 App 即默认桌面；Android 4.4 无 `pm set-home-activity`）；
2. 把 `ktvnet.py` + `ktvnet-watch.sh` 放进盒子的 Linux 子系统（chroot），开机脚本里拉起守护（端口探测保活）；
3. 改开机兜底脚本 `bootfix.sh` 的包名为 `com.openhardwarelab.ktv`，重启即进点歌台。

⚠️ 关键坑：开机 `start-stop-daemon` 拉起时 **PATH 不含 `/usr/bin`** → 脚本里一律用**绝对路径 `/usr/bin/python3`**（否则 `nohup: can't execute 'python3'` 静默失败）。

## 在线后端

`ktvnet.py`（纯标准库）监听 `127.0.0.1:8089`：

| 接口 | 说明 |
|---|---|
| `GET /search?q=&n=` | 搜歌(带可播性过滤) |
| `GET /hot?n=` | 热歌榜(在线默认内容) |
| `GET /lrc?id=` | 歌词 |
| `GET /play?id=` | 302 到音频直链 |
| `GET /ping` | 健康检查 |

`ktvnet-watch.sh` 每 25s 端口探测保活，开机由盒子开机脚本拉起。

## 手机点歌（App 内置）

App 内置 HTTP 服务 `:8081`：`/`(手机网页) · `/api/local` · `/api/search?q=` · `/api/hot` · `/api/add` · `/api/now`。首页右下角二维码按运行时 IP 自动生成。

## 开源协议

见 `LICENSE`。
