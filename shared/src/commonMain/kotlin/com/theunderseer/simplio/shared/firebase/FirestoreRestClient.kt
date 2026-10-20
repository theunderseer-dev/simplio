package com.theunderseer.simplio.shared.firebase

import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthException
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Instant

class FirestoreRestClient(
    private val httpClient: HttpClient,
    private val config: FirebaseConfig,
    private val sessionManager: FirebaseSessionManager,
    val userId: String,
) {
    val projectId: String get() = config.projectId

    init {
        require(projectId.matches(Regex("[a-zA-Z0-9-]+")))
        requireSegment(userId)
    }

    fun documentName(collection: String, id: String): String {
        requireSegment(collection)
        requireSegment(id)
        return "projects/$projectId/databases/(default)/documents/users/$userId/$collection/$id"
    }

    suspend fun getDocument(collection: String, id: String): FirestoreDocument? {
        val name = documentName(collection, id)
        val response = authorized { token ->
            httpClient.get(endpoint(name)) { bearerAuth(token) }
        }
        if (response.status.value == 404) return null
        checkStatus(response)
        return parseResponse {
            val document = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            require(document.getValue("name").jsonPrimitive.content == name)
            FirestoreDocument(
                fields = document["fields"]?.jsonObject ?: JsonObject(emptyMap()),
                updateTime = timestamp(document.getValue("updateTime").jsonPrimitive.content),
            )
        }
    }

    /** Capture a server read time, including when the requested document does not exist. */
    suspend fun readDocumentSnapshot(collection: String, id: String): FirestoreReadSnapshot {
        val name = documentName(collection, id)
        val body = buildJsonObject { put("documents", JsonArray(listOf(JsonPrimitive(name)))) }.toString()
        val response = authorized { token ->
            httpClient.post(endpoint("projects/$projectId/databases/(default)/documents:batchGet")) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        checkStatus(response)
        return parseResponse {
            val rows = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            require(rows.size == 1)
            val row = rows.single().jsonObject
            val readTime = timestamp(row.getValue("readTime").jsonPrimitive.content)
            val found = row["found"]?.jsonObject
            val document = if (found != null) {
                require(row["missing"] == null)
                require(found.getValue("name").jsonPrimitive.content == name)
                FirestoreDocument(
                    fields = found["fields"]?.jsonObject ?: JsonObject(emptyMap()),
                    updateTime = timestamp(found.getValue("updateTime").jsonPrimitive.content),
                )
            } else {
                require(row.getValue("missing").jsonPrimitive.content == name)
                null
            }
            require(document == null || Instant.parse(document.updateTime) <= Instant.parse(readTime))
            FirestoreReadSnapshot(document, readTime)
        }
    }

    /** Query one collection at a pinned server time, ordered by the document name. */
    suspend fun queryDocumentsAt(
        collection: String,
        readTime: String,
        afterId: String? = null,
        pageSize: Int = 500,
    ): FirestoreDocumentPage {
        requireSegment(collection)
        timestamp(readTime)
        require(pageSize in 1..500)
        val prefix = "projects/$projectId/databases/(default)/documents/users/$userId/$collection"
        val body = buildJsonObject {
            put("readTime", readTime)
            put(
                "structuredQuery",
                buildJsonObject {
                    put("from", JsonArray(listOf(buildJsonObject { put("collectionId", collection) })))
                    put(
                        "orderBy",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("field", buildJsonObject { put("fieldPath", "__name__") })
                                    put("direction", "ASCENDING")
                                },
                            ),
                        ),
                    )
                    put("limit", pageSize)
                    if (afterId != null) {
                        put(
                            "startAt",
                            buildJsonObject {
                                put(
                                    "values",
                                    JsonArray(
                                        listOf(
                                            buildJsonObject {
                                                put("referenceValue", documentName(collection, afterId))
                                            },
                                        ),
                                    ),
                                )
                                put("before", false)
                            },
                        )
                    }
                },
            )
        }.toString()
        val response = authorized { token ->
            httpClient.post(endpoint("projects/$projectId/databases/(default)/documents/users/$userId:runQuery")) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        checkStatus(response)
        return parseResponse {
            val rows = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            require(rows.isNotEmpty())
            val documents = rows.mapNotNull { element ->
                val row = element.jsonObject
                require(row["error"] == null)
                require(Instant.parse(row.getValue("readTime").jsonPrimitive.content) == Instant.parse(readTime))
                val document = row["document"]?.jsonObject ?: return@mapNotNull null
                val name = document.getValue("name").jsonPrimitive.content
                require(name.startsWith("$prefix/"))
                val id = name.removePrefix("$prefix/")
                requireSegment(id)
                val version = timestamp(document.getValue("updateTime").jsonPrimitive.content)
                require(Instant.parse(version) <= Instant.parse(readTime))
                FirestoreNamedDocument(id, document["fields"]?.jsonObject ?: JsonObject(emptyMap()), version)
            }
            require(documents.size <= pageSize)
            require(documents.map { it.id }.distinct().size == documents.size)
            FirestoreDocumentPage(documents, if (documents.size == pageSize) documents.last().id else null)
        }
    }

    suspend fun commit(body: String): List<String> {
        val writes = Json.parseToJsonElement(body).jsonObject.getValue("writes").jsonArray
        require(writes.isNotEmpty())
        val prefix = "projects/$projectId/databases/(default)/documents/users/$userId/"
        writes.forEach { write ->
            val name = write.jsonObject.getValue("update").jsonObject.getValue("name").jsonPrimitive.content
            require(name.startsWith(prefix))
            val segments = name.removePrefix(prefix).split('/')
            require(segments.size == 2)
            segments.forEach(::requireSegment)
        }
        val response = authorized { token ->
            httpClient.post(endpoint("projects/$projectId/databases/(default)/documents:commit")) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        checkStatus(response)
        return parseResponse {
            val results = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                .getValue("writeResults").jsonArray
            require(results.size == writes.size)
            results.map { timestamp(it.jsonObject.getValue("updateTime").jsonPrimitive.content) }
        }
    }

    private suspend fun authorized(request: suspend (String) -> HttpResponse): HttpResponse {
        if (sessionManager.session.value == null) throw FirestoreFailure(FirestoreFailureKind.AUTHENTICATION)
        val session = try {
            sessionManager.getValidSession()
        } catch (error: FirebaseAuthException) {
            throw FirestoreFailure(
                if (error.requiresSignIn) FirestoreFailureKind.AUTHENTICATION else FirestoreFailureKind.RETRYABLE,
            )
        }
        if (session.userId != userId) throw CancellationException("Cloud account changed")
        val response = request(session.idToken)
        if (sessionManager.session.value?.userId != userId) throw CancellationException("Cloud account changed")
        return response
    }

    private suspend fun checkStatus(response: HttpResponse) {
        if (response.status.value in 200..299) return
        val status = runCatching {
            Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]?.jsonObject
                ?.get("status")?.jsonPrimitive?.content
        }.getOrNull()
        val kind = when (status) {
            "FAILED_PRECONDITION", "ALREADY_EXISTS", "ABORTED" -> FirestoreFailureKind.CONFLICT
            "UNAUTHENTICATED" -> FirestoreFailureKind.AUTHENTICATION
            "PERMISSION_DENIED" -> FirestoreFailureKind.PERMISSION
            else -> when (response.status.value) {
                401 -> FirestoreFailureKind.AUTHENTICATION
                403 -> FirestoreFailureKind.PERMISSION
                409, 412 -> FirestoreFailureKind.CONFLICT
                408, 429, 502, 503, 504 -> FirestoreFailureKind.RETRYABLE
                500 -> FirestoreFailureKind.INTERNAL
                else -> FirestoreFailureKind.REJECTED
            }
        }
        throw FirestoreFailure(kind)
    }

    private suspend fun <T> parseResponse(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw FirestoreFailure(FirestoreFailureKind.INVALID_RESPONSE)
    }

    private fun endpoint(resource: String): String = URLBuilder("https://firestore.googleapis.com/v1/")
        .apply { appendPathSegments(resource.split('/')) }
        .buildString()

    private fun timestamp(value: String): String = value.also { Instant.parse(it) }

    private fun requireSegment(value: String) {
        require(value.isNotBlank() && '/' !in value && value != "." && value != "..")
    }
}

data class FirestoreDocument(val fields: JsonObject, val updateTime: String)

enum class FirestoreFailureKind {
    AUTHENTICATION,
    PERMISSION,
    CONFLICT,
    RETRYABLE,
    INTERNAL,
    REJECTED,
    INVALID_RESPONSE,
}

class FirestoreFailure(val kind: FirestoreFailureKind) : Exception("Firestore request failed: $kind")

data class FirestoreReadSnapshot(val document: FirestoreDocument?, val readTime: String)
data class FirestoreNamedDocument(val id: String, val fields: JsonObject, val updateTime: String)
data class FirestoreDocumentPage(val documents: List<FirestoreNamedDocument>, val nextAfterId: String?)
