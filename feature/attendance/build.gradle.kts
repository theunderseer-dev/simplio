plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.datetime)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}