# Raspberry Pi 4B 单格六点程序

当前入口为 Braille.py；引脚见 config.py 和 [PCB 说明](../hardware/pcb/README.md)。
支持英文 a–z 与空白，单格逐字显示；不支持中文、数字或缩写盲文。

在 Raspberry Pi OS 上：
```bash
cd raspberry-pi
bash setup_remote.sh --with-bluetooth
python3 Braille.py --simulate --text "hello world"
python3 test_hardware.py
# 断电接线检查完成后再进行短脉冲输出测试
python3 test_hardware.py --outputs
bash start.sh --bluetooth --text hello
```

默认读取 text.txt，Voice 朗读当前字母，Next/Previous 切换字符。完整蓝牙操作见 [BLUETOOTH_SETUP.md](BLUETOOTH_SETUP.md)，实机验收见 [BLUETOOTH_ACCEPTANCE.md](BLUETOOTH_ACCEPTANCE.md)。
依赖由安装脚本通过系统包安装；requirements.txt 的 pyserial 仅用于旧独立 RFCOMM 脚本。不要与主程序同时运行 bluetooth-RPi-wifi.py，也不要同时运行多个 GPIO 程序。

无硬件验证：
```bash
python3 -m unittest discover -s tests -v
python3 Braille.py --simulate --text "hello world"
```

Firebase 为可选功能，本包清空了原项目地址。需要时设置 BRAILLIO_FIREBASE_URL、BRAILLIO_BOARD_ID 后使用 --firebase；路径为 /Boards/{board_id}/words。无此参数时可使用本地文本和蓝牙。
默认通电上限 90 秒、冷却 2 秒；结束实验须切断线圈电源。测试通过不代表真实驱动、温升、音箱或蓝牙链路已验收。
