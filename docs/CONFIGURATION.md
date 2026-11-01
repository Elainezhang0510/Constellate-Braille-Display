# 配置说明

本包不包含私人 google-services.json、签名密钥、Wi-Fi 凭据、SSH 配置、设备缓存或 IDE 设置。Pi 的原数据库地址及 Android 的 firebase_database_url 已清空。

Android：从自己的 Firebase 项目下载包名 thesonid.com.brailler 对应的 google-services.json 放入 android/app/；strings.xml 中设置 firebase_database_url。文件存在时才启用 Google Services 插件。没有配置也能构建，但账号及云端功能不能完整使用。

Pi：默认从本地 text.txt 读取，也可通过 --bluetooth 接收单词。需要云词库时设置 BRAILLIO_FIREBASE_URL、BRAILLIO_BOARD_ID，再传入 --firebase。现有 Pi Firebase 读取器没有提供 Firebase 认证令牌，数据库权限需与所用读取方式匹配；本包未修改云端规则。

原 APK 单独保留为已有调试安装包，可能仍指向原项目云配置；它不是从脱敏配置重建的正式签名版本。公开发布自有云端版本时，请使用你自己的 Firebase 配置和签名重新构建。
