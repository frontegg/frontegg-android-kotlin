package com.frontegg.android.compose

import com.frontegg.android.models.Tenant
import com.frontegg.android.models.User

// Gson leaves a lateinit field unset when the response omits it, so every read goes through these.
private inline fun <T> readOrNull(read: () -> T): T? =
    try {
        read()
    } catch (e: UninitializedPropertyAccessException) {
        null
    }

internal fun Tenant.tenantIdOrNull(): String? = readOrNull { tenantId }

internal fun Tenant.nameOrNull(): String? = readOrNull { name }?.takeIf { it.isNotBlank() }

internal fun User.nameOrNull(): String? = readOrNull { name }?.takeIf { it.isNotBlank() }

internal fun User.emailOrNull(): String? = readOrNull { email }?.takeIf { it.isNotBlank() }

internal fun User.activeTenantIdOrNull(): String? = readOrNull { activeTenant }?.tenantIdOrNull()

internal fun User.tenantsOrEmpty(): List<Tenant> =
    readOrNull { tenants }.orEmpty().filter { it.tenantIdOrNull() != null }
