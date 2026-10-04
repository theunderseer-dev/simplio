plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }

        commonMain {
            dependencies {
                api(libs.androidx.room.runtime)
                api(libs.androidx.lifecycle.viewmodel)

                implementation(libs.kotlinx.datetime)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.compose.ui.test)
            }
        }
    }
}

dependencies {
    add("kspDesktop", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

ktlint {
    val separator = File.separator

    filter {
        exclude { element ->
            element.file.path.contains("${separator}build${separator}generated$separator")
        }
    }
}
