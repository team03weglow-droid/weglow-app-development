package com.example.weglow.data.repository

import com.example.weglow.domain.model.UserProfile
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Exercises real SDK request construction; the engine never connects to a server. */
class SupabaseWriteContractTest {
    @Test fun cartInsertRequestsReturnedRowsAndDoesNotRetrySuccessfulWrite() = runTest {
        val engine = ScriptedEngine(mutableListOf("[]", "[{\"quantity\":1}]"))
        val client = client(engine)
        try {
            SupabaseCartRepository(client).addToCart("alice", 7).getOrThrow()
            assertEquals(2, engine.requests.size)
            val insert = engine.requests.last()
            assertEquals(HttpMethod.Post, insert.method)
            assertEquals("profile_id,product_no", insert.url.parameters["on_conflict"])
            assertRepresentation(insert)
            assertEquals("quantity", insert.url.parameters["select"])
            val body = insert.jsonBody()
            assertEquals("alice", body["profile_id"]?.jsonPrimitive?.content)
            assertEquals(7, body["product_no"]?.jsonPrimitive?.int)
            assertFalse(body.containsKey("id"))
        } finally { client.close() }
    }

    @Test fun cartRetriesAnInsertConflictThenIncrementsWithOwnerAndQuantityFilters() = runTest {
        val engine = ScriptedEngine(mutableListOf("[]", "[]", "[{\"quantity\":2}]", "[{\"quantity\":3}]"))
        val client = client(engine)
        try {
            SupabaseCartRepository(client).addToCart("alice", 7).getOrThrow()
            assertEquals(4, engine.requests.size)
            val update = engine.requests.last()
            assertEquals(HttpMethod.Patch, update.method)
            assertRepresentation(update)
            assertEquals("eq.alice", update.url.parameters["profile_id"])
            assertEquals("eq.7", update.url.parameters["product_no"])
            assertEquals("eq.2", update.url.parameters["quantity"])
            assertEquals(3, update.jsonBody()["quantity"]?.jsonPrimitive?.int)
        } finally { client.close() }
    }

    @Test fun onboardingWritesOnlyAnswersAndPreservesUnrelatedProfileColumns() = runTest {
        val engine = ScriptedEngine(mutableListOf("[]"))
        val client = client(engine)
        try {
            SupabaseProfileRepository(client).saveProfile(UserProfile(
                id = "alice", fullName = "Alice", ageRange = "25–35",
                gender = "Female", isSkinSensitive = false, onboardingCompleted = true,
            )).getOrThrow()
            val write = engine.requests.single()
            assertEquals("/rest/v1/profiles", write.url.encodedPath)
            assertEquals("id", write.url.parameters["on_conflict"])
            val body = write.jsonBody()
            assertEquals(setOf("id", "full_name", "age_range", "skin_type", "gender",
                "is_skin_sensitive", "onboarding_completed"), body.keys)
            assertEquals("alice", body["id"]?.jsonPrimitive?.content)
            assertEquals(JsonNull, body["skin_type"])
            assertEquals(false, body["is_skin_sensitive"]?.jsonPrimitive?.boolean)
            assertEquals(true, body["onboarding_completed"]?.jsonPrimitive?.boolean)
        } finally { client.close() }
    }

    @Test fun savedProductUsesOwnerAndCatalogKeyWithoutSupplyingSurrogateId() = runTest {
        val engine = ScriptedEngine(mutableListOf("[]", "[]"))
        val client = client(engine)
        try {
            val repo = SupabaseSavedProductRepository(client)
            repo.saveProduct("alice", 7).getOrThrow()
            repo.removeSavedProduct("alice", 7).getOrThrow()
            val insert = engine.requests.first()
            assertEquals("/rest/v1/saved_products", insert.url.encodedPath)
            assertEquals("profile_id,product_no", insert.url.parameters["on_conflict"])
            assertEquals(setOf("profile_id", "product_no"), insert.jsonBody().keys)
            val delete = engine.requests.last()
            assertEquals("eq.alice", delete.url.parameters["profile_id"])
            assertEquals("eq.7", delete.url.parameters["product_no"])
        } finally { client.close() }
    }

    private fun client(engine: ScriptedEngine) = createSupabaseClient("https://contract.invalid", "test-key") {
        httpEngine = engine
        install(Postgrest)
    }

    private fun assertRepresentation(request: HttpRequestData) {
        assertTrue(request.headers.getAll("Prefer").orEmpty().joinToString(",").contains("return=representation"))
    }

    private fun HttpRequestData.jsonBody(): JsonObject {
        val text = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        val element = Json.parseToJsonElement(text)
        return if (element is JsonArray) element.single().jsonObject else element.jsonObject
    }

    @OptIn(io.ktor.utils.io.InternalAPI::class)
    private class ScriptedEngine(val replies: MutableList<String>) : HttpClientEngineBase("contract-test") {
        override val config = HttpClientEngineConfig()
        override val supportedCapabilities: Set<io.ktor.client.engine.HttpClientEngineCapability<*>> =
            setOf(io.ktor.client.plugins.HttpTimeoutCapability)
        val requests = mutableListOf<HttpRequestData>()
        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            requests += data
            check(replies.isNotEmpty()) { "Unexpected extra request: ${data.method} ${data.url}" }
            return HttpResponseData(HttpStatusCode.OK, GMTDate(),
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                HttpProtocolVersion.HTTP_1_1, ByteReadChannel(replies.removeAt(0).encodeToByteArray()),
                callContext())
        }
    }
}
