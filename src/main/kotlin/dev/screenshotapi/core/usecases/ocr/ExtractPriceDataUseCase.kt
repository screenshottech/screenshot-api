package dev.screenshotapi.core.usecases.ocr

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.exceptions.OcrException
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import org.slf4j.LoggerFactory

/**
 * Extract Price Data Use Case - Specialized OCR for price monitoring
 * GitHub Issue #2: OCR Domain Architecture
 */
class ExtractPriceDataUseCase(
    private val extractTextUseCase: ExtractTextUseCase,
    private val logUsageUseCase: LogUsageUseCase
) {
    private val logger = LoggerFactory.getLogger(ExtractPriceDataUseCase::class.java)
    
    // Price detection regex patterns
    private val pricePatterns = listOf(
        // Currency symbols with numbers
        Regex("""[$€£¥₹₽¢]\s*([0-9]{1,3}(?:[,.]?[0-9]{3})*(?:\.[0-9]{2})?)"""),
        // Numbers with currency codes
        Regex("""([0-9]{1,3}(?:[,.]?[0-9]{3})*(?:\.[0-9]{2})?) ?(USD|EUR|GBP|JPY|INR|RUB|CAD|AUD|CHF|CNY)"""),
        // Price: $XX.XX format
        Regex("""(?:price|cost|amount):\s*[$€£¥₹₽¢]\s*([0-9]{1,3}(?:[,.]?[0-9]{3})*(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
        // Starting at/from $XX.XX
        Regex("""(?:starting at|from|only)\s*[$€£¥₹₽¢]\s*([0-9]{1,3}(?:[,.]?[0-9]{3})*(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE)
    )
    
    private val currencySymbols = mapOf(
        "$" to "USD",
        "€" to "EUR", 
        "£" to "GBP",
        "¥" to "JPY",
        "₹" to "INR",
        "₽" to "RUB",
        "¢" to "USD"
    )

    suspend fun invoke(request: OcrRequest): OcrResult {
        val analysisType = request.analysisType ?: AnalysisType.BASIC_OCR
        logger.info("Starting price data extraction for user ${request.userId}, analysisType: ${analysisType.name}")
        
        // Enhance request for price monitoring
        val priceRequest = request.copy(
            useCase = OcrUseCase.PRICE_MONITORING,
            options = request.options.copy(
                extractPrices = true,
                enableStructuredData = true
            )
        )
        
        // Perform base OCR extraction
        val baseResult = extractTextUseCase.invoke(priceRequest)
        
        if (!baseResult.success) {
            return baseResult
        }
        
        try {
            // Extract prices from the text
            val extractedPrices = extractPricesFromText(baseResult.extractedText, baseResult.lines)
            
            // Enhanced structured data with price information
            val enhancedStructuredData = baseResult.structuredData?.copy(
                prices = extractedPrices
            ) ?: OcrStructuredData(prices = extractedPrices)
            
            val enhancedResult = baseResult.copy(
                structuredData = enhancedStructuredData,
                metadata = baseResult.metadata + mapOf(
                    "prices_found" to extractedPrices.size.toString(),
                    "price_extraction" to "enhanced"
                )
            )
            
            // Log price extraction results
            logUsageUseCase(
                LogUsageUseCase.Request(
                    userId = request.userId,
                    action = UsageLogAction.OCR_PRICE_EXTRACTION,
                    screenshotId = request.screenshotJobId,
                    metadata = mapOf(
                        "analysis_type" to analysisType.name,
                        "prices_extracted" to extractedPrices.size.toString(),
                        "currencies_found" to extractedPrices.map { it.currency }.distinct().joinToString(","),
                        "max_price" to (extractedPrices.maxByOrNull { it.numericValue ?: 0.0 }?.value ?: "N/A"),
                        "min_price" to (extractedPrices.minByOrNull { it.numericValue ?: Double.MAX_VALUE }?.value ?: "N/A"),
                        "ocrRequestId" to request.id
                    )
                )
            )
            
            logger.info("Price extraction completed: found ${extractedPrices.size} prices for user ${request.userId} using ${analysisType.name}")
            return enhancedResult
            
        } catch (e: Exception) {
            logger.error("Price extraction enhancement failed for user ${request.userId}", e)
            // Return base result even if price enhancement fails
            return baseResult
        }
    }
    
    private fun extractPricesFromText(text: String, lines: List<OcrTextLine>): List<OcrPrice> {
        val prices = mutableListOf<OcrPrice>()
        
        // Extract prices from full text
        pricePatterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                val priceInfo = parsePriceMatch(match)
                if (priceInfo != null) {
                    // Try to find the line containing this price for bounding box
                    val containingLine = lines.find { line ->
                        line.text.contains(match.value, ignoreCase = true)
                    }
                    
                    prices.add(OcrPrice(
                        value = priceInfo.first,
                        numericValue = priceInfo.second,
                        currency = priceInfo.third,
                        confidence = containingLine?.confidence ?: 0.8,
                        boundingBox = containingLine?.boundingBox ?: OcrBoundingBox(0, 0, 0, 0, 0, 0)
                    ))
                }
            }
        }
        
        return prices.distinctBy { it.value } // Remove duplicates
    }
    
    private fun parsePriceMatch(match: MatchResult): Triple<String, Double?, String?>? {
        val fullMatch = match.value
        
        try {
            // Extract currency symbol or code
            val currency = currencySymbols.entries.find { fullMatch.contains(it.key) }?.value
                ?: Regex("""(USD|EUR|GBP|JPY|INR|RUB|CAD|AUD|CHF|CNY)""").find(fullMatch)?.value
            
            // Extract numeric value
            val numericText = Regex("""([0-9]{1,3}(?:[,.]?[0-9]{3})*(?:\.[0-9]{2})?)""").find(fullMatch)?.value
            val numericValue = numericText?.replace(",", "")?.toDoubleOrNull()
            
            return if (numericValue != null && numericValue > 0) {
                Triple(fullMatch.trim(), numericValue, currency)
            } else null
            
        } catch (e: Exception) {
            logger.debug("Failed to parse price match: $fullMatch", e)
            return null
        }
    }
}