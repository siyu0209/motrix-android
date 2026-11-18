// 根构建脚本：只声明插件版本，具体在各模块应用
plugins {
    // AGP 8.6.1 支持 compileSdk 35（本机已装 android-35 + build-tools 35.0.0）
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
