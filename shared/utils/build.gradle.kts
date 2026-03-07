plugins {
    id("com.android.library")          // 👈 核心修改：从 kotlin("jvm") 改成这个
    id("org.jetbrains.kotlin.android") // 👈 核心修改：添加这个
}

android {
    namespace = "com.spineassistant.utils" // 👈 必须提供一个唯一的命名空间
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    // 关闭不需要的Android功能
    buildFeatures {
        buildConfig = false
        resValues = false
        androidResources = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    // 👇 依赖 models 模块，现在它们都是 Android 库，可以互相依赖了
    implementation(project(":shared:models"))

    // 👇 保留您原有的 Jackson 依赖
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    testImplementation("junit:junit:4.13.2")
}
