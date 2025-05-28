package dev.screenshotapi.infrastructure.exceptions

class ConfigurationException(
    val configKey: String,
    message: String = "Invalid configuration for key: $configKey",
    cause: Throwable? = null
) : Exception(message, cause) {

    companion object {
        fun missingRequired(key: String): ConfigurationException {
            return ConfigurationException(
                configKey = key,
                message = "Required configuration '$key' is missing"
            )
        }

        fun invalidValue(key: String, value: String, expected: String): ConfigurationException {
            return ConfigurationException(
                configKey = key,
                message = "Invalid value '$value' for configuration '$key'. Expected: $expected"
            )
        }

        fun invalidFormat(key: String, value: String, format: String): ConfigurationException {
            return ConfigurationException(
                configKey = key,
                message = "Invalid format for configuration '$key': '$value'. Expected format: $format"
            )
        }
    }
}
