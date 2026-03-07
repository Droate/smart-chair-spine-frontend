// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.spineassistant"  // 根据你的实际 package 名
    compileSdk = 34  // 必须加这一行！（新语法是 compileSdk，不是 compileSdkVersion）

    defaultConfig {
        applicationId = "com.example.spineassistant"  // 你的APP包名
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // 其他原有配置保持不变
    }

    // ... 其余 buildTypes、compileOptions 等保持原有
}