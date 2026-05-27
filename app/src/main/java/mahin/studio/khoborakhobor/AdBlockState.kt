package mahin.studio.khoborakhobor

enum class UBlockStatus {
    LOADING,
    ACTIVE,
    FAILED,
    DISABLED,
    FALLBACK_ACTIVE
}

data class AdBlockState(
    val enabled: Boolean,
    val status: UBlockStatus,
    val fallbackActive: Boolean = false,
    val error: String? = null
)
