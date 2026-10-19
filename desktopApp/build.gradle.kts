import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(project(":feature:attendance"))

                implementation(compose.desktop.currentOs)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.compose.uiToolingPreview)
                implementation(libs.kotlinx.datetime)
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.theunderseer.simplio.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "com.theunderseer.simplio"
            packageVersion = "1.0.0"
        }
    }
}

ktlint {
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}
