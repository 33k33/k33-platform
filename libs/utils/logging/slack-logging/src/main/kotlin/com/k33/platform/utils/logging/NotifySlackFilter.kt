package com.k33.platform.utils.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import com.k33.platform.utils.slack.Channel
import com.k33.platform.utils.slack.ChannelId
import com.k33.platform.utils.slack.ChannelName
import com.k33.platform.utils.slack.SlackClient
import org.slf4j.Marker

class NotifySlackFilter : Filter<ILoggingEvent>() {

    init {
        super.setName("Notify Slack Filter")
    }

    private val slackChannel by lazy {
        System.getenv("SLACK_ALERTS_CHANNEL_ID")?.let { ChannelId(it) }
            ?: System.getenv("SLACK_ALERTS_CHANNEL_NAME")?.let { ChannelName(it) }
            ?: ChannelName("gcp-alerts")
    }

    private val slackInvestChannel by lazy {
        System.getenv("SLACK_INVEST_CHANNEL_ID")?.let { ChannelId(it) }
            ?: System.getenv("SLACK_INVEST_CHANNEL_NAME")?.let { ChannelName(it) }
            ?: ChannelName("invest")
    }

    private val slackGeneralChannel by lazy {
        System.getenv("SLACK_GENERAL_CHANNEL_ID")?.let { ChannelId(it) }
            ?: System.getenv("SLACK_GENERAL_CHANNEL_NAME")?.let { ChannelName(it) }
            ?: ChannelName("general")
    }

    private val slackProductChannel by lazy {
        System.getenv("SLACK_PRODUCT_CHANNEL_ID")?.let { ChannelId(it) }
            ?: System.getenv("SLACK_PRODUCT_CHANNEL_NAME")?.let { ChannelName(it) }
            ?: ChannelName("product")
    }

    private val slackResearchChannel by lazy {
        System.getenv("SLACK_RESEARCH_CHANNEL_ID")?.let { ChannelId(it) }
            ?: System.getenv("SLACK_RESEARCH_CHANNEL_NAME")?.let { ChannelName(it) }
            ?: ChannelName("research")
    }

    private fun getChannel(marker: Marker): Channel? {
        val notifySlack = marker.asNotifySlack() ?: return null
        return when (notifySlack) {
            NotifySlack.NOTIFY_SLACK_ALERTS -> slackChannel
            NotifySlack.NOTIFY_SLACK_GENERAL -> slackGeneralChannel
            NotifySlack.NOTIFY_SLACK_INVEST -> slackInvestChannel
            NotifySlack.NOTIFY_SLACK_PRODUCT -> slackProductChannel
            NotifySlack.NOTIFY_SLACK_RESEARCH -> slackResearchChannel
        }
    }

    override fun decide(event: ILoggingEvent?): FilterReply {
        if (event != null) {
            val channels = event.markerList.mapNotNull(::getChannel)
            if (channels.isEmpty()) {
                return FilterReply.NEUTRAL
            }
            val header = when (event.level) {
                Level.ERROR -> "🔥 Error"
                Level.WARN -> "⚠️ Warning"
                Level.INFO -> "ℹ Info"
                Level.DEBUG -> "🩺 Debug"
                Level.TRACE -> "👣 Trace"
                else -> event.level.levelStr
            }
            val mdcMap = event.mdcPropertyMap ?: emptyMap()
            runBlocking {
                channels.map { channel ->
                    async {
                        SlackClient.sendRichMessage(
                            channel = channel,
                            altPlainTextMessage = header + " " + event.message,
                        ) {
                            header {
                                text(header, emoji = true)
                            }
                            section {
                                markdownText("```${event.message}```")
                                if (mdcMap.isNotEmpty()) {
                                    fields {
                                        mdcMap.forEach { (key, value) ->
                                            markdownText("*$key*: `$value`")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        return FilterReply.NEUTRAL
    }
}