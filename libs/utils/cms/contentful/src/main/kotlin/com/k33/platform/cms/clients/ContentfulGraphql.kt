package com.k33.platform.cms.clients

import com.k33.platform.cms.utils.forEachInArrayAt
import com.k33.platform.utils.logging.getLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import net.andreinc.mapneat.dsl.MapNeat
import net.andreinc.mapneat.dsl.json

class ContentfulGraphql(
    spaceId: String,
    token: String,
    private val type: String,
    private val transform: MapNeat.() -> Unit,
) {

    private val logger by getLogger()

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.NONE
        }
        defaultRequest {
            url("https://graphql.contentful.com/content/v1/spaces/$spaceId")
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
        }
    }

    private suspend fun fetchResponse(
        query: String,
        vararg variables: Pair<String, Any>
    ): String? {
        val graphqlRequest = buildJsonObject {
            put("query", JsonPrimitive(query))
            put("variables",
                buildJsonObject {
                    variables.forEach { (key, value) ->
                        when (value) {
                            is String -> put(key, JsonPrimitive(value))
                            is Number -> put(key, JsonPrimitive(value))
                            is Boolean -> put(key, JsonPrimitive(value))
                        }
                    }
                }
            )
        }
        val response: String = withContext(Dispatchers.IO) {
            client.post {
                setBody(graphqlRequest)
            }.bodyAsText()
        }

        val jsonObject = Json.parseToJsonElement(response).jsonObject
        val errors = jsonObject["errors"]?.jsonArray
        if (!errors.isNullOrEmpty()) {
            logger.error(errors.joinToString())
            return null
        }

        return response
    }

    suspend fun fetch(
        query: String,
        vararg variables: Pair<String, Any>
    ): List<JsonObject> {
        val response: String = fetchResponse(query, *variables) ?: return emptyList()
        val transformed = json(response) {
            "objects" /= forEachInArrayAt("data.${type}Collection.items", transform)
        }
        return Json.parseToJsonElement(transformed.getString())
            .jsonObject["objects"]!!
            .jsonArray
            .map { it.jsonObject }
    }
}