package com.nuvio.app.features.home

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.details.MetaExternalRating

data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val banner: String? = null,
    val logo: String? = null,
    val posterShape: PosterShape = PosterShape.Poster,
    val description: String? = null,
    val releaseInfo: String? = null,
    val rawReleaseDate: String? = null,
    val popularity: Double? = null,
    val voteCount: Int? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val rawPosterUrl: String? = null,
    val landscapePoster: String? = null,
    val rawLandscapePosterUrl: String? = null,
    val runtime: String? = null,
    val status: String? = null,
    val ageRating: String? = null,
    val language: String? = null,
    val externalRatings: List<MetaExternalRating> = emptyList(),
)

fun MetaPreview.stableKey(): String = "$type:$id"

enum class PosterShape {
    Poster,
    Square,
    Landscape,
}

data class HomeCatalogSection(
    val key: String,
    val title: String,
    val subtitle: String,
    val addonName: String,
    val target: CatalogTarget,
    val items: List<MetaPreview>,
    val availableItemCount: Int = items.size,
    val hasMore: Boolean = false,
)

const val TOP_10_ITEM_LIMIT = 10

private val TOP_10_REGEX = Regex("""(?i)(?:^|[^a-z0-9])top[\s_-]*(?:10|ten)(?:$|[^a-z0-9])""")

fun HomeCatalogSection.canOpenCatalog(previewLimit: Int): Boolean =
    availableItemCount > previewLimit || hasMore

fun HomeCatalogSection.isTop10Catalog(parentTitle: String? = null): Boolean {
    if (TOP_10_REGEX.containsMatchIn(title)) return true
    if (!parentTitle.isNullOrBlank() && TOP_10_REGEX.containsMatchIn(parentTitle)) return true
    val catalogId = (target as? CatalogTarget.Addon)?.catalogId.orEmpty()
    return catalogId.isNotBlank() && TOP_10_REGEX.containsMatchIn(catalogId)
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val heroItems: List<MetaPreview> = emptyList(),
    val sections: List<HomeCatalogSection> = emptyList(),
    val errorMessage: String? = null,
)

internal fun shouldShowInitialHomeLoading(
    hasRenderableHomeRows: Boolean,
    addonManifestsLoading: Boolean,
    homeCatalogLoading: Boolean,
): Boolean = !hasRenderableHomeRows && (addonManifestsLoading || homeCatalogLoading)

internal fun shouldShowHomeHeroSlot(
    heroEnabled: Boolean,
    hasHeroItems: Boolean,
    isResolvingHeroSources: Boolean,
    hasRenderableHomeRows: Boolean,
): Boolean = heroEnabled && (hasHeroItems || isResolvingHeroSources || hasRenderableHomeRows)

internal data class CatalogRequest(
    val addon: ManagedAddon,
    val catalogId: String,
    val catalogName: String,
    val type: String,
    val supportsPagination: Boolean,
)
