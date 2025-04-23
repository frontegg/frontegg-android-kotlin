## FronteggApp methods

| Method | Description |
|--------|-------------|
| `init(domain, clientId, context, applicationId, useChromeCustomTabs)` | Initialize Frontegg with the specified configuration |
| `initWithRegions(regions, context)` | Initialize Frontegg with multiple regions |
| `initWithRegion(regionKey)` | Switch to a specific region |
| `getInstance()` | Get the Frontegg instance |


## FronteggAuth methods

The FronteggAuth class provides several methods for authentication and user management:

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `accessToken` | `ReadOnlyObservableValue<String?>` | Current access token. Null if user is unauthorized |
| `refreshToken` | `ReadOnlyObservableValue<String?>` | Current refresh token. Null if user is unauthorized |
| `user` | `ReadOnlyObservableValue<User?>` | Current user data. Contains all information about the user |
| `isAuthenticated` | `ReadOnlyObservableValue<Boolean>` | True if user is authenticated |
| `isLoading` | `ReadOnlyObservableValue<Boolean>` | True if some process is running |
| `initializing` | `ReadOnlyObservableValue<Boolean>` | True if Frontegg SDK is initializing |
| `showLoader` | `ReadOnlyObservableValue<Boolean>` | True if need to show loading UI |
| `refreshingToken` | `ReadOnlyObservableValue<Boolean>` | True if refreshing token is in progress |
| `isStepUpAuthorization` | `ReadOnlyObservableValue<Boolean>` | True if step-up authorization is active |
| `baseUrl` | `String` | The Frontegg base URL |
| `clientId` | `String` | The Frontegg Client ID |
| `applicationId` | `String?` | The ID of Frontegg application |
| `isMultiRegion` | `Boolean` | Flag indicating if Frontegg SDK is multi-region |
| `regions` | `List<RegionConfig>` | List of initialized regions |
| `selectedRegion` | `RegionConfig?` | Current selected region |
| `isEmbeddedMode` | `Boolean` | Flag indicating if Frontegg SDK is in embedded mode |
| `useAssetsLinks` | `Boolean` | Flag for using asset links |
| `useChromeCustomTabs` | `Boolean` | Flag for using Chrome Custom Tabs |
| `mainActivityClass` | `Class<*>?` | Main activity class reference |

#### Methods

| Method | Description | Parameters | Return Type |
|--------|-------------|------------|-------------|
| `login(activity: Activity, loginHint: String?, callback: ((Exception?) -> Unit)?)` | Triggers the login flow | `activity`: Current Activity, `loginHint`: Optional login hint, `callback`: Optional error handler | `void` |
| `logout(callback: () -> Unit)` | Logs out the current user | `callback`: Optional completion handler | `void` |
| `directLoginAction(activity: Activity, type: String, data: String, callback: ((Exception?) -> Unit)?)` | Performs direct login action. **Note:** Supported only in Embedded Mode (when EmbeddedAuthActivity is enabled).<br><br>**Type parameter values:**<br>- "direct": data can be any URL string<br>- "social-login": data must be one of: google, linkedin, facebook, github, apple, etc.<br>- "custom-social-login": data must be configured UUID | `activity`: Current Activity<br>`type`: Login type ("direct", "social-login", or "custom-social-login")<br>`data`: Login data (URL/provider/UUID)<br>`callback`: Optional error handler | `void` |
| `switchTenant(tenantId: String, callback: (Boolean) -> Unit)` | Switches the current user's active tenant | `tenantId`: Target tenant ID, `callback`: Success handler | `void` |
| `refreshTokenIfNeeded()` | Refresh token if needed | None | `Boolean` |
| `loginWithPasskeys(activity: Activity, callback: ((Exception?) -> Unit)?)` | Logs in with a passkey | `activity`: Current Activity, `callback`: Error handler | `void` |
| `registerPasskeys(activity: Activity, callback: ((Exception?) -> Unit)?)` | Registers a new passkey | `activity`: Current Activity, `callback`: Error handler | `void` |
| `requestAuthorizeAsync(refreshToken: String, deviceTokenCookie: String?)` | Requests silent authorization (suspend) | `refreshToken`: Refresh token, `deviceTokenCookie`: Optional device token | `User` |
| `requestAuthorize(refreshToken: String, deviceTokenCookie: String?, callback: (Result<User>) -> Unit)` | Requests authorization | `refreshToken`: Token, `deviceTokenCookie`: Device token, `callback`: Result handler | `void` |
| `isSteppedUp(maxAge: Duration?)` | Checks step-up authentication status | `maxAge`: Optional max duration | `Boolean` |
| `stepUp(activity: Activity, maxAge: Duration?, callback: ((Exception?) -> Unit)?)` | Triggers step-up authentication | `activity`: Activity, `maxAge`: Optional duration, `callback`: Error handler | `void` |
