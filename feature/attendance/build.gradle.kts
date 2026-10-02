plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain {
            dependencies {
                api(libs.androidx.room.runtime)

                implementation(libs.kotlinx.datetime)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
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
