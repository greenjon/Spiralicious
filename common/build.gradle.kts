plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    id("com.android.library")
    alias(libs.plugins.jetbrains.compose)
    // alias(libs.plugins.kotlin.compose) // Removed - handled by JetBrains Compose plugin
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
        val androidMain by getting {
            dependencies {
                // Android-specific dependencies
                implementation(libs.androidx.core.ktx)
                implementation(libs.room.runtime)
                implementation(libs.room.ktx)
                implementation(libs.androidx.lifecycle.runtime.ktx)
            }
        }
        val desktopMain by getting {
            dependencies {
                // Desktop-specific dependencies
            }
        }
    }
}

android {
    namespace = "llm.slop.spirals.common"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
