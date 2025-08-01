package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Domain entity representing comprehensive page metadata extracted during screenshot generation.
 * Provides SEO, performance, and content analysis data.
 */
@Serializable
data class PageMetadata(
    val seo: SeoData,
    val performance: PerformanceData,
    val content: ContentData,
    val social: SocialMediaData,
    val technical: TechnicalData,
    val extractedAt: Instant
)

/**
 * SEO-related metadata extracted from the page
 */
@Serializable
data class SeoData(
    val title: String?,
    val metaDescription: String?,
    val metaKeywords: String?,
    val canonicalUrl: String?,
    val robotsMeta: String?,
    val headings: HeadingStructure,
    val imageAlts: List<String>,
    val internalLinks: Int,
    val externalLinks: Int,
    val schemaMarkup: List<String>
)

/**
 * Performance metrics of the page
 */
@Serializable
data class PerformanceData(
    val loadTimeMs: Long?,
    val domContentLoadedMs: Long?,
    val resourceCount: ResourceCount,
    val pageSize: PageSize,
    val httpStatus: Int
)

/**
 * Content analysis data
 */
@Serializable
data class ContentData(
    val wordCount: Int,
    val textLength: Int,
    val language: String?,
    val headingCount: Int,
    val imageCount: Int,
    val linkCount: Int,
    val hasContactInfo: Boolean,
    val readabilityScore: Double? = null
)

/**
 * Social media optimization data
 */
@Serializable
data class SocialMediaData(
    val openGraph: OpenGraphData,
    val twitterCard: TwitterCardData,
    val facebookData: FacebookData
)

/**
 * Technical SEO data
 */
@Serializable
data class TechnicalData(
    val hasStructuredData: Boolean,
    val schemaTypes: List<String>,
    val hasRobotsTxt: Boolean,
    val hasSitemap: Boolean,
    val viewport: String?,
    val charset: String?,
    val contentType: String?,
    val generator: String?
)

/**
 * Heading structure for SEO analysis
 */
@Serializable
data class HeadingStructure(
    val h1: List<String>,
    val h2: List<String>,
    val h3: List<String>,
    val h4: List<String>,
    val h5: List<String>,
    val h6: List<String>
) {
    val totalCount: Int get() = h1.size + h2.size + h3.size + h4.size + h5.size + h6.size
    val hasProperStructure: Boolean get() = h1.isNotEmpty() && h1.size == 1
}

/**
 * Resource count breakdown
 */
@Serializable
data class ResourceCount(
    val total: Int,
    val images: Int,
    val scripts: Int,
    val stylesheets: Int,
    val fonts: Int,
    val others: Int
)

/**
 * Page size breakdown
 */
@Serializable
data class PageSize(
    val totalBytes: Long,
    val htmlBytes: Long,
    val cssBytes: Long,
    val jsBytes: Long,
    val imageBytes: Long,
    val fontBytes: Long,
    val otherBytes: Long
) {
    val totalKB: Double get() = totalBytes / 1024.0
    val totalMB: Double get() = totalKB / 1024.0
}

/**
 * Open Graph metadata
 */
@Serializable
data class OpenGraphData(
    val title: String?,
    val description: String?,
    val image: String?,
    val url: String?,
    val type: String?,
    val siteName: String?
)

/**
 * Twitter Card metadata
 */
@Serializable
data class TwitterCardData(
    val card: String?,
    val title: String?,
    val description: String?,
    val image: String?,
    val site: String?,
    val creator: String?
)

/**
 * Facebook-specific metadata
 */
@Serializable
data class FacebookData(
    val appId: String?,
    val admins: List<String>,
    val pages: List<String>
)