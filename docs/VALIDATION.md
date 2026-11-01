# 打包验证（2026-11-02）

- Raspberry Pi：17 项 unittest 全部通过；hello world 无硬件模拟运行成功。
- Android：在整理后的源码目录执行 assembleDebug 和 testDebugUnitTest，BUILD SUCCESSFUL；5 项普通单元测试通过。未运行 Android 仪器测试或 nativeUiTests 扩展套件。
- Android 构建在未提供私人 google-services.json、数据库地址为空的状态下完成，使用本机缓存依赖、JDK 17、Gradle 9.4.1 和 Android SDK 34；未验证首次联网下载依赖。
- PCB、原理图、原理图 PDF 与原始文件逐字节一致；外盒全部 STL/STEP 与最终 v3 原文件逐字节一致。
- 文本源码扫描未发现原 Firebase 项目标识、Google API Key 或原电脑绝对路径；有效 google-services.json 和 local.properties 未进入源码包。
- 本次未进行实物打印、PCB DRC/制造验证、线圈温升测试或手机到实板蓝牙验收。外盒 validation.json 是原生成记录。

Android 单元测试套件：
- thesonid.com.brailler.ConstellateRulesTest: 4 tests, 0 failures, 0 errors
- thesonid.com.brailler.ExampleUnitTest: 1 tests, 0 failures, 0 errors
