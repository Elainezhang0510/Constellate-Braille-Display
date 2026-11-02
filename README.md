# Constellate

Android companion app + Raspberry Pi 4B single-cell, six-dot refreshable Braille display.

## 目录

- `android/`：完整 Android Gradle 源码、资源、测试、离线英文语音模型及依赖许可。
- `raspberry-pi/`：当前硬件控制程序、安装启动脚本、测试和 Arduino 编码参考。
- `hardware/pcb/`：用户提供的 PCB、原理图源文件、PDF、BOM 与当前接线图。
- `hardware/enclosure/v3/`：最终版 STL、STEP、参数化源文件、预览、试配件及使用说明。
- `MANIFEST-SHA256.txt`：文件校验值。

## Android 构建

使用 JDK 17、Android SDK 34；项目使用 AGP 9.2.1、Gradle 9.4.1。Android Studio 打开 android/，首次构建需要联网获取依赖。Gradle 下载地址已从原电脑路径改为通用地址，未携带本机 local.properties。

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Linux/macOS 可用 `bash gradlew :app:assembleDebug :app:testDebugUnitTest`。
APK 输出为 `android/app/build/outputs/apk/debug/app-debug.apk`。这是调试构建；本包没有发布签名密钥。

**Firebase：**原 google-services.json 和数据库地址未进入源码包。将你自己的 Firebase Android 配置保存到 android/app/google-services.json（应用包名 thesonid.com.brailler），并把 android/app/src/main/res/values/strings.xml 的 firebase_database_url 设为自己的 Realtime Database URL。example 文件仅展示结构，不能作为有效配置。未配置时可编译；依赖账号登录、云词库的功能需要配置后才能使用。详情见 docs/CONFIGURATION.md。

## 硬件与外盒

先读 [当前 PCB 接线说明](hardware/pcb/README.md)，再使用 [树莓派程序说明](raspberry-pi/README.md)。当前 GPIO 与旧面包板方案不同，因此没有混入旧方案文档。
外盒打印与尺寸见 [v3 使用说明](hardware/enclosure/v3/使用说明.md)；直接打印 base.stl、lid_print.stl，先用 fit_coupon.stl 验证公差。assembly_with_reference.step 里的执行器仅供位置检查。
