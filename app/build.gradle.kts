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
val versionMaterial = "1.11.0"
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

android {
    namespace = "com.errorsiayusulif.zakocountdown"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.errorsiayusulif.zakocountdown"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.8.10-Final"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")

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