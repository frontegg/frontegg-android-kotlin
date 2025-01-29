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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserRolePermission

        if (id != other.id) return false
        if (key != other.key) return false
        if (name != other.name) return false
        if (description != other.description) return false
        if (categoryId != other.categoryId) return false
        if (fePermission != other.fePermission) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + categoryId.hashCode()
        result = 31 * result + fePermission.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}