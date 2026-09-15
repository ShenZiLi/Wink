plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.wink.eye"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wink.eye"
        minSdk = 34
        targetSdk = 36
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "wink-keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "winkpass"
            keyAlias = System.getenv("KEY_ALIAS") ?: "wink"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "winkpass"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    // CameraX 1.4.0：要求 compileSdk ≥ 35，本项目 36，满足
    val cameraX = "1.4.0"

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // 提供 collectAsStateWithLifecycle 与 LocalLifecycleOwner（2.8+ 起从 compose.ui 迁到这里）
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Haze 1.6.10：Compose 背景模糊（backdrop blur），液态玻璃的基础层。
    // 说明：Haze 2 的 haze-glass 模块（折射玻璃）要求 Kotlin 2.4 + AGP 9.1 + compileSdk 37，
    // 本项目的构建链暂时不满足，故选用稳定版 1.x；折射/高光由自绘玻璃层补齐。
    implementation("dev.chrisbanes.haze:haze:1.6.10")

    // 二维码 Tab：CameraX 取景 + ML Kit 识别 + ZXing 生成
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    // bundled 版：识别模型打包进 APK，运行时不需要 Google Play 服务在线下载
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
