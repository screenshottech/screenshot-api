package dev.screenshotapi.core.domain.entities

data class RateLimitInfo(
    val requestsPerMinute: Int,
    val requestsPerHour: Int,
    val concurrentRequests: Int
) {
    companion object {
        fun fromPlan(plan: Plan): RateLimitInfo {
            val creditsPerMonth = plan.creditsPerMonth
            
            // User-friendly rate limits based on competitive analysis
            val (requestsPerHour, requestsPerMinute, concurrentRequests) = when {
                creditsPerMonth <= 300 -> Triple(10, 1, 2)        // Free: 10/hour, 1/minute, 2 concurrent
                creditsPerMonth <= 2000 -> Triple(60, 1, 5)       // Starter: 60/hour, 1/minute, 5 concurrent  
                creditsPerMonth <= 10000 -> Triple(300, 5, 10)    // Professional: 300/hour, 5/minute, 10 concurrent
                else -> Triple(1500, 25, Int.MAX_VALUE)           // Enterprise: 1500/hour, 25/minute, unlimited concurrent
            }

            return RateLimitInfo(
                requestsPerHour = requestsPerHour,
                requestsPerMinute = requestsPerMinute,
                concurrentRequests = concurrentRequests
            )
        }
    }
}
