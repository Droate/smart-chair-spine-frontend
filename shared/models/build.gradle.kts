plugins {
    id("com.android.library")          // 👈 这是让 'android {...}' 代码块生效的关键
    id("org.jetbrains.kotlin.android") // 👈 配合 Android Library 使用 Kotlin
}

android {
    // 命名空间必须唯一，并且通常与你的包名一致
    namespace = "com.spineassistant.models"
    compileSdk = 34

    defaultConfig {
        minSdk = 24 // 与您的 app 保持一致或更低
    }

    // 对于纯数据/逻辑模块，关闭不必要的Android功能可以加速编译
    buildFeatures {
        buildConfig = false
        resValues = false
        androidResources = false
    }

    // 确保Kotlin版本与主项目一致
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // Java版本与主项目一致
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // Kotlin 标准库
    implementation(kotlin("stdlib"))

    // JSON 序列化：作为数据模型，这个模块只需要注解
    // api() 会将这个依赖传递给 app 模块，app 无需重复添加
    api("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}
