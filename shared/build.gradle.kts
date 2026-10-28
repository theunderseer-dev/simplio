plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.animation)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(libs.compose.ui.test)
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

ktlint {
    filter {
        exclude { element ->
            element.file.path.contains("${File.separator}build${File.separator}")
        }
    }

    additionalEditorconfig.set(
        mapOf(
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
        ),
    )
}

compose.resources {
    packageOfResClass = "com.theunderseer.simplio.shared.resources"
}
