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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserRole

        if (id != other.id) return false
        if (key != other.key) return false
        if (isDefault != other.isDefault) return false
        if (name != other.name) return false
        if (description != other.description) return false
        if (permissions != other.permissions) return false
        if (tenantId != other.tenantId) return false
        if (vendorId != other.vendorId) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + isDefault.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + permissions.hashCode()
        result = 31 * result + (tenantId?.hashCode() ?: 0)
        result = 31 * result + vendorId.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
