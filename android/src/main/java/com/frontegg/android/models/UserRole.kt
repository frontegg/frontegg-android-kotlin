package com.frontegg.android.models

class UserRole {
    lateinit var id: String
    lateinit var key: String
    var isDefault: Boolean = false
    lateinit var name: String
    var description: String? = null
    lateinit var permissions: List<String>
    var tenantId: String? = null
    lateinit var vendorId: String
    lateinit var createdAt: String
    lateinit var updatedAt: String

    // Gson leaves omitted lateinit fields unset, so equality must not read them.
    private fun comparedFields(): List<Any?> = listOf(
        if (this::id.isInitialized) id else null,
        if (this::key.isInitialized) key else null,
        isDefault,
        if (this::name.isInitialized) name else null,
        description,
        if (this::permissions.isInitialized) permissions else null,
        tenantId,
        if (this::vendorId.isInitialized) vendorId else null,
        if (this::createdAt.isInitialized) createdAt else null,
        if (this::updatedAt.isInitialized) updatedAt else null,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return comparedFields() == (other as UserRole).comparedFields()
    }

    override fun hashCode(): Int = comparedFields().hashCode()
}
