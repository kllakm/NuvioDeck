package com.nuvio.app.features.home

import com.nuvio.app.features.catalog.CatalogTarget
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeCatalogSectionTest {
    @Test
    fun `can open catalog when page has more below preview limit`() {
        val section = section(
            availableItemCount = 11,
            hasMore = true,
        )

        assertTrue(section.canOpenCatalog(previewLimit = 18))
    }

    @Test
    fun `can open catalog when non paginated section exceeds preview limit`() {
        val section = section(
            availableItemCount = 19,
            hasMore = false,
        )

        assertTrue(section.canOpenCatalog(previewLimit = 18))
    }

    @Test
    fun `cannot open catalog when section fits preview and has no more pages`() {
        val section = section(
            availableItemCount = 11,
            hasMore = false,
        )

        assertFalse(section.canOpenCatalog(previewLimit = 18))
    }

    @Test
    fun `detects top 10 catalogs accurately across titles folder names and addon ids`() {
        assertTrue(section(title = "Top 10 TV Shows in Australia Today", availableItemCount = 10, hasMore = false).isTop10Catalog())
        assertTrue(section(title = "Netflix Top-10", availableItemCount = 10, hasMore = false).isTop10Catalog())
        assertTrue(section(title = "Top Ten Movies", availableItemCount = 10, hasMore = false).isTop10Catalog())
        assertTrue(section(title = "Movies", availableItemCount = 10, hasMore = false).isTop10Catalog(parentTitle = "Top 10 Today"))
        assertTrue(section(title = "Trending", catalogId = "top_10_movies", availableItemCount = 10, hasMore = false).isTop10Catalog())

        assertFalse(section(title = "Top 100 Movies", availableItemCount = 100, hasMore = false).isTop10Catalog())
        assertFalse(section(title = "Popular", catalogId = "popular", availableItemCount = 10, hasMore = false).isTop10Catalog())
    }

    private fun section(
        availableItemCount: Int,
        hasMore: Boolean,
        title: String = "Popular",
        catalogId: String = "popular",
    ): HomeCatalogSection =
        HomeCatalogSection(
            key = "addon:movie:$catalogId",
            title = title,
            subtitle = "Addon",
            addonName = "Addon",
            target = CatalogTarget.Addon(
                manifestUrl = "https://example.com/manifest.json",
                contentType = "movie",
                catalogId = catalogId,
                supportsPagination = hasMore,
            ),
            items = listOf(
                MetaPreview(
                    id = "tt1",
                    type = "movie",
                    name = "Movie",
                ),
            ),
            availableItemCount = availableItemCount,
            hasMore = hasMore,
        )
}
