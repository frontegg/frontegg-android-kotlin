package com.frontegg.android.models

class UserRolePermission {
    lateinit var id: String
    lateinit var key: String
    lateinit var name: String
    var description: String? = null
    lateinit var categoryId: String
    var fePermission: Boolean = false
    lateinit var createdAt: String
    lateinit var updatedAt: String

    // Gson leaves omitted lateinit fields unset, so equality must not read them.
    private fun comparedFields(): List<Any?> = listOf(
        if (this::id.isInitialized) id else null,
        if (this::key.isInitialized) key else null,
        if (this::name.isInitialized) name else null,
        description,
        if (this::categoryId.isInitialized) categoryId else null,
        fePermission,
        if (this::createdAt.isInitialized) createdAt else null,
        if (this::updatedAt.isInitialized) updatedAt else null,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return comparedFields() == (other as UserRolePermission).comparedFields()
    }

    override fun hashCode(): Int = comparedFields().hashCode()
}