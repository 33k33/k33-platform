package com.k33.platform.payment

import com.k33.platform.identity.auth.gcp.UserInfo
import com.k33.platform.payment.stripe.AlreadySubscribed
import com.k33.platform.payment.stripe.BadRequest
import com.k33.platform.payment.stripe.NotFound
import com.k33.platform.payment.stripe.PaymentServiceError
import com.k33.platform.payment.stripe.StripeClient
import com.k33.platform.user.UserId
import com.k33.platform.user.UserService.fetchUser
import com.k33.platform.utils.logging.logWithMDC
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

fun Application.module() {

    routing {
        authenticate("esp-v2-header") {
            route("/payment") {
                route("/subscribed-products/{productId}") {
                    get {
                        val userId = UserId(call.principal<UserInfo>()!!.userId)
                        logWithMDC("userId" to userId.value) {
                            val productId: String = call.parameters["productId"]
                                ?: throw BadRequest("Path param: productId is mandatory")
                            try {
                                val userEmail = call.principal<UserInfo>()!!.email
                                val productSubscription = StripeClient.getSubscription(
                                    customerEmail = userEmail,
                                    productId = productId,
                                )
                                if (productSubscription == null) {
                                    call.respond(HttpStatusCode.NotFound)
                                } else {
                                    call.respond(
                                        SubscribedProduct(
                                            productId = productSubscription.productId,
                                            status = productSubscription.status,
                                            priceId = productSubscription.priceId,
                                        )
                                    )
                                }
                            } catch (e: PaymentServiceError) {
                                call.application.log.error("Payment service error in fetching subscribed products", e)
                                call.respond(e.httpStatusCode, e.message)
                            } catch (e: Exception) {
                                call.application.log.error("Exception in fetching subscribed products", e)
                                call.respond(HttpStatusCode.InternalServerError)
                            }
                        }
                    }
                }
                post("checkout-sessions") {
                    val userId = UserId(call.principal<UserInfo>()!!.userId)
                    logWithMDC("userId" to userId.value) {
                        try {
                            val userAnalyticsId = userId.fetchUser()
                                ?.analyticsId
                                ?: UUID.randomUUID().toString()
                            val webClientId = call.request.header("x-client-id")
                                ?: UUID.randomUUID().toString()
                            val userEmail = call.principal<UserInfo>()!!.email
                            val request = call.receive<CheckoutSessionRequest>()
                            val checkoutSession = StripeClient.createOrFetchCheckoutSession(
                                customerEmail = userEmail,
                                priceId = request.priceId,
                                successUrl = request.successUrl,
                                cancelUrl = request.cancelUrl,
                                webClientId = webClientId,
                                userAnalyticsId = userAnalyticsId,
                            )
                            call.respond(
                                CheckoutSessionResponse(
                                    url = checkoutSession.url,
                                    expiresAt = checkoutSession.expiresAt,
                                    priceId = checkoutSession.priceId,
                                    successUrl = checkoutSession.successUrl,
                                    cancelUrl = checkoutSession.cancelUrl,
                                )
                            )
                        } catch (e: AlreadySubscribed) {
                            call.respond(e.httpStatusCode, e.message)
                        } catch (e: PaymentServiceError) {
                            call.application.log.error("Payment service error in create/fetch checkout session", e)
                            call.respond(e.httpStatusCode, e.message)
                        } catch (e: Exception) {
                            call.application.log.error("Exception in create/fetch checkout session", e)
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                }
                post("customer-portal-sessions") {
                    val userId = UserId(call.principal<UserInfo>()!!.userId)
                    logWithMDC("userId" to userId.value) {
                        try {
                            val userEmail = call.principal<UserInfo>()!!.email
                            val request = call.receive<CustomerPortalSessionRequest>()
                            val customerPortalSession = StripeClient.createCustomerPortalSession(
                                customerEmail = userEmail,
                                returnUrl = request.returnUrl,
                            )
                            call.respond(
                                CustomerPortalSessionResponse(
                                    url = customerPortalSession.url,
                                    returnUrl = customerPortalSession.returnUrl,
                                )
                            )
                        } catch (e: NotFound) {
                            call.respond(e.httpStatusCode, e.message)
                        } catch (e: PaymentServiceError) {
                            call.application.log.error("Payment service error in create customer portal session", e)
                            call.respond(e.httpStatusCode, e.message)
                        } catch (e: Exception) {
                            call.application.log.error("Exception in create customer portal session", e)
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }
        }
    }
}


@Serializable
data class SubscribedProducts(
    val subscribedProducts: Collection<String>
)

@Serializable
data class SubscribedProduct(
    val productId: String,
    val priceId: String,
    val status: StripeClient.ProductSubscriptionStatus,
)

@Serializable
data class CheckoutSessionRequest(
    val priceId: String,
    val successUrl: String,
    val cancelUrl: String,
)

@Serializable
data class CheckoutSessionResponse(
    val url: String,
    val expiresAt: String,
    val priceId: String,
    val successUrl: String,
    val cancelUrl: String,
)

@Serializable
data class CustomerPortalSessionRequest(
    val returnUrl: String,
)

@Serializable
data class CustomerPortalSessionResponse(
    val url: String,
    val returnUrl: String,
)