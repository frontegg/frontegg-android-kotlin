package com.frontegg.android.utils

/**
 * Result from a tenant resolver containing the tenant/organization identifier.
 *
 * @property tenant The tenant alias or organization identifier that should be passed
 *                  to the authorization URL. This corresponds to the account alias
 *                  configured in the Frontegg portal for custom login per tenant.
 */
data class TenantResolverResult(
    val tenant: String?
)

/**
 * Functional interface for resolving the tenant/organization before login.
 *
 * This is used to support "Login per Account" (custom login per tenant) functionality,
 * which allows each tenant to have a customized login experience with different branding,
 * social logins, and login methods.
 *
 * ## Usage
 *
 * The tenant can be resolved from various sources:
 * - Query parameters (e.g., `?organization=tenant-alias`)
 * - Deep link parameters
 * - App configuration
 * - User selection
 *
 * ## Example
 *
 * ```kotlin
 * // Static tenant resolver
 * val tenantResolver = TenantResolver { TenantResolverResult("my-tenant-alias") }
 *
 * // Dynamic tenant resolver from intent data
 * val tenantResolver = TenantResolver {
 *     val organization = intent.data?.getQueryParameter("organization")
 *     TenantResolverResult(organization)
 * }
 * ```
 *
 * ## Important Notes
 *
 * - When custom login per tenant is enabled for an account, `switchTenant` is not supported
 *   between accounts that have custom login boxes enabled.
 * - The tenant alias must match exactly the alias configured in the Frontegg portal.
 * - Users assigned to multiple accounts with custom login enabled will need to re-login
 *   when switching between them.
 *
 * @see FronteggApp.init for initialization with tenant resolver
 * @see FronteggAuth.login for login with organization parameter
 */
fun interface TenantResolver {
    /**
     * Resolves the tenant/organization identifier for the login request.
     *
     * @return A [TenantResolverResult] containing the tenant alias, or null if no
     *         specific tenant should be used (default login experience).
     */
    fun resolve(): TenantResolverResult
}
