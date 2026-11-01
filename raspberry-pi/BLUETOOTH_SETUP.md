# 手机蓝牙控制盲文板：配置与使用

本功能适用于现有 Android App、Raspberry Pi 4B 内置蓝牙，以及已经按 `../hardware/pcb/README.md` 接好的单格六点电磁推进器。无需另购蓝牙模块。Pi 的线圈驱动、稳压 7.4V 电源、续流二极管和公共地必须已经具备；手机或 GPIO 不能直接给线圈供电。

## 1. 安装与复制

把本目录完整复制到 Pi，保留 `Braille.py`、`bluetooth_profile.py`、`board_protocol.py`、`wifi_config.py` 等文件。在 Pi 的终端执行：

```bash
cd "/实际路径/Constellate-Braille-Display/raspberry-pi"
bash setup_remote.sh --with-bluetooth
python3 -c 'import dbus; from gi.repository import GLib; import RPi.GPIO; print("依赖就绪")'
python3 Braille.py --simulate --text hello
```

安装只添加系统软件，不会改变 GPIO 接线。安卓端必须使用本次源码重新构建的 App；发布目录内另附当前已有的调试 APK；源码包不包含旧 APK。在具备 JDK 17 与 Android SDK 的电脑进入 `android` 目录，执行 `gradlew.bat :app:assembleDebug`，新 APK 位于 `app/build/outputs/apk/debug/`。

## 2. 打开 Pi 蓝牙并配对

先保持 **7.4V 线圈电源关闭**。在 Pi 上执行：

```bash
sudo systemctl enable --now bluetooth
bluetoothctl
```

在 `bluetoothctl` 交互提示符中依次输入：

```text
power on
agent on
default-agent
pairable on
discoverable on
show
```

保持此终端打开，在安卓手机的系统蓝牙设置里找到 Pi 并配对。两边如显示配对码，核对一致后确认。在 `bluetoothctl` 输入 `paired-devices` 检查。首次配对完成后可输入 `discoverable off`，再输入 `quit`。设备已配对后，无需每次打开可发现模式。

## 3. 启动唯一的 GPIO 主程序

不要同时运行 `test_hardware.py` 或另一个 `Braille.py` 实例。配网也由主程序接收，不要同时启动旧的 `bluetooth-RPi-wifi.py`。

```bash
cd "/实际路径/Constellate-Braille-Display/raspberry-pi"
python3 Braille.py --bluetooth --mute --text hello
```

看到 `Bluetooth SPP profile ready` 表示 BlueZ 已注册串口服务；这**不等于手机已连接**。如果报 D-Bus、BlueZ 或 Profile 错误，先检查 `systemctl status bluetooth` 和 `python3 -c 'import dbus; from gi.repository import GLib'`。若已有别的 SPP 程序占用服务，先停止它。此功能使用 BlueZ 的 SPP 服务，不创建 `/dev/rfcomm0`。

实际装置如要语音，可去掉 `--mute`。完成低压验证后，按硬件验收文档逐路检查，最后才打开线圈电源。

## 4. 手机操作

1. 在 App 登录并打开 **Word library**，选择一个英文字母组成的单词。
2. 在单词预览中点 **Send to Braille board**。
3. 点 **Step 1 / Bluetooth connect**，选已配对的 Pi。状态变成 `Connected to ...` 后，点 **Send word to board**。
4. Pi 的单格先请求显示第一个字母。用盲文板上的实体 **Next** 和 **Previous** 按键逐字循环；手机端不提供重复的翻页按键。
5. App 的回执显示首字母、位置和六位点阵。`Queued` 表示仍处于线圈冷却期，尚未实际抬起；`Requested` 表示主程序已请求显示，仍需实机观察确认。实体按键的后续变化以 Pi 终端和实物为准。

空格为六点全零。当前只支持英文字母 `a–z` 和空白；App 词库的单词最多八个字母。单格无法同时显示整词。主程序的线圈默认连续通电最多 90 秒，随后至少断电 2 秒；这是软件上限，不是已验证的热额定值。命令中的 `Braille.MAX_ON_SECONDS=0.2` 会覆盖默认值，需移除或改成 `90`。

Android 返回词库或上一页时保持当前蓝牙连接，重新进入发送页可继续发送；点 **Disconnect board** 或退出账号时断开连接。App 进程结束或系统回收后需重新连接。

配网入口仍位于 **Pair your board → Bluetooth · set up Wi-Fi**。先用 Step 1 连接，再用 Step 2 输入 Wi-Fi 信息；主程序通过同一 SPP 服务处理配网请求。配网可能切断当前 SSH 连接，进行前须关闭 7.4V 电源。

## 5. 指令格式与排查

SPP 服务 UUID：`00001101-0000-1000-8000-00805F9B34FB`。每条消息是一个 UTF-8 JSON 对象，以换行结束。手机发送：

```text
{"cmd":"load","text":"hello"}
```

Pi 回传一行 JSON，成功时 `ok:true`，拒绝时 `ok:false` 和 `error`。Wi-Fi 配网沿用 `SSID`、`PWD` 与 `result`、`IP` 字段。手机不能发送翻页、GPIO 编号、原始点阵或通电时长指令。

连接失败时，先看 Pi 是否打印 `Bluetooth SPP profile ready`、手机系统是否已配对、App 是否获准“附近设备”权限，并重启一次主程序后重连。状态已连接但没有回执时，确认 Pi 端运行的是本次代码且没有同时运行旧配网脚本。**结束实验先关 7.4V，再按 Ctrl+C 停主程序。**

BlueZ Profile API 与 Android RFCOMM 参考：
[BlueZ ProfileManager](https://github.com/bluez/bluez/blob/master/doc/org.bluez.ProfileManager.rst)、[Android BluetoothDevice](https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createRfcommSocketToServiceRecord(java.util.UUID))。
