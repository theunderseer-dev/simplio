package com.theunderseer.simplio.database

import java.nio.file.Files
import java.nio.file.Path

fun simplioDatabasePath(userHome: Path): Path = userHome
    .resolve("Library")
    .resolve("Application Support")
    .resolve("Simplio")
    .resolve("simplio.db")

fun prepareSimplioDatabasePath(): String {
    val userHome =
        Path.of(
            System.getProperty("user.home"),
        )

    return prepareSimplioDatabasePath(
        userHome = userHome,
    ).toString()
}

fun prepareSimplioDatabasePath(userHome: Path): Path {
    val path =
        simplioDatabasePath(
            userHome = userHome,
        )

    Files.createDirectories(
        path.parent,
    )

    return path
}
