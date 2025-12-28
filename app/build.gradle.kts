// file: app/build.gradle.kts (Module :app)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") // 应用 KSP 插件
    id("androidx.navigation.safeargs.kotlin")
    // 不要再使用 kotlin-kapt
}

android {
    namespace = "com.errorsiayusulif.zakocountdown"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.errorsiayusulif.zakocountdown"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.7.8-Prestable 2026元旦特别版"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
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
    // 如果这里有 sourceSets 代码块，请先删除它，我们暂时不需要
}

dependencies {
    // 核心AndroidX库
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 测试库
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // ViewModel 和 LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Room
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    ksp("androidx.room:room-compiler:$room_version") // 使用 ksp
    implementation("androidx.room:room-ktx:$room_version")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Preferences
    implementation("androidx.preference:preference-ktx:1.2.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // --- ↓↓↓ 在这里添加Coil依赖 ↓↓↓ ---
    implementation("io.coil-kt:coil:2.6.0")
}