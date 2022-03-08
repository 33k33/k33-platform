package no.arcane.platform.cms.space.research

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import no.arcane.platform.cms.clients.ContentfulGraphql
import no.arcane.platform.cms.utils.optional
import no.arcane.platform.utils.config.readResource
import no.arcane.platform.utils.logging.getLogger

class ResearchPageForSlack(
    spaceId: String,
    token: String,
) {

    private val logger by getLogger()

    private val client by lazy {

        ContentfulGraphql(
            spaceId = spaceId,
            token = token,
            query = readResource("/research/queryOneForSlack.graphql").replace(Regex("\\s+"), " "),
        ).AdvancedClient(
            arrayPath = "data.pageCollection.items"
        ) {
            "title" *= "title"
            "slug" *= "slug"
            "publishedAt" *= "sys.publishedAt"
            "firstPublishedAt" *= "sys.firstPublishedAt"
            optional {
                "subtitle" *= "content.subtitle"
                "image" *= "content.image"
                "socialMediaImage" *= "content.socialMediaJpegOrPng"
                "tags" *= "content.tagsCollection.items[*].name"
                "authors" *= "content.authorsCollection.items[*]"
                "publishDate" *= "content.publishDate"
            }
        }
    }

    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
        }
    }

    suspend fun fetch(pageId: String): Page? {
        return try {
            val jsonObject = client.fetch("pageId" to pageId).singleOrNull() ?: return null
            json.decodeFromJsonElement(jsonObject)
        } catch (e: Exception) {
            logger.error("Decoding page failed", e)
            null
        }
    }
}

@Serializable
data class Page(
    val title: String,
    val slug: String,
    val subtitle: String,
    val image: Image,
    val socialMediaImage: Image?,
    val tags: List<String>,
    val authors: List<Author>,
    val publishDate: String,
    val publishedAt: String,
    val firstPublishedAt: String,
)

@Serializable
data class Author(
    val name: String,
    val image: Image,
)

@Serializable
data class Image(
    val fileName: String,
    val title: String,
    val url: String,
)