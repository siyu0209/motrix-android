plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.motrix.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.motrix.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "2.4.3-android.1"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("int", "DEFAULT_SERVER_PORT", "16800")
    }

    signingConfigs {
        create("release") {
            storeFile = file("motrix-release.jks")
            storePassword = "Motrix@2026"
            keyAlias = "motrix"
            keyPassword = "Motrix@2026"
        }
    }

    buildTypes {
        release {
            // 不做代码混淆/资源压缩：避免反射/RPC 通道被裁而引发运行期问题；显著性来源是「已签名 + 非 debuggable」
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // 内嵌本地 server：静态托管 renderer-web + 未来 REST/WS 兼容桥
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
}
