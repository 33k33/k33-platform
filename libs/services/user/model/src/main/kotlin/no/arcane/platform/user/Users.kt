package no.arcane.platform.user

import dev.vihang.firestore4k.typed.rootCollection
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val userId: String,
    val analyticsId: String,
)

@JvmInline
value class UserId(val value: String) {
    override fun toString(): String = value
}

val users = rootCollection<User, UserId>("users")
