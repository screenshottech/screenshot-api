package dev.screenshotapi.core.domain.entities

data class RateLimitInfo(
    val requestsPerMinute: Int,
    val requestsPerHour: Int,
    val concurrentRequests: Int,
    val requestsPerDay: Int = 0
) {
    companion object {
        fun fromPlan(plan: Plan): RateLimitInfo {
            val creditsPerMonth = plan.creditsPerMonth
            val planId = plan.id

            val (requestsPerMinute, requestsPerHour, concurrentRequests, requestsPerDay) = when {
                planId.contains("free") -> arrayOf(10, 300, 5, 500)           // Free: 10/min, 300/hour, 5 concurrent, 500/day - allows full 300 credit usage
                planId.contains("starter") -> arrayOf(15, 400, 8, 1000)        // Starter: 15/min, 400/hour, 8 concurrent, 1000/day
                planId.contains("professional") -> arrayOf(25, 1500, 20, 8000)    // Professional: 25/min, 1500/hour, 20 concurrent, 8000/day
                planId.contains("enterprise") -> arrayOf(100, 6000, Int.MAX_VALUE, 25000)           // Enterprise: 100/min, 6000/hour, unlimited concurrent, 25000/day
                else -> arrayOf(10, 300, 5, 500) // Default to free plan limits
            }

            return RateLimitInfo(
                requestsPerMinute = requestsPerMinute,
                requestsPerHour = requestsPerHour,
                concurrentRequests = concurrentRequests,
                requestsPerDay = requestsPerDay
            )
        }
    }
}
