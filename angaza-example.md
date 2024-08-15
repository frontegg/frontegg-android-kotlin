```kotlin
private val onLoadingChanged: Consumer<NullableObject<Boolean>> = Consumer { isLoading ->
        println("### isLoading = ${isLoading.value}")
        if (isLoading.value) {
            // Runs on the Main thread since the initial emission was done from the main thread when we call ".subscribe()".
            showLoadingWhenAuthenticating()
        }
        else {
            // Switches to a non-Main thread due to the fallowing emissions being on a thread.
            runOnUiThread { hideLoadingWhenAuthenticated() }
            //hideLoadingWhenAuthenticated()

//            // TODO: put FronteggAuth.instance.isAuthenticated.subscriptionObject
//            onIsAuthenticatedChange.accept(NullableObject(false))
        }
    }
```


```kotlin
private val onIsAuthenticatedChange: Consumer<NullableObject<Boolean>> = Consumer {
    println("### Auth TOken (new) --> ${FronteggAuth.instance.credentialManager.get(CredentialKeys.ACCESS_TOKEN)}")

    // dont use it.
    // Question: Is it better to get tokens from the "FronteggAuth.instance" or from CredentialManager?
    // Question: Is it ok to get the User here or need to listen separately?
    val frontegg = FronteggAuth.instance
    val isAuthenticated = frontegg.isAuthenticated.value

    val accessToken = frontegg.accessToken.value
    val userName = frontegg.user.value?.name
    activeSession = persistence.session

    println("### Active Session --> $activeSession")
    println("### isAuthenticated --> $isAuthenticated")

    if (isAuthenticated) {
        updateAuthToken(userName, accessToken)
    }

when(activeSession) {
    null -> {
        if (isAuthenticated) {
            // Send a message to the Login Screen that we got authenticated
            // The Login Fragment will deal with the rest of the non-FrontEgg login logic.
            loginOnAuthenticated(userName, accessToken)
        } else {
            navigator.showRootScreen(fragmentMaker.login())
        }
    }
    
    else -> {
        // After we return to the app and there was a known session decide what to show based on "isAuthenticated".
        // - Home
        // - Login
        if (isAuthenticated) {
            println("### AUTHENTICATED")
            val deepLinker = DeepLinker(eventProcessor).onCreate(intent, navigator, fragmentMaker)
            println("### Deep Linker = $deepLinker")
            println("### isLoggedIn = $loggedIn")

            if (!loggedIn) {
                loggedIn = true
                if (isDebug(activeSession?.token)) {
                    navigator.showRootScreen(fragmentMaker.development())
                } else if (!deepLinker) {
                    println("### Home")
                    navigator.showRootScreen(fragmentMaker.home())
                }
            }
        } else {

            navigator.showRootScreen(fragmentMaker.login())
        }
    }
}
```


```kotlin

override fun onCreate(savedInstanceState: Bundle?) {
…
//        disposables?.add(
//            FronteggAuth.instance.isLoading.observable
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(onLoadingChanged)
//        )
        FronteggAuth.instance.isLoading.subscribe(onLoadingChanged)
        FronteggAuth.instance.isAuthenticated.subscribe(onIsAuthenticatedChange)

}
```


