// file: ZakoCountdown/build.gradle.kts (项目根目录)

plugins {
    id("com.android.application") version "8.1.4" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.7.7" apply false
    // --- 【最终修复】在这里添加KSP插件声明 ---
    id("com.google.devtools.ksp") version "1.9.0-1.0.13" apply false
}

// subprojects { ... } 部分是用于解决依赖冲突的，我们暂时不需要它
// 如果它存在，请先删除或注释掉，以保持配置最简化
/*
subprojects {
    configurations.all {
        resolutionStrategy {
            eachDependency {
                if (requested.group == "androidx.appcompat") {
                    useVersion("1.6.1")
                }
            }
        }
    }
}
*/