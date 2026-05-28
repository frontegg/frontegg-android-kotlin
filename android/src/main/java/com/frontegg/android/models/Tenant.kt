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

    /**
     * Safe-access helpers around the `lateinit var` fields. Gson silently leaves
     * a `lateinit var` uninitialized when the corresponding JSON field is absent
     * from the response, and any subsequent direct read on the property throws
     * [UninitializedPropertyAccessException].
     *
     * That bites particularly hard in `/me`-style responses scoped by
     * `applicationId` where the server can omit fields like `metadata`,
     * `createdAt`, `updatedAt`, `vendorId`, etc. — and previously `equals()`
     * crashed the host app's `==` comparison the moment any one of those
     * fields was missing on either side. Pavel hit exactly that on the
     * embedded demo (`TenantAdapter.getView:39` — `if (tenant == activeTenant)`)
     * which blocked QA of the entitlements work in PR #257.
     *
     * Direct reads on `tenant.id` etc. still throw if the field was missing —
     * keeping that behavior so host code that explicitly relies on the field
     * being present surfaces the bug rather than silently seeing `""`. Only
     * `equals` and `hashCode` are made defensive, because those are calls the
     * JDK / Kotlin standard library invoke without the host app knowing
     * (collection lookups, set membership, `==`, etc.).
     */
    private fun safeId(): String? = if (this::id.isInitialized) id else null
    private fun safeName(): String? = if (this::name.isInitialized) name else null
    private fun safeTenantId(): String? = if (this::tenantId.isInitialized) tenantId else null
    private fun safeCreatedAt(): String? = if (this::createdAt.isInitialized) createdAt else null
    private fun safeUpdatedAt(): String? = if (this::updatedAt.isInitialized) updatedAt else null
    private fun safeMetadata(): String? = if (this::metadata.isInitialized) metadata else null
    private fun safeVendorId(): String? = if (this::vendorId.isInitialized) vendorId else null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Tenant

        if (safeId() != other.safeId()) return false
        if (safeName() != other.safeName()) return false
        if (creatorEmail != other.creatorEmail) return false
        if (creatorName != other.creatorName) return false
        if (safeTenantId() != other.safeTenantId()) return false
        if (safeCreatedAt() != other.safeCreatedAt()) return false
        if (safeUpdatedAt() != other.safeUpdatedAt()) return false
        if (isReseller != other.isReseller) return false
        if (safeMetadata() != other.safeMetadata()) return false
        if (safeVendorId() != other.safeVendorId()) return false
        if (website != other.website) return false

        return true
    }

    override fun hashCode(): Int {
        var result = safeId()?.hashCode() ?: 0
        result = 31 * result + (safeName()?.hashCode() ?: 0)
        result = 31 * result + (creatorEmail?.hashCode() ?: 0)
        result = 31 * result + (creatorName?.hashCode() ?: 0)
        result = 31 * result + (safeTenantId()?.hashCode() ?: 0)
        result = 31 * result + (safeCreatedAt()?.hashCode() ?: 0)
        result = 31 * result + (safeUpdatedAt()?.hashCode() ?: 0)
        result = 31 * result + isReseller.hashCode()
        result = 31 * result + (safeMetadata()?.hashCode() ?: 0)
        result = 31 * result + (safeVendorId()?.hashCode() ?: 0)
        result = 31 * result + (website?.hashCode() ?: 0)
        return result
    }
}
