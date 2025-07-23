package dev.screenshotapi.infrastructure.adapters.input.rest.dto

import dev.screenshotapi.core.domain.entities.*
import kotlinx.serialization.Serializable

@Serializable
data class PageMetadataDto(
    val seo: SeoDataDto,
    val performance: PerformanceDataDto,
    val content: ContentDataDto,
    val social: SocialMediaDataDto,
    val technical: TechnicalDataDto,
    val extractedAt: String
)

@Serializable
data class SeoDataDto(
    val title: String?,
    val metaDescription: String?,
    val metaKeywords: String?,
    val canonicalUrl: String?,
    val robotsMeta: String?,
    val headings: HeadingStructureDto,
    val imageAlts: List<String>,
    val internalLinks: Int,
    val externalLinks: Int,
    val schemaMarkup: List<String>
)

@Serializable
data class PerformanceDataDto(
    val loadTimeMs: Long?,
    val domContentLoadedMs: Long?,
    val resourceCount: ResourceCountDto,
    val pageSize: PageSizeDto,
    val httpStatus: Int
)

@Serializable
data class ContentDataDto(
    val wordCount: Int,
    val textLength: Int,
    val language: String?,
    val headingCount: Int,
    val imageCount: Int,
    val linkCount: Int,
    val hasContactInfo: Boolean,
    val readabilityScore: Double? = null
)

@Serializable
data class SocialMediaDataDto(
    val openGraph: OpenGraphDataDto,
    val twitterCard: TwitterCardDataDto,
    val facebookData: FacebookDataDto
)

@Serializable
data class TechnicalDataDto(
    val hasStructuredData: Boolean,
    val schemaTypes: List<String>,
    val hasRobotsTxt: Boolean,
    val hasSitemap: Boolean,
    val viewport: String?,
    val charset: String?,
    val contentType: String?,
    val generator: String?
)

@Serializable
data class HeadingStructureDto(
    val h1: List<String>,
    val h2: List<String>,
    val h3: List<String>,
    val h4: List<String>,
    val h5: List<String>,
    val h6: List<String>,
    val totalCount: Int,
    val hasProperStructure: Boolean
)

@Serializable
data class ResourceCountDto(
    val total: Int,
    val images: Int,
    val scripts: Int,
    val stylesheets: Int,
    val fonts: Int,
    val others: Int
)

@Serializable
data class PageSizeDto(
    val totalBytes: Long,
    val htmlBytes: Long,
    val cssBytes: Long,
    val jsBytes: Long,
    val imageBytes: Long,
    val fontBytes: Long,
    val otherBytes: Long,
    val totalKB: Double,
    val totalMB: Double
)

@Serializable
data class OpenGraphDataDto(
    val title: String?,
    val description: String?,
    val image: String?,
    val url: String?,
    val type: String?,
    val siteName: String?
)

@Serializable
data class TwitterCardDataDto(
    val card: String?,
    val title: String?,
    val description: String?,
    val image: String?,
    val site: String?,
    val creator: String?
)

@Serializable
data class FacebookDataDto(
    val appId: String?,
    val admins: List<String>,
    val pages: List<String>
)

// Mapping functions from domain to DTO
fun PageMetadata.toDto() = PageMetadataDto(
    seo = seo.toDto(),
    performance = performance.toDto(),
    content = content.toDto(),
    social = social.toDto(),
    technical = technical.toDto(),
    extractedAt = extractedAt.toString()
)

fun SeoData.toDto() = SeoDataDto(
    title = title,
    metaDescription = metaDescription,
    metaKeywords = metaKeywords,
    canonicalUrl = canonicalUrl,
    robotsMeta = robotsMeta,
    headings = headings.toDto(),
    imageAlts = imageAlts,
    internalLinks = internalLinks,
    externalLinks = externalLinks,
    schemaMarkup = schemaMarkup
)

fun PerformanceData.toDto() = PerformanceDataDto(
    loadTimeMs = loadTimeMs,
    domContentLoadedMs = domContentLoadedMs,
    resourceCount = resourceCount.toDto(),
    pageSize = pageSize.toDto(),
    httpStatus = httpStatus
)

fun ContentData.toDto() = ContentDataDto(
    wordCount = wordCount,
    textLength = textLength,
    language = language,
    headingCount = headingCount,
    imageCount = imageCount,
    linkCount = linkCount,
    hasContactInfo = hasContactInfo,
    readabilityScore = readabilityScore
)

fun SocialMediaData.toDto() = SocialMediaDataDto(
    openGraph = openGraph.toDto(),
    twitterCard = twitterCard.toDto(),
    facebookData = facebookData.toDto()
)

fun TechnicalData.toDto() = TechnicalDataDto(
    hasStructuredData = hasStructuredData,
    schemaTypes = schemaTypes,
    hasRobotsTxt = hasRobotsTxt,
    hasSitemap = hasSitemap,
    viewport = viewport,
    charset = charset,
    contentType = contentType,
    generator = generator
)

fun HeadingStructure.toDto() = HeadingStructureDto(
    h1 = h1,
    h2 = h2,
    h3 = h3,
    h4 = h4,
    h5 = h5,
    h6 = h6,
    totalCount = totalCount,
    hasProperStructure = hasProperStructure
)

fun ResourceCount.toDto() = ResourceCountDto(
    total = total,
    images = images,
    scripts = scripts,
    stylesheets = stylesheets,
    fonts = fonts,
    others = others
)

fun PageSize.toDto() = PageSizeDto(
    totalBytes = totalBytes,
    htmlBytes = htmlBytes,
    cssBytes = cssBytes,
    jsBytes = jsBytes,
    imageBytes = imageBytes,
    fontBytes = fontBytes,
    otherBytes = otherBytes,
    totalKB = totalKB,
    totalMB = totalMB
)

fun OpenGraphData.toDto() = OpenGraphDataDto(
    title = title,
    description = description,
    image = image,
    url = url,
    type = type,
    siteName = siteName
)

fun TwitterCardData.toDto() = TwitterCardDataDto(
    card = card,
    title = title,
    description = description,
    image = image,
    site = site,
    creator = creator
)

fun FacebookData.toDto() = FacebookDataDto(
    appId = appId,
    admins = admins,
    pages = pages
)