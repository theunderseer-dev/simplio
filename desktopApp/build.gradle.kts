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
                implementation(libs.compose.components.resources)
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

val appName = "Simplio"
val appPackageName = "com.theunderseer.simplio"
val macNotificationResources = layout.buildDirectory.dir("mac-notification-resources")
val isMacHost = providers.systemProperty("os.name").map { it.startsWith("Mac") }.get()
if (isMacHost) {
    val nativeSource = layout.projectDirectory.file("src/macos/ClassNotifications.m")
    val keychainSource = layout.projectDirectory.file("src/macos/FirebaseKeychain.m")
    val libraryName = "libSimplioNotifications.dylib"
    val library = macNotificationResources.map { it.file(libraryName) }
    val jdkHeaders = File(System.getProperty("java.home"), "include")
    val architecture = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x86_64"

    val compileNotifications = tasks.register<Exec>("compileMacAttendanceNotifications") {
        inputs.files(nativeSource, keychainSource)
        inputs.property("architecture", architecture)
        outputs.file(library)
        val output = library.get().asFile
        doFirst { check(output.parentFile.mkdirs() || output.parentFile.isDirectory) }
        commandLine(
            "xcrun", "--sdk", "macosx", "clang",
            "-arch", architecture, "-mmacosx-version-min=13.0", "-dynamiclib", "-fobjc-arc", "-fblocks",
            "-I", jdkHeaders.absolutePath, "-I", jdkHeaders.resolve("darwin").absolutePath,
            "-framework", "AppKit", "-framework", "UserNotifications", "-framework", "Security",
            nativeSource.asFile.absolutePath, keychainSource.asFile.absolutePath, "-o", output.absolutePath,
        )
    }

    val packagedLibrary = layout.buildDirectory.file(
        "compose/binaries/main/app/$appName.app/Contents/app/resources/$libraryName",
    )
    tasks.configureEach {
        when (name) {
            "prepareAppResources" -> {
                dependsOn(compileNotifications)
                // Feed the generated file directly; the platform resource folders may not exist yet.
                val copy = this as? AbstractCopyTask ?: error("Compose prepareAppResources must be a copy task")
                copy.from(library)
            }

            "createDistributable" -> {
                dependsOn(compileNotifications)
                inputs.file(library)
                doLast {
                    val bundled = packagedLibrary.get().asFile
                    check(bundled.isFile && bundled.length() > 0) { "Missing notification library in app: $bundled" }
                    logger.lifecycle("Verified notification library in $bundled")
                }
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.theunderseer.simplio.desktop.resources"
}

compose.desktop {
    application {
        mainClass = "com.theunderseer.simplio.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = appName
            packageVersion = "1.0.0"
            modules("java.prefs")
            if (isMacHost) appResourcesRootDir.set(macNotificationResources)
            macOS {
                bundleID = appPackageName
                val icon = project.file("icons/simplio.icns")
                if (icon.isFile) iconFile.set(icon)
            }
        }
    }
}

ktlint {
    val generatedDirectory = layout.buildDirectory.dir("generated").get().asFile

    filter {
        exclude { element -> element.file.toPath().startsWith(generatedDirectory.toPath()) }
    }
}
