package com.k33.platform.user

import com.k33.platform.email.Email
import com.k33.platform.email.EmailTemplateConfig
import com.k33.platform.email.MailTemplate
import com.k33.platform.email.getEmailService
import com.k33.platform.utils.analytics.Log
import com.k33.platform.utils.config.loadConfig
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object UserEventHandler {

    private val emailService by getEmailService()

    private val welcomeEmail by loadConfig<EmailTemplateConfig>(
        "userService",
        "services.user.welcomeEmail"
    )

    private val researchWelcomeEmail by loadConfig<EmailTemplateConfig>(
        "userService",
        "services.user.researchWelcomeEmail"
    )

    suspend fun onNewUserCreated(
        email: String,
        userAnalyticsId: String,
        webClientId: String?,
        idProvider: String?,
    ) {
        coroutineScope {
            launch {
                emailService.sendEmail(
                    from = Email(
                        address = welcomeEmail.from.email,
                        label = welcomeEmail.from.label,
                    ),
                    toList = listOf(Email(email)),
                    mail = MailTemplate(welcomeEmail.sendgridTemplateId),
                    unsubscribeSettings = welcomeEmail.unsubscribeSettings,
                )
            }
            launch {
                emailService.sendEmail(
                    from = Email(
                        address = researchWelcomeEmail.from.email,
                        label = researchWelcomeEmail.from.label,
                    ),
                    toList = listOf(Email(email)),
                    mail = MailTemplate(researchWelcomeEmail.sendgridTemplateId),
                    unsubscribeSettings = researchWelcomeEmail.unsubscribeSettings,
                )
            }
            launch {
                Log.signUp(
                    webClientId = webClientId,
                    userAnalyticsId = userAnalyticsId,
                    idProvider = idProvider,
                )
            }
        }
    }
}