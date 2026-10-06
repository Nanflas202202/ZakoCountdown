// file: app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("androidx.navigation.safeargs.kotlin")
}

// --- 核心优化：定义版本号变量，作为单一数据源 ---
val versionCoreKtx = "1.12.0"
val versionAppCompat = "1.6.1"
val versionMaterial = "1.13.0"
val versionConstraint = "2.1.4"
val versionJunit = "4.13.2"
val versionExtJunit = "1.1.5"
val versionEspresso = "3.5.1"
val versionLifecycle = "2.7.0"
val versionFragment = "1.6.2"
val versionRoom = "2.6.1"
val versionCoroutines = "1.7.3"
val versionNav = "2.7.7"
val versionPreference = "1.2.1"
val versionWork = "2.9.0"
val versionCoil = "2.6.0"
val versionGson = "2.10.1"

// ============================================================
// 升级代号 (UpgradeCode)
// ------------------------------------------------------------
// 与 Android 的 versionCode 完全独立：
//   * versionCode   → 给系统安装器看的，每次上架必须递增
//   * upgradeCode   → 给 OTA 引擎看的，用于判断「服务端这一版是不是比本机新」
//
// 好处：versionCode 有时会因为渠道包/回滚/重新上传而重排，
// 而 upgradeCode 只由我们自己维护，永远是单调递增的整型，
// 且**只出现在开发者选项里**，不会暴露在关于页、设置页或备份包中。
//
// 维护约定：每次准备发布时手工改成一个更大的值。
// 当前采用「构建日期」式的取值（YYYYMMDD），比纯序号更容易回溯：
//   0.9.1 → 20261025   ← 上一版
//   0.9.2 → 20261026   ← 当前
// 只要保证严格单调递增即可；换成 902001 这样的序号式也没问题。
// ============================================================
val upgradeCode = 20261026
val upgradeCodeLabel = "0.9.2-debug"

android {
    namespace = "com.errorsiayusulif.zakocountdown"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.errorsiayusulif.zakocountdown"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.9.2-debug 2026国庆特别版"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")

        // --- 升级代号：OTA 引擎用它比较版本，与 versionCode 解耦 ---
        buildConfigField("int", "UPGRADE_CODE", "$upgradeCode")
        buildConfigField("String", "UPGRADE_CODE_LABEL", "\"$upgradeCodeLabel\"")
        // 构建指纹：同一次构建固定，用于在开发者选项里核对「手上这个包是哪一次编译的」。
        // 这里算一个短哈希即可 —— 只是给人看的核对码，不需要密码学强度。
        val buildIdSeed = "$upgradeCode|$upgradeCodeLabel|${System.currentTimeMillis()}"
        val buildId = String.format("%06X", (buildIdSeed.hashCode() and 0xFFFFFF))
        buildConfigField("String", "BUILD_ID", "\"$buildId\"")

        // --- 核心优化：将变量注入给 BuildConfig 供 UI 读取 ---
        buildConfigField("String", "LIB_CORE_KTX", "\"$versionCoreKtx\"")
        buildConfigField("String", "LIB_APPCOMPAT", "\"$versionAppCompat\"")
        buildConfigField("String", "LIB_MATERIAL", "\"$versionMaterial\"")
        buildConfigField("String", "LIB_CONSTRAINT", "\"$versionConstraint\"")
        buildConfigField("String", "LIB_LIFECYCLE", "\"$versionLifecycle\"")
        buildConfigField("String", "LIB_FRAGMENT", "\"$versionFragment\"")
        buildConfigField("String", "LIB_ROOM", "\"$versionRoom\"")
        buildConfigField("String", "LIB_COROUTINES", "\"$versionCoroutines\"")
        buildConfigField("String", "LIB_NAV", "\"$versionNav\"")
        buildConfigField("String", "LIB_PREFERENCE", "\"$versionPreference\"")
        buildConfigField("String", "LIB_WORK", "\"$versionWork\"")
        buildConfigField("String", "LIB_COIL", "\"$versionCoil\"")
        buildConfigField("String", "LIB_GSON", "\"$versionGson\"")
    }

    // 本地构建使用工程内的调试签名文件，避免依赖用户目录下的 ~/.android/debug.keystore
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // --- 核心优化：引用顶部的变量 ---
    implementation("androidx.core:core-ktx:$versionCoreKtx")
    implementation("androidx.appcompat:appcompat:$versionAppCompat")
    implementation("com.google.android.material:material:$versionMaterial")
    implementation("androidx.constraintlayout:constraintlayout:$versionConstraint")

    testImplementation("junit:junit:$versionJunit")
    androidTestImplementation("androidx.test.ext:junit:$versionExtJunit")
    androidTestImplementation("androidx.test.espresso:espresso-core:$versionEspresso")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$versionLifecycle")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$versionLifecycle")
    implementation("androidx.fragment:fragment-ktx:$versionFragment")

    implementation("androidx.room:room-runtime:$versionRoom")
    ksp("androidx.room:room-compiler:$versionRoom")
    implementation("androidx.room:room-ktx:$versionRoom")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$versionCoroutines")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$versionCoroutines")

    implementation("androidx.navigation:navigation-fragment-ktx:$versionNav")
    implementation("androidx.navigation:navigation-ui-ktx:$versionNav")

    implementation("androidx.preference:preference-ktx:$versionPreference")

    implementation("androidx.work:work-runtime-ktx:$versionWork")

    implementation("io.coil-kt:coil:$versionCoil")
    implementation("com.google.code.gson:gson:$versionGson")
}