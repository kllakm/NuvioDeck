package com.nuvio.app.features.profiles

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

const val MAX_PROFILES = 6

@Serializable
data class NuvioProfile(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("profile_index") val profileIndex: Int = 1,
    val name: String = "",
    @SerialName("avatar_color_hex") val avatarColorHex: String = "#1E88E5",
    @SerialName("avatar_id") val avatarId: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("profile_background_id") val profileBackgroundId: String? = null,
    @SerialName("profile_background_url") val profileBackgroundUrl: String? = null,
    @SerialName("uses_primary_addons") val usesPrimaryAddons: Boolean = false,
    @SerialName("uses_primary_plugins") val usesPrimaryPlugins: Boolean = false,
    @SerialName("pin_enabled") val pinEnabled: Boolean = false,
    @SerialName("pin_locked_until") val pinLockedUntil: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class ProfilePushPayload(
    @SerialName("profile_index") val profileIndex: Int,
    val name: String,
    @SerialName("avatar_color_hex") val avatarColorHex: String,
    @SerialName("uses_primary_addons") val usesPrimaryAddons: Boolean = false,
    @SerialName("uses_primary_plugins") val usesPrimaryPlugins: Boolean = false,
    @SerialName("avatar_id") val avatarId: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("profile_background_id") val profileBackgroundId: String? = null,
    @SerialName("profile_background_url") val profileBackgroundUrl: String? = null,
)

@Serializable
data class PinVerifyResult(
    val unlocked: Boolean = false,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int = 0,
    val message: String? = null,
)

data class ProfileState(
    val profiles: List<NuvioProfile> = emptyList(),
    val activeProfile: NuvioProfile? = null,
    val isLoaded: Boolean = false,
    val hasEverSelectedProfile: Boolean = false,
    val rememberLastProfileEnabled: Boolean = false,
)

@Serializable
data class AvatarCatalogItem(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("storage_path") val storagePath: String = "",
    val category: String = "character",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("bg_color") val bgColor: String? = null,
    @Transient val localImageUrl: String? = null,
    @Transient val memberOnly: Boolean = false,
)

fun parseHexColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    return runCatching {
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> Color(0xFF1E88E5)
        }
    }.getOrDefault(Color(0xFF1E88E5))
}

val PROFILE_COLORS = listOf(
    "#1E88E5", "#E53935", "#43A047", "#FB8C00",
    "#8E24AA", "#00ACC1", "#F4511E", "#3949AB",
    "#C0CA33", "#D81B60", "#00897B", "#5E35B1",
    "#7CB342", "#039BE5", "#FFB300", "#6D4C41",
)

fun avatarStorageUrl(storagePath: String): String {
    if (storagePath.startsWith("https://") || storagePath.startsWith("http://")) {
        return storagePath
    }
    val cleanPath = storagePath.trim().trimStart('/')
    val base = com.nuvio.app.core.network.ServerConfigurationRepository.active.value.backendUrl
        .trim().trimEnd('/')
        .ifBlank { "https://api.nuvio.tv" }
    return "$base/storage/v1/object/public/avatars/$cleanPath"
}

fun avatarImageUrl(avatar: AvatarCatalogItem): String? =
    avatar.localImageUrl
        ?: avatar.storagePath.takeIf { it.isNotBlank() && !avatar.memberOnly }?.let(::avatarStorageUrl)

fun normalizedAvatarUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val raw = url.trim()
    if (!raw.isValidAvatarUrl()) return null
    return formatAvatarUrl(raw)
}

fun formatAvatarUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.length >= 3 && trimmed[1] == ':' && (trimmed[2] == '\\' || trimmed[2] == '/')) {
        val normalized = trimmed.replace('\\', '/')
        val encoded = normalized.split('/').joinToString("/") { segment ->
            if (segment.contains(':')) segment else segment.replace(" ", "%20")
        }
        return "file:///$encoded"
    }
    if (trimmed.startsWith("file:///", ignoreCase = true)) {
        return "file:///" + trimmed.removePrefix("file:///").removePrefix("FILE:///").replace(" ", "%20")
    }
    if (trimmed.startsWith("file:/", ignoreCase = true)) {
        return "file:///" + trimmed.removePrefix("file:/").removePrefix("FILE:/").trimStart('/').replace(" ", "%20")
    }
    if (trimmed.startsWith("/")) {
        return "file://" + trimmed.replace(" ", "%20")
    }
    return trimmed.replace(" ", "%20")
}

fun String.isValidAvatarUrl(): Boolean {
    val value = trim()
    if (value.isEmpty() || value.length > 2048) return false
    if (value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)) {
        return true
    }
    if (value.startsWith("file://", ignoreCase = true) || value.startsWith("file:/", ignoreCase = true)) {
        return true
    }
    if (value.startsWith("res://", ignoreCase = true) || value.startsWith("app://", ignoreCase = true)) {
        return true
    }
    if (value.length >= 3 && value[1] == ':' && (value[2] == '\\' || value[2] == '/')) {
        return true
    }
    if (value.startsWith("/")) {
        return true
    }
    val lower = value.lowercase()
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
        lower.endsWith(".webp") || lower.endsWith(".svg") || lower.endsWith(".gif")
}

fun profileAvatarImageUrl(profile: NuvioProfile, avatar: AvatarCatalogItem?): String? {
    normalizedAvatarUrl(profile.avatarUrl)?.let { return it }
    avatar?.let(::avatarImageUrl)?.let { return it }
    val avatarId = profile.avatarId?.trim()
    if (!avatarId.isNullOrBlank()) {
        if (avatarId.isValidAvatarUrl()) {
            return formatAvatarUrl(avatarId)
        }
        val path = if (avatarId.contains(".")) avatarId else "$avatarId.png"
        return avatarStorageUrl(path)
    }
    return null
}
