package com.theunderseer.simplio.di

import com.theunderseer.simplio.shared.firebase.FirebaseConfig
import com.theunderseer.simplio.shared.firebase.FirestoreAccessChecker
import com.theunderseer.simplio.shared.firebase.FirestoreRestClient
import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthClient
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import com.theunderseer.simplio.shared.firebase.createFirebaseHttpClient
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.util.Properties

internal fun desktopFirebaseModule() = module {
    single {
        loadFirebaseConfig()
    }

    single {
        createFirebaseHttpClient()
    } onClose { client ->
        client?.close()
    }

    single {
        FirebaseAuthClient(
            httpClient = get(),
            config = get(),
        )
    }

    single {
        FirestoreAccessChecker(
            httpClient = get(),
            config = get(),
            sessionManager = get(),
        )
    }

    factory { parameters ->
        FirestoreRestClient(get(), get(), get(), userId = parameters.get())
    }

    single {
        FirebaseSessionManager(
            authApi = get<FirebaseAuthClient>(),
        )
    }
}

private fun loadFirebaseConfig(): FirebaseConfig {
    val properties = Properties()

    val stream = checkNotNull(
        FirebaseConfig::class.java.getResourceAsStream("/firebase.properties"),
    ) {
        "firebase.properties was not found in application resources"
    }

    stream.use {
        properties.load(it)
    }

    fun requiredProperty(name: String): String = checkNotNull(
        properties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() },
    ) {
        "Missing Firebase configuration property: $name"
    }

    return FirebaseConfig(
        apiKey = requiredProperty("firebase.apiKey"),
        projectId = requiredProperty("firebase.projectId"),
    )
}
