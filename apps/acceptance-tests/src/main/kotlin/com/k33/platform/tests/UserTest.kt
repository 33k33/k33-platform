package com.k33.platform.tests

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import java.util.UUID

@kotlin.time.ExperimentalTime
class UserTest : BehaviorSpec({

    suspend fun getUser(userId: String): HttpResponse {
        return apiClient.get {
            url(path = "user")
            headers {
                appendEndpointsApiUserInfoHeader(userId)
                expectSuccess = false
            }
        }
    }

    given("user does not exists") {
        val userId = UUID.randomUUID().toString()
        `when`("GET /user to check if unregistered user exists") {
            then("response is 404 NOT FOUND") {
                getUser(userId = userId).status shouldBe HttpStatusCode.NotFound
            }
        }
        `when`("POST /user to register a user") {
            val user = apiClient.post {
                url(path = "user")
                headers {
                    appendEndpointsApiUserInfoHeader(userId)
                }
            }.body<User>()
            then("response should be user object") {
                user.userId shouldBe userId
            }
            then("GET /user to check if registered user exists, should be user object") {
                getUser(userId = userId).body<User>().userId shouldBe userId
            }
        }
    }
})

@Serializable
data class User(
    val userId: String,
    val analyticsId: String,
)