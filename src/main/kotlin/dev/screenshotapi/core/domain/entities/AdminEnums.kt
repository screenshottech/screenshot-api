package dev.screenshotapi.core.domain.entities

enum class UserStatus {
    ACTIVE, SUSPENDED, INACTIVE, PENDING_VERIFICATION
}

enum class StatsPeriod {
    DAY, WEEK, MONTH, QUARTER, YEAR
}

enum class StatsBreakdown {
    HOURLY, DAILY, WEEKLY, MONTHLY
}

enum class StatsGroupBy {
    HOUR, DAY, WEEK, MONTH, STATUS, FORMAT
}

enum class UserActivityType {
    SCREENSHOT_CREATED,
    SCREENSHOT_COMPLETED,
    SCREENSHOT_FAILED,
    API_KEY_CREATED,
    API_KEY_DELETED,
    LOGIN,
    LOGOUT,
    PROFILE_UPDATED,
    PLAN_CHANGED,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED
}
