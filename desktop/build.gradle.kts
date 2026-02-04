import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    alias(libs.plugins.kotlin.compose)
}

group = "llm.slop.spirals"
version = "1.0"

kotlin {
    jvm {
        withJava()
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":common")) // Assuming your logic is here

                // --- VITAL FOR OPENGL PORTING ---
                // You need these to replace 'android.opengl.*'
                val lwjglVersion = "3.3.3"
                implementation("org.lwjgl:lwjgl:$lwjglVersion")
                implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion")
                implementation("org.lwjgl:lwjgl-glfw:${lwjglVersion}")

                // Since you are on Linux, you need the natives
                runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:natives-linux")
                runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:natives-linux")
                runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:natives-linux")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "llm.slop.spirals.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Spirals"
            packageVersion = "1.0.0"
        }
    }
}
