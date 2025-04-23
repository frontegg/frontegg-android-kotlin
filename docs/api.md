## APIs

### FronteggApp methods

| Method | Description |
|--------|-------------|
| `init(domain, clientId, context, applicationId, useChromeCustomTabs)` | Initialize Frontegg with the specified configuration |
| `initWithRegions(regions, context)` | Initialize Frontegg with multiple regions |
| `initWithRegion(regionKey)` | Switch to a specific region |
| `getInstance()` | Get the Frontegg instance |


### FronteggAuth methods

The FronteggAuth class provides several methods for authentication and user management:

| Method | Description | Parameters | Return Type |
|--------|-------------|------------|-------------|
| `login(activity: Activity)` | Triggers the login flow | `activity`: Current Activity | `void` |
| `logout()` | Logs out the current user | None | `void` |
| `switchTenant(tenantId: String)` | Switches the current user's active tenant | `tenantId`: Target tenant ID | `void` |
| `registerPasskeys(activity: Activity, callback: (Exception?) -> Unit)` | Registers a new passkey | `activity`: Current Activity, `callback`: Error handler | `void` |
| `loginWithPasskeys(activity: Activity, callback: (Exception?) -> Unit)` | Logs in with a passkey | `activity`: Current Activity, `callback`: Error handler | `void` |
| `stepUp(activity: Activity, maxAge: Duration, callback: (Exception?) -> Unit)` | Triggers step-up authentication | `activity`: Current Activity, `maxAge`: Duration, `callback`: Error handler | `void` |
| `isSteppedUp(maxAge: Duration): Boolean` | Checks step-up authentication status | `maxAge`: Duration | `Boolean` |
| `user: StateFlow<User?>` | User state observable | None | `StateFlow<User?>` |
