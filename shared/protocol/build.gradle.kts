plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    // 命名空间必须唯一，这是 protocol 模块，所以用 protocol
    namespace = "com.spineassistant.protocol"  // 👈【修改点 1】
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    // 关闭不必要的Android功能
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

    // --- 🔹【修改点 2】🔹 ---
    // 声明 protocol 模块依赖于 models 模块，这样它才能找到 ChairState
    implementation(project(":shared:models"))

    // JSON 序列化：因为 WebSocketMessage 也要被序列化，所以保留这些
    api("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}
