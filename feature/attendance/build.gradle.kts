plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
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

ktlint {
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}
