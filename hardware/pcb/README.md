# 当前 PCB 与接线

原样保留用户提供的 Altium PCB（PcbDoc）、原理图（SchDoc）、原理图 PDF 和 BOM（xlsx）。没有对电路做改版，也未补造 Gerber、DRC 报告或制造工程文件。

当前软件以 raspberry-pi/config.py 为准：J1–J6 对应点 1–6，BCM 引脚依次为 23、24、10、9、25、8，Pi 物理脚依次为 16、18、19、21、22、24。
触读正面点位为左列 1/2/3、右列 4/5/6。装配时应实测每个插座对应的机械点位。

六键：Next BCM26/Pin37；Previous BCM13/Pin33；Voice BCM16/Pin36；Random BCM6/Pin31；Refresh BCM5/Pin29；Change BCM12/Pin32。联网灯 BCM4/Pin7。

接线图见 [raspberry-pi-wiring.png](raspberry-pi-wiring.png)，器件位置见 [component-guide.png](component-guide.png)。这些图为已有项目说明图，不能替代通电前的极性及连通性核对。

已有接线方案：Pi USB-C 使用稳压 5V/3A；Pi Pin2 接 PCB J8 Pin2（VIN），Pi Pin6 接 J8 Pin1（GND）；PCB J8 经板载电路得到 3.3V。独立稳压 7.4V/3A 的线圈电源正极经约 2A 保险丝接 J7 Pin2，负极接 J7 Pin1。两路电源在 PCB 共地；P1 Pin20 接 Pi Pin20。P1 Pin1/2/4/17 留空。
J7 网络名虽然标注 12V，本项目线圈按额定 7.4V 使用。J1–J6 线圈接 Pin1（开关端）与 Pin2（正电源）。实际接线前对照原理图并用万用表核对地脚、极性和板上 3.3V。

旧面包板方案使用不同 GPIO，未纳入本包。请勿将旧面包板接线说明混用于此 PCB。
线圈不能直接接 GPIO。软件通电上限 90 秒、冷却 2 秒；该参数不是线圈热额定值，软件异常不能替代硬件断电保护。实机接线、温升及蓝牙仍需在目标装置验收。
