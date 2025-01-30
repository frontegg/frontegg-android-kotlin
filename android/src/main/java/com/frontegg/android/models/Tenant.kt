package com.frontegg.android.models

class Tenant {
    lateinit var id: String
    lateinit var name: String
    var creatorEmail: String? = null
    var creatorName: String? = null
    lateinit var tenantId: String
    lateinit var createdAt: String
    lateinit var updatedAt: String
    var isReseller: Boolean = false
    lateinit var metadata: String
    lateinit var vendorId: String
    var website: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Tenant

        if (id != other.id) return false
        if (name != other.name) return false
        if (creatorEmail != other.creatorEmail) return false
        if (creatorName != other.creatorName) return false
        if (tenantId != other.tenantId) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (isReseller != other.isReseller) return false
        if (metadata != other.metadata) return false
        if (vendorId != other.vendorId) return false
        if (website != other.website) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (creatorEmail?.hashCode() ?: 0)
        result = 31 * result + (creatorName?.hashCode() ?: 0)
        result = 31 * result + tenantId.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + isReseller.hashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + vendorId.hashCode()
        result = 31 * result + (website?.hashCode() ?: 0)
        return result
    }
}
