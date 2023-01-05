package com.frontegg.android.models

class UserRolePermission {
    public lateinit var id: String
    public lateinit var key: String
    public lateinit var name: String
    public var description: String? = null
    public lateinit var categoryId: String
    public var fePermission: Boolean = false
    public lateinit var createdAt: String
    public lateinit var updatedAt: String
}