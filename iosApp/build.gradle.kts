import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

kotlin {
    val simulator = iosSimulatorArm64()
    listOf(iosArm64(), simulator).forEach { target ->
        target.binaries.framework {
            baseName = "SimplioKit"
            isStatic = true
        }
    }

    // Give the simulator test executable its own Keychain identity.
    simulator.testRuns.configureEach {
        val entitlements = layout.projectDirectory.file("src/iosTest/KeychainTest.entitlements")
        executionSource.binary.linkerOpts("-sectcreate", "__TEXT", "__entitlements", entitlements.asFile.absolutePath)
        executionSource.binary.linkTaskProvider.configure { inputs.file(entitlements) }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":feature:attendance"))
            implementation(project(":shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        iosTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    // Keychain services are available in a booted simulator.
    standalone.set(false)
}
