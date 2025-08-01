package dev.screenshotapi.core.domain.entities

/**
 * Enum representing different types of AI-powered analysis that can be performed on images.
 * These types determine the complexity, cost, and prompts used for the analysis operation.
 * 
 * Credit System:
 * - BASIC_OCR: 2 credits (traditional OCR, can fallback to PaddleOCR)
 * - AI Analysis: 3 credits (requires Claude 3 Haiku or similar vision model)
 */
enum class AnalysisType(
    val displayName: String,
    val description: String,
    val credits: Int,
    val systemPrompt: String,
    val userPrompt: String,
    val requiresAI: Boolean = true
) {
    /**
     * Basic OCR - Simple text extraction from images
     * Can use traditional OCR engines or AI models
     */
    BASIC_OCR(
        displayName = "Basic OCR",
        description = "Simple text extraction from images with high accuracy",
        credits = 2,
        systemPrompt = "You are an expert OCR assistant. Extract all visible text from images with perfect accuracy.",
        userPrompt = "Please extract ALL text from this image. Maintain the original structure, line breaks, and formatting. Return only the extracted text without any additional commentary.",
        requiresAI = false // Can fallback to PaddleOCR
    ),
    
    /**
     * UX Analysis - User experience and interface analysis
     * Requires AI vision model for complex analysis
     */
    UX_ANALYSIS(
        displayName = "UX Analysis",
        description = "Comprehensive user experience and interface analysis",
        credits = 3,
        systemPrompt = "You are a senior UX/UI expert with 10+ years of experience. Analyze interfaces for usability, accessibility, and design quality.",
        userPrompt = """Analyze this interface for UX quality. Provide structured analysis covering:

1. **Visual Hierarchy**: Layout effectiveness, information prioritization
2. **Accessibility**: Color contrast, text readability, navigation clarity
3. **User Flow**: Intuitive navigation, clear call-to-actions
4. **Design Consistency**: UI patterns, spacing, typography
5. **Mobile Responsiveness**: Adaptation for different screen sizes
6. **Improvement Recommendations**: Specific, actionable suggestions

Format your response as structured sections with clear headings."""
    ),
    
    /**
     * Content Summary - Comprehensive content analysis and summarization
     * Requires AI for understanding context and meaning
     */
    CONTENT_SUMMARY(
        displayName = "Content Summary",
        description = "Intelligent content analysis and summarization",
        credits = 3,
        systemPrompt = "You are a content analyst expert. Analyze and summarize content with focus on key insights and actionable information.",
        userPrompt = """Analyze this content and provide a comprehensive summary including:

1. **Main Topics**: Primary themes and subjects covered
2. **Key Information**: Most important facts, data, or insights
3. **Content Classification**: Type of content (article, product page, dashboard, etc.)
4. **Target Audience**: Intended users or demographic
5. **Action Items**: Any tasks, deadlines, or next steps mentioned
6. **Summary**: Concise 2-3 sentence overview

Structure your response clearly with the above sections."""
    ),
    
    /**
     * General Analysis - Open-ended AI analysis with custom prompts
     * Most flexible option for various use cases
     */
    GENERAL(
        displayName = "General Analysis",
        description = "Open-ended AI analysis for custom requirements",
        credits = 3,
        systemPrompt = "You are an AI assistant capable of analyzing any type of image or interface. Provide thorough, accurate, and helpful analysis.",
        userPrompt = """Analyze this image thoroughly and describe:

1. **Visual Elements**: What you see in the image (UI components, text, graphics)
2. **Content Analysis**: Any text content, its purpose and context
3. **Technical Details**: Interface elements, layout patterns, design choices
4. **Functionality Assessment**: What this interface/content is designed to do
5. **Quality Evaluation**: Overall quality, professionalism, effectiveness
6. **Insights**: Any notable observations or recommendations

Provide detailed, actionable insights in a well-structured format."""
    ),
    
    /**
     * Custom Analysis - User-provided custom prompts for specialized analysis
     * Premium feature with enhanced security and validation
     */
    CUSTOM(
        displayName = "Custom Analysis",
        description = "AI analysis with user-provided custom prompts for specialized requirements",
        credits = 4,
        systemPrompt = "", // Will be provided by user or default
        userPrompt = "", // Will be provided by user
        requiresAI = true
    );
    
    companion object {
        /**
         * Get analysis type by name, case-insensitive
         */
        fun fromString(value: String): AnalysisType? {
            return values().find { it.name.equals(value, ignoreCase = true) }
        }
        
        /**
         * Get all available analysis types
         */
        fun getAllTypes(): List<AnalysisType> {
            return values().toList()
        }
        
        /**
         * Get AI-only analysis types (excludes BASIC_OCR)
         */
        fun getAITypes(): List<AnalysisType> {
            return values().filter { it.requiresAI }
        }
        
        /**
         * Get credit cost for an analysis type
         */
        fun getCreditCost(analysisType: AnalysisType): Int {
            return analysisType.credits
        }
        
        /**
         * Check if analysis type requires AI processing
         */
        fun requiresAI(analysisType: AnalysisType): Boolean {
            return analysisType.requiresAI
        }
        
        /**
         * Get appropriate credit deduction reason for analysis type
         */
        fun getDeductionReason(analysisType: AnalysisType): CreditDeductionReason {
            return when (analysisType) {
                BASIC_OCR -> CreditDeductionReason.OCR
                UX_ANALYSIS, CONTENT_SUMMARY, GENERAL, CUSTOM -> CreditDeductionReason.AI_ANALYSIS
            }
        }
    }
}