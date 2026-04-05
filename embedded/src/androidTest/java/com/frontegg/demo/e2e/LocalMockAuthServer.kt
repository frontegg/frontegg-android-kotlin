package com.frontegg.demo.e2e

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** OkHttp MockWebServer mirror of Swift `LocalMockAuthServer` for embedded E2E. */
class LocalMockAuthServer {
    val clientId = "demo-embedded-e2e-client"
    val server = MockWebServer()
    private val gson = Gson()
    private val state = MockAuthState()
    private val requestLogLock = ReentrantLock()
    private val requestLog = mutableListOf<Triple<String, String, String>>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handle(request)
        }
    }

    fun start() = server.start()
    fun shutdown() = server.shutdown()
    fun urlRoot(): String = server.url("/").toString().trimEnd('/')

    private fun mockAuthority(): String {
        val url = server.url("/")
        return if (url.port == 80 || url.port == 443) url.host else "${url.host}:${url.port}"
    }
    fun reset() {
        state.reset()
        requestLogLock.withLock { requestLog.clear() }
    }

    fun clearRequestLog() = requestLogLock.withLock { requestLog.clear() }
    fun configureTokenPolicy(email: String, accessTTL: Int, refreshTTL: Int, startVer: Int = 1) =
        state.configureTokenPolicy(email, accessTTL, refreshTTL, startVer)

    fun enqueue(method: String = "GET", path: String, responses: List<Map<String, Any?>>) =
        state.enqueue(method, path, responses)

    fun queueProbeFailures(codes: List<Int>) =
        enqueue("HEAD", "/test", codes.map { mapOf("status" to it, "body" to "offline") })

    fun queueProbeTimeouts(count: Int, delayMs: Int = 1500) =
        enqueue("HEAD", "/test", List(count) { mapOf("status" to 200, "body" to "ok", "delay_ms" to delayMs) })

    fun queueConnectionDrops(method: String = "POST", path: String, count: Int = 1) =
        enqueue(method, path, List(count) { mapOf("close_connection" to true) })

    fun queueEmbeddedSocialSuccessOAuthError(errorCode: String, errorDescription: String) =
        state.queueEmbeddedSocialSuccessOAuthError(errorCode, errorDescription)

    fun configurePasswordPolicy(minLength: Int, requireUppercase: Boolean = false, requireNumber: Boolean = false, requireSpecial: Boolean = false) = state.configurePasswordPolicy(minLength, requireUppercase, requireNumber, requireSpecial)
    fun configureLockout(maxAttempts: Int) = state.configureLockout(maxAttempts)
    fun configureMfa(email: String, type: String) = state.configureMfa(email, type)
    fun configureAuthStrategies(password: Boolean = true, magicCode: Boolean = false, magicLink: Boolean = false, socialLogin: Boolean = true, sso: Boolean = true) = state.configureAuthStrategies(password, magicCode, magicLink, socialLogin, sso)
    fun configureSignup(requireTerms: Boolean = false, requireEmailVerification: Boolean = false) = state.configureSignup(requireTerms, requireEmailVerification)
    fun configurePasswordExpiration(email: String, daysUntilExpiry: Int) = state.configurePasswordExpiration(email, daysUntilExpiry)
    fun configureBreachedPassword(email: String) = state.configureBreachedPassword(email)
    fun configureMagicCodeTTL(seconds: Int) = state.configureMagicCodeTTL(seconds)
    fun getIssuedMagicCode(email: String): String? = state.getIssuedMagicCode(email)
    fun getIssuedMagicLinkUrl(email: String): String? = state.getIssuedMagicLinkUrl(email)?.let { "${urlRoot()}/identity/resources/auth/v1/passwordless/link/verify?token=$it" }

    fun waitForRequest(method: String? = null, path: String, timeoutMs: Long = 10_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (requestCount(method, path) > 0) return true
            Thread.sleep(100)
        }
        return requestCount(method, path) > 0
    }

    fun waitForRequestCount(method: String? = null, path: String, count: Int, timeoutMs: Long = 10_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (requestCount(method, path) >= count) return true
            Thread.sleep(100)
        }
        return requestCount(method, path) >= count
    }

    fun requestCount(method: String?, path: String): Int = requestLogLock.withLock {
        requestLog.count { it.second == path && (method == null || it.first == method) }
    }

    private fun log(m: String, p: String, t: String) {
        requestLogLock.withLock { requestLog.add(Triple(m, p, t)) }
    }

    private fun handle(request: RecordedRequest): MockResponse {
        val raw = request.path ?: "/"
        val path = normPath(raw.substringBefore('?'))
        val method = request.method ?: "GET"
        log(method, path, raw)
        state.dequeue(method, path)?.let { return fromSpec(it, method) }
        val body = try {
            request.body.clone().readUtf8()
        } catch (_: Exception) {
            ""
        }
        val cookie = request.getHeader("Cookie")
        val authz = request.getHeader("Authorization")
        return route(method, path, raw, body, cookie, authz)
    }

    private fun fromSpec(spec: Map<String, Any?>, @Suppress("UNUSED_PARAMETER") method: String): MockResponse {
        val delayMs = (spec["delay_ms"] as? Number)?.toInt() ?: 0
        if (spec["close_connection"] == true) {
            return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        }
        val b = MockResponse()
        if (delayMs > 0) b.setBodyDelay(delayMs.toLong(), TimeUnit.MILLISECONDS)
        val redirect = spec["redirect"] as? String
        if (!redirect.isNullOrEmpty()) {
            val st = (spec["status"] as? Number)?.toInt() ?: 302
            return b.setResponseCode(st).addHeader("Location", redirect)
        }
        val status = (spec["status"] as? Number)?.toInt() ?: 200
        when (val json = spec["json"]) {
            null -> {
                val txt = spec["body"] as? String ?: ""
                return b.setResponseCode(status)
                    .addHeader("Content-Type", "text/plain; charset=utf-8")
                    .setBody(txt)
            }
            else -> {
                val jo = when (json) {
                    is JSONObject -> json
                    is Map<*, *> -> JSONObject(json)
                    else -> JSONObject(gson.toJson(json))
                }
                return b.setResponseCode(status)
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .setBody(jo.toString())
            }
        }
    }

    private fun route(
        method: String,
        path: String,
        raw: String,
        body: String,
        cookie: String?,
        authorization: String?,
    ): MockResponse {
        val q = parseQuery(raw)
        return when {
            (method == "HEAD" || method == "GET") && (path == "/test" || path == "/") ->
                text(200, "ok")
            method == "GET" && path == "/oauth/authorize" -> authorizePage(q)
            method == "GET" && path == "/oauth/account/social/success" -> socialSuccess(q)
            method == "GET" && path == "/oauth/prelogin" -> hostedPrelogin(q)
            method == "POST" && path == "/oauth/postlogin" -> hostedPostlogin(body)
            method == "GET" && path == "/oauth/postlogin/redirect" -> hostedPostRedirect(q)
            method == "GET" && path == "/idp/google/authorize" -> mockGoogle(q)
            method == "GET" && path == "/embedded/continue" -> embeddedContinue(q)
            method == "POST" && path == "/embedded/password" -> embeddedPassword(body)
            method == "GET" && path == "/browser/complete" -> browserComplete(q)
            method == "POST" && path == "/oauth/token" -> oauthToken(body)
            method == "POST" && (path == "/oauth/authorize/silent" || path == "/frontegg/oauth/authorize/silent") ->
                silentAuthorize(cookie)
            (method == "GET" && path == "/flags") || (method == "GET" && path == "/frontegg/flags") ->
                json(200, JSONObject().put("mobile-enable-logging", "off"))
            method == "GET" && path == "/frontegg/metadata" ->
                json(200, JSONObject().put("appName", "e2e").put("environment", "local"))
            method == "GET" && (path == "/vendors/public" || path == "/frontegg/vendors/public") ->
                json(200, JSONObject().put("vendors", JSONArray()))
            method == "GET" && path == "/frontegg/identity/resources/sso/v2" -> socialConfigArr()
            method == "GET" && path == "/identity/resources/auth/v1/sso/config" -> androidSocialConfig()
            method == "GET" && path == "/identity/resources/auth/v1/feature-flags" -> json(200, JSONObject())
            method == "GET" && path == "/frontegg/identity/resources/configurations/v1/public" ->
                json(200, JSONObject().put("embeddedMode", true).put("loginBoxVisible", true))
            method == "GET" && path == "/frontegg/identity/resources/configurations/v1/auth/strategies/public" ->
                json(200, JSONObject()
                    .put("password", state.authStrategies.password)
                    .put("socialLogin", state.authStrategies.socialLogin)
                    .put("sso", state.authStrategies.sso)
                    .put("magicCode", state.authStrategies.magicCode)
                    .put("magicLink", state.authStrategies.magicLink))
            method == "GET" && path == "/frontegg/identity/resources/configurations/v1/sign-up/strategies" ->
                json(200, JSONObject().put("allowSignUp", true))
            method == "GET" && path == "/frontegg/team/resources/sso/v2/configurations/public" -> json(200, JSONArray())
            (method == "GET" && path == "/identity/resources/sso/custom/v1") ||
                (method == "GET" && path == "/frontegg/identity/resources/sso/custom/v1") ->
                json(200, JSONObject().put("providers", JSONArray()))
            method == "GET" && path == "/identity/resources/configurations/sessions/v1" ->
                json(
                    200,
                    JSONObject().put("cookieName", "fe_refresh_demoembedded-e2e-client").put("keepSessionAlive", true),
                )
            method == "GET" && path == "/frontegg/identity/resources/configurations/v1/captcha-policy/public" ->
                json(200, JSONObject().put("enabled", false))
            method == "POST" && path == "/frontegg/identity/resources/auth/v1/user/token/refresh" ->
                hostedRefresh(cookie)
            method == "POST" && path == "/frontegg/identity/resources/auth/v2/user/sso/prelogin" ->
                ssoPrelogin(body)
            method == "POST" && path == "/frontegg/identity/resources/auth/v1/user" -> hostedPasswordLogin(body)
            method == "GET" && path == "/identity/resources/users/v2/me" -> me(authorization)
            method == "GET" && path == "/identity/resources/users/v3/me/tenants" -> tenants(authorization)
            method == "POST" && path == "/oauth/logout/token" -> {
                cookieRefresh(cookie)?.let { state.invalidateRefreshToken(it) }
                json(200, JSONObject().put("ok", true))
            }
            method == "GET" && path == "/idp/custom-sso" -> idpPage("custom-sso@frontegg.com", "Custom SSO", "Continue Custom SSO", q)
            method == "GET" && path.startsWith("/idp/social/") ->
                idpPage("social-login@frontegg.com", "Mock Social", "Continue Mock Social", q)
            method == "GET" && path.startsWith("/oauth/account/redirect/android/") -> {
                val pkg = path.removePrefix("/oauth/account/redirect/android/")
                val code = fq(q, "code")
                val st = fq(q, "state")
                val err = state.consumeOAuthErr()
                val loc = if (err != null) {
                    "${pkg.lowercase()}://${mockAuthority()}/android/oauth/callback?error=${enc(err.first)}&error_description=${enc(err.second)}&state=${enc(st)}"
                } else {
                    "${pkg.lowercase()}://${mockAuthority()}/android/oauth/callback?code=${enc(code)}&state=${enc(st)}"
                }
                val jsLoc = loc.replace("\\", "\\\\").replace("'", "\\'")
                html(200, "Redirect", """<p>Completing login…</p>
                    <script>(function(){window.location.href='$jsLoc';})()</script>""")
            }
            method == "GET" && path == "/oauth/account/mfa-mobile-authenticator" -> mfaChallengePage(q)
            method == "POST" && path == "/identity/resources/auth/v1/user/mfa/verify" -> mfaVerifyHandler(body)
            method == "POST" && path == "/identity/resources/auth/v1/passwordless/code/prelogin" -> magicCodePrelogin(body)
            method == "POST" && path == "/identity/resources/auth/v1/passwordless/code/postlogin" -> magicCodePostlogin(body)
            method == "POST" && path == "/identity/resources/auth/v1/passwordless/code/resend" -> magicCodeResend(body)
            method == "GET" && path == "/identity/resources/auth/v1/passwordless/link/verify" -> magicLinkVerify(q)
            method == "GET" && path == "/oauth/account/sign-up" -> signupPage(q)
            method == "POST" && path == "/frontegg/identity/resources/auth/v1/user/signUp" -> signupHandler(body)
            method == "GET" && path == "/oauth/account/reset-password" -> resetPasswordPage(q)
            method == "POST" && path == "/identity/resources/auth/v1/user/passwords/reset" -> json(200, JSONObject().put("ok", true))
            method == "GET" && path == "/oauth/account/activate" -> activateAccountPage(q)
            method == "GET" && path == "/oauth/account/invitation/accept" -> acceptInvitationPage(q)
            method == "GET" && path == "/oauth/account/unlock" -> unlockAccountPage(q)
            method == "GET" && path == "/oauth/account/verify-email" -> verifyEmailPage(q)
            else -> json(404, JSONObject().put("error", "$method $path"))
        }
    }

    private fun idpPage(email: String, title: String, btn: String, q: Map<String, List<String>>): MockResponse {
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val href =
            "/browser/complete?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}"
        // Anchor links are easier for Chrome + UiAutomator than submit buttons in Custom Tabs.
        val body =
            """<h1>$title</h1><p><a id="e2e-complete" href="$href">$btn</a></p>
            <script>
            setTimeout(function(){
              var e=document.getElementById('e2e-complete');
              if(e) e.click();
            }, 900);
            </script>"""
        return html(200, title, body)
    }

    private fun me(auth: String?): MockResponse {
        val tok = bearer(auth) ?: return json(401, JSONObject().put("error", "missing_access_token"))
        val email = jwtEmail(tok) ?: return json(401, JSONObject().put("error", "missing_access_token"))
        return json(200, JSONObject(gson.toJson(userPayload(email))))
    }

    private fun tenants(auth: String?): MockResponse {
        val tok = bearer(auth) ?: return json(401, JSONObject().put("error", "missing_access_token"))
        val email = jwtEmail(tok) ?: return json(401, JSONObject().put("error", "missing_access_token"))
        val t = tenantObj(email)
        return json(
            200,
            JSONObject().put("tenants", JSONArray().put(t)).put("activeTenant", t),
        )
    }

    private fun bearer(h: String?) =
        if (h.isNullOrBlank() || !h.startsWith("Bearer ")) null else h.removePrefix("Bearer ").trim()

    private fun authorizePage(q: Map<String, List<String>>): MockResponse {
        val redirect = fq(q, "redirect_uri")
        val st = fq(q, "state")
        val cid = fq(q, "client_id")
        val loginAction = fq(q, "login_direct_action")
        val hint = fq(q, "login_hint")
        if (loginAction.isNotEmpty()) {
            val dec = decodeB64Json(loginAction)
            val dest = dec?.get("data")?.asString ?: ""
            val typ = dec?.get("type")?.asString
            // Embedded Google social (loginWithSocialLoginProvider): authorize carries social-login/google → mock IdP page.
            if (typ.equals("social-login", ignoreCase = true) && dest.equals("google", ignoreCase = true)) {
                val loc =
                    "${urlRoot()}/idp/google/authorize?redirect_uri=${enc(redirect)}&state=${enc(st)}"
                return redir(loc)
            }
            val (ti, bt, em) = when {
                dest.contains("custom-sso") ->
                    Triple("Custom SSO", "Continue Custom SSO", "custom-sso@frontegg.com")
                dest.contains("mock-social-provider") ->
                    Triple("Mock Social", "Continue Mock Social", "social-login@frontegg.com")
                else -> Triple("Direct", "Continue", "direct-login@frontegg.com")
            }
            val href =
                "/browser/complete?email=${enc(em)}&redirect_uri=${enc(redirect)}&state=${enc(st)}"
            val htmlBody = """<h1>$ti</h1><p><a id="e2e-complete" href="$href">$bt</a></p>
            <script>
            setTimeout(function(){
              var e=document.getElementById('e2e-complete');
              if(e) e.click();
            }, 900);
            </script>"""
            return html(200, ti, htmlBody)
        }
        val hs = state.issueHosted(redirect, st, hint)
        val loc = "${urlRoot()}/oauth/prelogin?" +
            "client_id=${enc(cid)}&redirect_uri=${enc(redirect)}&state=${enc(hs)}" +
            if (hint.isNotEmpty()) "&login_hint=${enc(hint)}" else ""
        return redir(loc)
    }

    private fun hostedPrelogin(q: Map<String, List<String>>): MockResponse {
        val hs = fq(q, "state")
        val ctx = state.hosted(hs) ?: return html(400, "err", "<h1>Invalid</h1>")
        var email = fq(q, "email", ctx.loginHint)
        if (email.isEmpty()) {
            val b =
                """<h1>Login</h1><form action="/oauth/prelogin" method="get">
                <input type="hidden" name="state" value="${htmlEsc(hs)}"/>
                <input type="email" name="email"/><button>Continue</button></form>"""
            return html(200, "Login", b)
        }
        email = email.trim()
        // Route to magic code/link page when those strategies are enabled
        val strategies = state.authStrategies
        if (strategies.magicCode && !email.endsWith("@saml-domain.com") && !email.endsWith("@oidc-domain.com")) {
            return magicCodePage(hs, email)
        }
        if (strategies.magicLink && !email.endsWith("@saml-domain.com") && !email.endsWith("@oidc-domain.com")) {
            return magicLinkPage(hs, email)
        }
        return when {
            email.endsWith("@saml-domain.com") ->
                providerPage("OKTA SAML Mock Server", "Login With Okta", hs, email)
            email.endsWith("@oidc-domain.com") ->
                providerPage("OKTA OIDC Mock Server", "Login With Okta", hs, email)
            else -> passwordPage(hs, email, ctx.loginHint.isNotEmpty())
        }
    }

    private fun passwordPage(hs: String, email: String, prefill: Boolean): MockResponse {
        val pv = if (prefill) """ value="Testpassword1!"""" else ""
        val b = """<h1>Password Login</h1>
    <div id="e2e-password-error" style="color:red;display:none"></div>
    <form id="f"><input id="e" type="email" value="${htmlEsc(email)}"/>
    <input id="p" type="password" name="password"$pv/>
    <button type="submit" id="e2e-password-submit">Sign in</button></form>
    <p><a id="e2e-forgot-password" href="/oauth/account/reset-password?state=${enc(hs)}">Forgot password?</a></p>
    <p><a id="e2e-signup-link" href="/oauth/account/sign-up?state=${enc(hs)}">Sign up</a></p>
    <script>document.getElementById('f').onsubmit=async ev=>{ev.preventDefault();
    var errDiv=document.getElementById('e2e-password-error');errDiv.style.display='none';
    await fetch('/frontegg/identity/resources/auth/v2/user/sso/prelogin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:document.getElementById('e').value})}).catch(()=>null);
    const r=await fetch('/frontegg/identity/resources/auth/v1/user',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({email:document.getElementById('e').value,password:document.getElementById('p').value,invitationToken:''})});
    const j=await r.json();
    if(!r.ok){errDiv.textContent=(j.errors||['Login failed']).join('. ');errDiv.style.display='block';return;}
    if(j.mfaRequired){location='/oauth/account/mfa-mobile-authenticator?state='+encodeURIComponent(j.mfaToken);return;}
    const p=await fetch('/oauth/postlogin',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({state:'${hs}',token:j.access_token})});
    if(!p.ok)return;await p.json();location='/oauth/postlogin/redirect?state=${enc(hs)}';};</script>"""
        return html(200, "pwd", b)
    }

    private fun providerPage(title: String, buttonText: String, hs: String, email: String): MockResponse {
        val pol = state.policy(email)
        val at = mintAccess(email, pol.startingTokenVersion, pol.accessTokenTTL)
        val b =
            """<h1>$title</h1><form id="f"><button type="submit" id="e2e-sso-okta">$buttonText</button></form>
            <script>document.getElementById('f').onsubmit=async ev=>{ev.preventDefault();
            await fetch('/frontegg/identity/resources/auth/v2/user/sso/prelogin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'$email'})});
            const p=await fetch('/oauth/postlogin',{method:'POST',headers:{'Content-Type':'application/json'},
            body:JSON.stringify({state:'$hs',token:'$at'})});
            if(!p.ok)return;await p.json();location='/oauth/postlogin/redirect?state=${enc(hs)}';};</script>"""
        return html(200, title, b)
    }

    private fun hostedPostlogin(body: String): MockResponse {
        val jo = JSONObject(body.ifEmpty { "{}" })
        val hs = jo.optString("state", "")
        val ctx = state.hosted(hs) ?: return json(400, JSONObject().put("error", "invalid_state"))
        val tok = jo.optString("token", "")
        val email = when {
            tok.isNotEmpty() -> jwtEmail(tok)
            ctx.loginHint.isNotEmpty() -> ctx.loginHint
            else -> null
        } ?: return json(400, JSONObject().put("error", "missing_token"))
        val code = state.issueCode(email, ctx.redirect, ctx.origState)
        state.recordDone(hs, email)
        return json(200, JSONObject().put("redirectUrl", cb(ctx.redirect, code, ctx.origState)))
    }

    private fun hostedPostRedirect(q: Map<String, List<String>>): MockResponse {
        val hs = fq(q, "state")
        val ctx = state.hosted(hs)
        val em = state.doneEmail(hs)
        if (ctx == null || em == null) return json(400, JSONObject().put("error", "missing_postlogin_completion"))
        val code = state.issueCode(em, ctx.redirect, ctx.origState)
        return redir(cb(ctx.redirect, code, ctx.origState))
    }

    private fun mockGoogle(q: Map<String, List<String>>): MockResponse {
        val ru = fq(q, "redirect_uri")
        val st = fq(q, "state")
        if (ru.isEmpty() || st.isEmpty()) return html(400, "bad", "<h1>bad</h1>")
        val code = state.issueCode("google-social@frontegg.com", ru, st)
        val err = state.consumeOAuthErr()
        // Redirect back to redirect_uri with code/error. redir() automatically uses HTML+JS
        // instead of 302 when the target is a custom-scheme URL (Chrome Custom Tab compat).
        return if (err != null) {
            redir(cbErr(ru, st, err.first, err.second))
        } else {
            redir(cb(ru, code, st))
        }
    }

    private fun socialSuccess(q: Map<String, List<String>>): MockResponse {
        val code = fq(q, "code")
        val rawState = fq(q, "state")
        if (state.peekCode(code) == null) return json(400, JSONObject().put("error", "invalid_social_code"))
        val redir = fq(q, "redirectUri")
        if (redir.isEmpty()) {
            val o = try {
                JSONObject(rawState)
            } catch (_: Exception) {
                JSONObject()
            }
            val pkg = o.optString("bundleId", "")
            if (pkg.isEmpty()) return json(400, JSONObject().put("error", "invalid_social_state"))
            val auth = mockAuthority()
            val plat = o.optString("platform", "")
            val path = if (plat.equals("android", true)) "/android/oauth/callback" else "/ios/oauth/callback"
            val loc = "${pkg.lowercase()}://$auth$path?state=${enc(rawState)}&code=${enc(code)}&social-login-callback=true"
            return redir(loc)
        }
        val err = state.consumeOAuthErr()
        if (err != null) {
            val st = state.latestHosted() ?: rawState
            val bundle = embeddedBundle(redir)
            val loc = if (bundle != null) {
                "${bundle.lowercase()}://${mockAuthority()}/android/oauth/callback?error=${enc(err.first)}&error_description=${enc(err.second)}&state=${enc(st)}"
            } else {
                cbErr(redir, st, err.first, err.second)
            }
            return redir(loc)
        }
        return redir(cb(redir, code, rawState))
    }

    private fun embeddedBundle(redirectUri: String): String? {
        val path = try {
            java.net.URI(redirectUri).path ?: ""
        } catch (_: Exception) {
            return null
        }
        val p = "/oauth/account/redirect/android/"
        if (!path.startsWith(p)) return null
        return path.removePrefix(p).split("/").firstOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun embeddedContinue(q: Map<String, List<String>>): MockResponse {
        val email = fq(q, "email", "test@frontegg.com")
        val ru = fq(q, "redirect_uri")
        val st = fq(q, "state")
        return when {
            email.endsWith("@saml-domain.com") || email.endsWith("@oidc-domain.com") -> {
                val href =
                    "/browser/complete?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}"
                html(
                    200,
                    "sso",
                    """<h1>SSO</h1><p><a id="e2e-sso-okta" href="$href">Login With Okta</a></p>""",
                )
            }
            else -> {
                val pv = ""
                html(
                    200,
                    "pwd",
                    """<h1>Password Login</h1><form action="/embedded/password" method="post">
                    <input type="hidden" name="email" value="${htmlEsc(email)}"/>
                    <input type="hidden" name="redirect_uri" value="${htmlEsc(ru)}"/>
                    <input type="hidden" name="state" value="${htmlEsc(st)}"/>
                    <input type="password" name="password"$pv/><button type="submit" id="e2e-password-submit">Sign in</button></form>""",
                )
            }
        }
    }

    private fun embeddedPassword(body: String): MockResponse {
        val f = parseForm(body)
        val email = f["email"] ?: "test@frontegg.com"
        val ru = f["redirect_uri"] ?: ""
        val st = f["state"] ?: ""
        val code = state.issueCode(email, ru, st)
        return redir(cb(ru, code, st))
    }

    private fun browserComplete(q: Map<String, List<String>>): MockResponse {
        val email = fq(q, "email", "browser@frontegg.com")
        val ru = fq(q, "redirect_uri")
        val st = fq(q, "state")
        val code = state.issueCode(email, ru, st)
        return redir(cb(ru, code, st))
    }

    private fun oauthToken(body: String): MockResponse {
        val jo = JSONObject(body.ifEmpty { "{}" })
        return when (jo.optString("grant_type", "")) {
            "authorization_code" -> {
                val c = jo.optString("code", "")
                if (c.isEmpty()) return json(400, JSONObject().put("error", "missing_code"))
                val ac = state.consumeCode(c) ?: return json(400, JSONObject().put("error", "invalid_code"))
                val iss = state.issueRefresh(ac.email)
                json(200, JSONObject(gson.toJson(tokenJson(iss.rec, iss.token))))
            }
            "refresh_token" -> {
                val rt = jo.optString("refresh_token", "")
                if (rt.isEmpty()) return json(400, JSONObject().put("error", "missing_refresh_token"))
                val ses = state.refresh(rt) ?: return json(401, JSONObject().put("error", "invalid_refresh_token"))
                json(200, JSONObject(gson.toJson(tokenJson(ses, rt))))
            }
            else -> json(400, JSONObject().put("error", "unsupported_grant_type"))
        }
    }

    private fun silentAuthorize(cookie: String?): MockResponse {
        val rt = cookieRefresh(cookie) ?: return json(401, JSONObject().put("error", "invalid_refresh_cookie"))
        val s = state.validRt(rt) ?: return json(401, JSONObject().put("error", "invalid_refresh_cookie"))
        return json(200, JSONObject(gson.toJson(tokenJson(s, rt))))
    }

    private fun hostedRefresh(cookie: String?): MockResponse {
        val rt = cookieRefresh(cookie) ?: return json(401, JSONObject().put("errors", JSONArray().put("Session not found")))
        val s = state.refresh(rt) ?: return json(401, JSONObject().put("errors", JSONArray().put("Session not found")))
        return json(200, JSONObject(gson.toJson(tokenJson(s, rt))))
    }

    private fun ssoPrelogin(body: String): MockResponse {
        val em = JSONObject(body.ifEmpty { "{}" }).optString("email", "").lowercase()
        return when {
            em.endsWith("@saml-domain.com") -> json(200, JSONObject().put("type", "saml").put("tenantId", tid(em)))
            em.endsWith("@oidc-domain.com") -> json(200, JSONObject().put("type", "oidc").put("tenantId", tid(em)))
            else -> json(404, JSONObject().put("errors", JSONArray().put("SSO domain was not found")))
        }
    }

    private fun hostedPasswordLogin(body: String): MockResponse {
        val jo = JSONObject(body.ifEmpty { "{}" })
        val em = jo.optString("email", "test@frontegg.com")
        val pw = jo.optString("password", "")

        // Check lockout
        val lockout = state.lockoutConfig
        if (lockout != null) {
            val attempts = state.incrementLockout(em)
            if (attempts > lockout.maxAttempts) {
                return json(423, JSONObject().put("errors", JSONArray().put("Your account is locked due to too many failed login attempts")))
            }
        }

        // Check breached password
        if (state.isBreachedEmail(em)) {
            return json(400, JSONObject().put("errors", JSONArray().put("This password has been found in a data breach. Please choose a different password")))
        }

        // Check password policy
        val policy = state.passwordPolicy
        if (policy != null) {
            val errors = mutableListOf<String>()
            if (pw.length < policy.minLength) errors.add("Password must be at least ${policy.minLength} characters")
            if (policy.requireUppercase && pw.none { it.isUpperCase() }) errors.add("Password must contain an uppercase letter")
            if (policy.requireNumber && pw.none { it.isDigit() }) errors.add("Password must contain a number")
            if (policy.requireSpecial && pw.none { !it.isLetterOrDigit() }) errors.add("Password must contain a special character")
            if (errors.isNotEmpty()) {
                return json(400, JSONObject().put("errors", JSONArray(errors)))
            }
        }

        // Check password expiration
        val expDays = state.getPasswordExpiration(em)
        if (expDays != null && expDays <= 0) {
            return json(400, JSONObject().put("errors", JSONArray().put("Your password has expired. Please reset your password")).put("passwordExpired", true))
        }

        // Check MFA requirement
        val mfa = state.getMfaConfig(em)
        if (mfa != null) {
            val iss = state.issueRefresh(em)
            val at = mintAccess(em, iss.rec.tokenVersion, state.policy(em).accessTokenTTL)
            val mfaState = b64url(JSONObject().put("email", em).put("mfaType", mfa.type).put("token", at).put("refreshToken", iss.token).toString())
            return json(200, JSONObject()
                .put("mfaRequired", true)
                .put("mfaToken", mfaState)
                .put("mfaDevices", JSONArray().put(JSONObject().put("type", mfa.type))))
        }

        // Normal login
        val iss = state.issueRefresh(em)
        val resp = MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Set-Cookie", "fe_refresh_demoembedded-e2e-client=${iss.token}; Path=/; HttpOnly")
            .setBody(JSONObject(gson.toJson(tokenJson(iss.rec, iss.token))).apply {
                // Add password expiration warning if applicable
                val days = state.getPasswordExpiration(em)
                if (days != null && days > 0) {
                    put("passwordExpiration", JSONObject().put("daysUntilExpiry", days))
                }
            }.toString())
        return resp
    }

    // ── Magic Code / Magic Link / MFA handler methods ──

    private fun magicCodePage(hs: String, email: String): MockResponse {
        val b = """<h1>Enter verification code</h1>
    <p>We sent a code to ${htmlEsc(email)}</p>
    <div id="e2e-magic-error" style="color:red;display:none"></div>
    <form id="f"><input id="e2e-magic-code" type="text" maxlength="6" placeholder="000000"/>
    <button type="submit" id="e2e-magic-verify">Verify</button></form>
    <p><a id="e2e-resend-code" href="#">Resend code</a></p>
    <script>
    fetch('/identity/resources/auth/v1/passwordless/code/prelogin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'${htmlEsc(email)}'})});
    document.getElementById('f').onsubmit=async ev=>{ev.preventDefault();
    var errDiv=document.getElementById('e2e-magic-error');errDiv.style.display='none';
    const r=await fetch('/identity/resources/auth/v1/passwordless/code/postlogin',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({email:'${htmlEsc(email)}',code:document.getElementById('e2e-magic-code').value,state:'${hs}'})});
    const j=await r.json();
    if(!r.ok){errDiv.textContent=(j.errors||['Verification failed']).join('. ');errDiv.style.display='block';return;}
    if(j.mfaRequired){location='/oauth/account/mfa-mobile-authenticator?state='+encodeURIComponent(j.mfaToken);return;}
    if(j.redirectUrl){location=j.redirectUrl;}};
    document.getElementById('e2e-resend-code').onclick=async ev=>{ev.preventDefault();
    await fetch('/identity/resources/auth/v1/passwordless/code/resend',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'${htmlEsc(email)}'})});
    document.getElementById('e2e-resend-code').textContent='Code resent!';};
    </script>"""
        return html(200, "magic-code", b)
    }

    private fun magicLinkPage(hs: String, email: String): MockResponse {
        val b = """<h1>Check your email</h1>
    <p id="e2e-magic-link-sent">We sent a magic link to ${htmlEsc(email)}</p>
    <script>
    fetch('/identity/resources/auth/v1/passwordless/code/prelogin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'${htmlEsc(email)}',type:'magicLink'})});
    </script>"""
        return html(200, "magic-link", b)
    }

    private fun magicCodePrelogin(body: String): MockResponse {
        val jo = JSONObject(body.ifEmpty { "{}" })
        val email = jo.optString("email", "")
        val type = jo.optString("type", "magicCode")
        if (email.isEmpty()) return json(400, JSONObject().put("errors", JSONArray().put("Email required")))
        if (type == "magicLink") {
            state.issueMagicLink(email)
        } else {
            state.issueMagicCode(email)
        }
        return json(200, JSONObject().put("ok", true))
    }

    private fun magicCodePostlogin(body: String): MockResponse {
        val jo = JSONObject(body.ifEmpty { "{}" })
        val email = jo.optString("email", "")
        val code = jo.optString("code", "")
        val hs = jo.optString("state", "")
        val result = state.validateMagicCode(email, code)
        if (result != null) {
            return json(400, JSONObject().put("errors", JSONArray().put(result)))
        }
        // Check MFA
        val mfa = state.getMfaConfig(email)
        if (mfa != null) {
            val iss = state.issueRefresh(email)
            val at = mintAccess(email, iss.rec.tokenVersion, state.policy(email).accessTokenTTL)
            val mfaState = b64url(JSONObject().put("email", email).put("mfaType", mfa.type).put("token", at).put("refreshToken", iss.token).toString())
            return json(200, JSONObject().put("mfaRequired", true).put("mfaToken", mfaState))
        }
        val ctx = state.hosted(hs)
        if (ctx != null) {
            val codeVal = state.issueCode(email, ctx.redirect, ctx.origState)
            state.recordDone(hs, email)
            return json(200, JSONObject().put("redirectUrl", "/oauth/postlogin/redirect?state=${enc(hs)}"))
        }
        return json(400, JSONObject().put("errors", JSONArray().put("Invalid state")))
    }

    private fun magicCodeResend(body: String): MockResponse {
        val jo = JSONObject(body.ifEmpty { "{}" })
        val email = jo.optString("email", "")
        if (email.isEmpty()) return json(400, JSONObject().put("errors", JSONArray().put("Email required")))
        state.issueMagicCode(email)
        return json(200, JSONObject().put("ok", true))
    }

    private fun magicLinkVerify(q: Map<String, List<String>>): MockResponse {
        val token = fq(q, "token")
        if (token.isEmpty()) return html(400, "err", "<h1>Invalid link</h1>")
        val result = state.validateMagicLink(token)
        if (result != null) {
            return html(400, "err", "<h1>${htmlEsc(result)}</h1><p id='e2e-magic-link-error'>${htmlEsc(result)}</p>")
        }
        val entry = state.consumeMagicLink(token) ?: return html(400, "err", "<h1>Invalid link</h1>")
        val hs = state.latestHosted()
        if (hs != null) {
            val ctx = state.hosted(hs)
            if (ctx != null) {
                val code = state.issueCode(entry.email, ctx.redirect, ctx.origState)
                state.recordDone(hs, entry.email)
                return redir("/oauth/postlogin/redirect?state=${enc(hs)}")
            }
        }
        // Fallback: issue tokens directly
        val iss = state.issueRefresh(entry.email)
        return html(200, "Verified", """<p id="e2e-magic-link-verified">Email verified! Redirecting...</p>
        <script>setTimeout(function(){location='/'},1000);</script>""")
    }

    private fun mfaChallengePage(q: Map<String, List<String>>): MockResponse {
        val mfaState = fq(q, "state")
        if (mfaState.isEmpty()) return html(400, "err", "<h1>Invalid MFA state</h1>")
        val decoded = try {
            JSONObject(String(b64dec(mfaState), Charsets.UTF_8))
        } catch (_: Exception) {
            return html(400, "err", "<h1>Invalid MFA state</h1>")
        }
        val mfaType = decoded.optString("mfaType", "authenticator")
        val label = if (mfaType == "sms") "Enter SMS code" else "Enter authenticator code"
        val b = """<h1>$label</h1>
    <div id="e2e-mfa-error" style="color:red;display:none"></div>
    <form id="f"><input id="e2e-mfa-code" type="text" maxlength="6" placeholder="000000"/>
    <button type="submit" id="e2e-mfa-verify">Verify</button></form>
    <script>document.getElementById('f').onsubmit=async ev=>{ev.preventDefault();
    var errDiv=document.getElementById('e2e-mfa-error');errDiv.style.display='none';
    const r=await fetch('/identity/resources/auth/v1/user/mfa/verify',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({mfaToken:'${htmlEsc(mfaState)}',code:document.getElementById('e2e-mfa-code').value})});
    const j=await r.json();
    if(!r.ok){errDiv.textContent=(j.errors||['Invalid code']).join('. ');errDiv.style.display='block';return;}
    if(j.redirectUrl){location=j.redirectUrl;}};
    </script>"""
        return html(200, "mfa", b)
    }

    private fun mfaVerifyHandler(body: String): MockResponse {
        val jo = JSONObject(body.ifEmpty { "{}" })
        val mfaToken = jo.optString("mfaToken", "")
        val code = jo.optString("code", "")
        if (code != "123456") {
            return json(400, JSONObject().put("errors", JSONArray().put("Invalid verification code")))
        }
        val decoded = try {
            JSONObject(String(b64dec(mfaToken), Charsets.UTF_8))
        } catch (_: Exception) {
            return json(400, JSONObject().put("errors", JSONArray().put("Invalid MFA token")))
        }
        val email = decoded.optString("email", "")
        val hs = state.latestHosted()
        if (hs != null) {
            val ctx = state.hosted(hs)
            if (ctx != null) {
                val codeVal = state.issueCode(email, ctx.redirect, ctx.origState)
                state.recordDone(hs, email)
                return json(200, JSONObject().put("redirectUrl", "/oauth/postlogin/redirect?state=${enc(hs)}"))
            }
        }
        return json(400, JSONObject().put("errors", JSONArray().put("Invalid session")))
    }

    // ── Helpers ──

    private fun cookieRefresh(h: String?): String? {
        if (h.isNullOrBlank()) return null
        for (s in h.split(";")) {
            val t = s.trim()
            if (t.startsWith("fe_refresh_") && t.contains("=")) return t.substringAfter("=")
        }
        return null
    }

    private fun socialConfigArr(): MockResponse {
        val b = urlRoot()
        val a = JSONArray()
        a.put(
            JSONObject()
                .put("type", "google").put("active", true).put("customised", false)
                .put("clientId", "mock").put("redirectUrl", "$b/oauth/account/social/success")
                .put("redirectUrlPattern", "$b/oauth/account/social/success")
                .put("options", JSONObject().put("verifyEmail", false))
                .put("additionalScopes", JSONArray()),
        )
        return json(200, a)
    }

    private fun androidSocialConfig(): MockResponse {
        val b = urlRoot()
        return json(
            200,
            JSONObject().put(
                "google",
                JSONObject()
                    .put("active", true)
                    .put("clientId", "mock")
                    .put("authorizationUrl", "$b/idp/google/authorize")
                    .put("additionalScopes", JSONArray())
            )
        )
    }

    private fun mintAccess(email: String, ver: Int, ttl: Int): String {
        val now = (System.currentTimeMillis() / 1000).toInt()
        val pl = JSONObject()
            .put("sub", "user-${email.substringBefore("@")}")
            .put("email", email)
            .put("name", uname(email))
            .put("tenantId", tid(email))
            .put("tenantIds", JSONArray().put(tid(email)))
            .put("profilePictureUrl", "https://ex/a.png")
            .put("exp", now + ttl)
            .put("iat", now)
            .put("token_version", ver)
        val h = b64url("""{"alg":"none","typ":"JWT"}""")
        val p = b64url(pl.toString())
        return "$h.$p.sig"
    }

    private fun jwtEmail(token: String): String? {
        val parts = token.split(".")
        if (parts.size < 2) return null
        return try {
            val jo = JSONObject(String(b64dec(parts[1]), Charsets.UTF_8))
            jo.optString("email", "").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun b64url(s: String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        .replace('+', '-').replace('/', '_').trimEnd('=')

    private fun b64dec(s: String): ByteArray {
        var x = s.replace('-', '+').replace('_', '/')
        while (x.length % 4 != 0) x += "="
        return Base64.decode(x, Base64.DEFAULT)
    }

    private fun tokenJson(rec: RtRec, rt: String): Map<String, Any?> {
        val p = state.policy(rec.email)
        val at = mintAccess(rec.email, rec.tokenVersion, p.accessTokenTTL)
        return mapOf("token_type" to "Bearer", "refresh_token" to rt, "access_token" to at, "id_token" to at)
    }

    private fun userPayload(email: String): Map<String, Any?> {
        val t = tenantObj(email)
        val tid = t.getString("id")
        return mapOf(
            "id" to "user-${email.substringBefore("@")}",
            "email" to email,
            "mfaEnrolled" to false,
            "name" to uname(email),
            "profilePictureUrl" to "https://ex/a.png",
            "roles" to emptyList<Any>(),
            "permissions" to emptyList<Any>(),
            "tenantId" to tid,
            "tenantIds" to listOf(tid),
            "tenants" to listOf(jsonToMap(t)),
            "activeTenant" to jsonToMap(t),
            "activatedForTenant" to true,
            "metadata" to "{}",
            "verified" to true,
            "superUser" to false,
        )
    }

    private fun jsonToMap(jo: JSONObject): Map<String, Any?> {
        val m = mutableMapOf<String, Any?>()
        val it = jo.keys()
        while (it.hasNext()) {
            val k = it.next()
            m[k] = jo.get(k)
        }
        return m
    }

    private fun tenantObj(email: String) = JSONObject()
        .put("id", tid(email))
        .put("name", "${uname(email)} Tenant")
        .put("tenantId", tid(email))
        .put("createdAt", "2026-01-01T00:00:00Z")
        .put("updatedAt", "2026-01-01T00:00:00Z")
        .put("isReseller", false)
        .put("metadata", "{}")
        .put("vendorId", "v")

    private fun uname(email: String) =
        email.substringBefore("@").split(Regex("[._-]")).filter { it.isNotEmpty() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }.ifEmpty { "Demo" }

    private fun tid(email: String) =
        "tenant-${email.substringBefore("@").replace(".", "-").replace("_", "-")}"

    private fun cb(ru: String, code: String, st: String): String {
        val sep = if (ru.contains("?")) "&" else "?"
        return ru + sep + "code=${enc(code)}&state=${enc(st)}"
    }

    private fun cbErr(ru: String, st: String, e: String, d: String): String {
        val sep = if (ru.contains("?")) "&" else "?"
        return ru + sep + "error=${enc(e)}&error_description=${enc(d)}&state=${enc(st)}"
    }

    private fun signupPage(q: Map<String, List<String>>): MockResponse {
        val hs = fq(q, "state")
        val termsRequired = state.signupConfig.requireTerms
        val termsHtml = if (termsRequired) """<p><label><input type="checkbox" id="e2e-signup-terms"/> I agree to Terms of use and Privacy policy</label></p>""" else ""
        val b = """<h1>Sign up</h1>
        <div id="e2e-signup-error" style="color:red;display:none"></div>
        <form id="sf"><input id="e2e-signup-email" type="email" placeholder="Email"/>
        <input id="e2e-signup-name" type="text" placeholder="Name"/>
        <input id="e2e-signup-password" type="password" placeholder="Password"/>
        <input id="e2e-signup-org" type="text" placeholder="Organization"/>
        $termsHtml
        <button type="submit" id="e2e-signup-submit">Sign up</button></form>
        <p><a id="e2e-mock-google" href="#">Continue with Google</a></p>
        <script>
        document.getElementById('sf').onsubmit=async ev=>{ev.preventDefault();
        var errDiv=document.getElementById('e2e-signup-error');errDiv.style.display='none';
        var terms=document.getElementById('e2e-signup-terms');
        if(terms&&!terms.checked){errDiv.textContent='You must agree to the terms of use and privacy policy';errDiv.style.display='block';return;}
        const r=await fetch('/frontegg/identity/resources/auth/v1/user/signUp',{method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({email:document.getElementById('e2e-signup-email').value,name:document.getElementById('e2e-signup-name').value,
        password:document.getElementById('e2e-signup-password').value,companyName:document.getElementById('e2e-signup-org').value})});
        const j=await r.json();
        if(!r.ok){errDiv.textContent=(j.errors||['Signup failed']).join('. ');errDiv.style.display='block';return;}
        if(j.shouldVerifyEmail){document.body.innerHTML='<h1>Verify your email</h1><p id="e2e-verify-email-sent">Check your email to verify your account</p>';return;}
        location='/oauth/postlogin/redirect?state=${enc(hs)}';};</script>"""
        return html(200, "signup", b)
    }

    private fun signupHandler(body: String): MockResponse {
        val jo = JSONObject(body.ifEmpty { "{}" })
        val em = jo.optString("email", "")
        if (em.isEmpty()) return json(400, JSONObject().put("errors", JSONArray().put("Email is required")))
        if (state.signupConfig.requireEmailVerification) {
            return json(200, JSONObject().put("shouldVerifyEmail", true))
        }
        val hs = state.latestHosted()
        if (hs != null) {
            val ctx = state.hosted(hs)
            if (ctx != null) {
                state.issueCode(em, ctx.redirect, ctx.origState)
                state.recordDone(hs, em)
            }
        }
        val iss = state.issueRefresh(em)
        return json(200, JSONObject(gson.toJson(tokenJson(iss.rec, iss.token))))
    }

    private fun resetPasswordPage(q: Map<String, List<String>>): MockResponse {
        val b = """<h1>Reset password</h1>
        <form id="rf"><input id="e2e-reset-email" type="email" placeholder="Email"/>
        <button type="submit" id="e2e-reset-submit">Reset password</button></form>
        <div id="e2e-reset-confirmation" style="display:none"><p>Password reset email sent</p></div>
        <script>document.getElementById('rf').onsubmit=async ev=>{ev.preventDefault();
        await fetch('/identity/resources/auth/v1/user/passwords/reset',{method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({email:document.getElementById('e2e-reset-email').value})});
        document.getElementById('rf').style.display='none';
        document.getElementById('e2e-reset-confirmation').style.display='block';};</script>"""
        return html(200, "reset", b)
    }

    private fun activateAccountPage(q: Map<String, List<String>>): MockResponse {
        val b = """<h1>Activate your account</h1>
        <p id="e2e-activate-page">Set up your password to activate your account</p>
        <form id="af"><input id="e2e-activate-password" type="password" placeholder="Password"/>
        <button type="submit" id="e2e-activate-submit">Activate</button></form>"""
        return html(200, "activate", b)
    }

    private fun acceptInvitationPage(q: Map<String, List<String>>): MockResponse {
        val b = """<h1>Accept invitation</h1>
        <p id="e2e-invitation-page">You have been invited to join an organization</p>
        <button id="e2e-accept-invite">Accept invitation</button>"""
        return html(200, "invitation", b)
    }

    private fun unlockAccountPage(q: Map<String, List<String>>): MockResponse {
        val b = """<h1>Account unlocked</h1>
        <p id="e2e-unlock-page">Your account has been unlocked successfully</p>
        <p><a href="/">Back to login</a></p>"""
        return html(200, "unlock", b)
    }

    private fun verifyEmailPage(q: Map<String, List<String>>): MockResponse {
        val b = """<h1>Email verified</h1>
        <p id="e2e-verify-email-page">Your email has been verified successfully</p>
        <p><a href="/">Continue to login</a></p>"""
        return html(200, "verify-email", b)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /**
     * Redirect to [loc]. Chrome Custom Tabs on API 34+ block HTTP 302 redirects to custom URL
     * schemes (com.frontegg.demo://...). When the target is a custom scheme, serve an HTML page
     * with a JS `window.location.href` redirect instead — Chrome allows JS-initiated navigation
     * to custom schemes from same-origin pages.
     */
    private fun redir(loc: String): MockResponse {
        val isCustomScheme = !loc.startsWith("http://") && !loc.startsWith("https://") && loc.contains("://")
        if (isCustomScheme) {
            val jsLoc = loc.replace("\\", "\\\\").replace("'", "\\'")
            return html(200, "Redirect", """<p>Redirecting…</p>
                <script>(function(){window.location.href='$jsLoc';})()</script>""")
        }
        return MockResponse().setResponseCode(302).addHeader("Location", loc)
    }

    private fun html(c: Int, @Suppress("UNUSED_PARAMETER") title: String, body: String) =
        MockResponse().setResponseCode(c).addHeader("Content-Type", "text/html; charset=utf-8")
            .setBody("<!DOCTYPE html><html><body>$body</body></html>")

    private fun json(c: Int, jo: JSONObject) =
        MockResponse().setResponseCode(c).addHeader("Content-Type", "application/json; charset=utf-8").setBody(jo.toString())

    private fun json(c: Int, arr: JSONArray) =
        MockResponse().setResponseCode(c).addHeader("Content-Type", "application/json; charset=utf-8").setBody(arr.toString())

    private fun text(c: Int, t: String) =
        MockResponse().setResponseCode(c).addHeader("Content-Type", "text/plain; charset=utf-8").setBody(t)

    private fun htmlEsc(s: String) =
        s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private fun fq(q: Map<String, List<String>>, k: String, d: String = ""): String = q[k]?.firstOrNull() ?: d
    private fun normPath(p: String) = if (p.isEmpty() || p.startsWith("/")) p else "/$p"

    private fun parseQuery(raw: String): Map<String, List<String>> {
        val qs = raw.substringAfter('?', "")
        if (qs.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, MutableList<String>>()
        for (pair in qs.split("&")) {
            if (pair.isEmpty()) continue
            val a = pair.split("=", limit = 2)
            val k = URLDecoder.decode(a[0], StandardCharsets.UTF_8.name())
            val v = URLDecoder.decode(a.getOrElse(1) { "" }, StandardCharsets.UTF_8.name())
            out.getOrPut(k) { mutableListOf() }.add(v)
        }
        return out
    }

    private fun parseForm(body: String): Map<String, String> {
        if (body.isEmpty()) return emptyMap()
        val m = mutableMapOf<String, String>()
        for (pair in body.split("&")) {
            if (pair.isEmpty()) continue
            val a = pair.split("=", limit = 2)
            m[URLDecoder.decode(a[0], StandardCharsets.UTF_8.name())] =
                URLDecoder.decode(a.getOrElse(1) { "" }, StandardCharsets.UTF_8.name())
        }
        return m
    }

    private fun decodeB64Json(b64: String): JsonObject? = try {
        var s = b64.replace('-', '+').replace('_', '/')
        while (s.length % 4 != 0) s += "="
        gson.fromJson(String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8), JsonObject::class.java)
    } catch (_: Exception) {
        null
    }

}

private data class Hosted(val redirect: String, val origState: String, val loginHint: String)
private data class AuthCode(val email: String, val redirect: String, val state: String)
private data class Pol(val accessTokenTTL: Int = 3600, val refreshTokenTTL: Int = 86400, val startingTokenVersion: Int = 1)
private data class RtRec(val email: String, val exp: Double, var tokenVersion: Int)
private data class Issued(val token: String, val rec: RtRec)
private data class PasswordPolicyConfig(val minLength: Int, val requireUppercase: Boolean, val requireNumber: Boolean, val requireSpecial: Boolean)
private data class LockoutConfig(val maxAttempts: Int)
private data class MfaConfig(val type: String) // "authenticator" | "sms"
private data class MagicCodeEntry(val code: String, val email: String, val expiresAt: Long, var used: Boolean = false)
private data class MagicLinkEntry(val token: String, val email: String, val expiresAt: Long, var used: Boolean = false)
private data class SignupConfig(val requireTerms: Boolean = false, val requireEmailVerification: Boolean = false)
private data class AuthStrategiesConfig(val password: Boolean = true, val magicCode: Boolean = false, val magicLink: Boolean = false, val socialLogin: Boolean = true, val sso: Boolean = true)

private class MockAuthState {
    private val lock = ReentrantLock()
    private val q = mutableMapOf<String, MutableList<Map<String, Any?>>>()
    private val codes = mutableMapOf<String, AuthCode>()
    private val hosted = mutableMapOf<String, Hosted>()
    private var latestH: String? = null
    private val done = mutableMapOf<String, String>()
    private val rts = mutableMapOf<String, RtRec>()
    private val pols = mutableMapOf<String, Pol>()
    private var oauthErr: Pair<String, String>? = null

    // Phase 0-3 state fields
    var passwordPolicy: PasswordPolicyConfig? = null
    var lockoutConfig: LockoutConfig? = null
    val lockoutState = mutableMapOf<String, Int>()
    val mfaConfig = mutableMapOf<String, MfaConfig>()
    val magicCodes = mutableMapOf<String, MagicCodeEntry>()
    val magicLinks = mutableMapOf<String, MagicLinkEntry>()
    var signupConfig: SignupConfig = SignupConfig()
    val passwordExpiration = mutableMapOf<String, Int>()
    val breachedEmails = mutableSetOf<String>()
    var authStrategies: AuthStrategiesConfig = AuthStrategiesConfig()
    var magicCodeTTLSeconds: Int = 300

    fun reset() = lock.withLock {
        q.clear(); codes.clear(); hosted.clear(); latestH = null; done.clear(); pols.clear(); oauthErr = null; rts.clear()
        passwordPolicy = null; lockoutConfig = null; lockoutState.clear()
        mfaConfig.clear(); magicCodes.clear(); magicLinks.clear()
        signupConfig = SignupConfig(); passwordExpiration.clear(); breachedEmails.clear()
        authStrategies = AuthStrategiesConfig(); magicCodeTTLSeconds = 300
        rts["signup-refresh-token"] = RtRec(
            "signup@frontegg.com",
            System.currentTimeMillis() / 1000.0 + Pol().refreshTokenTTL,
            Pol().startingTokenVersion,
        )
    }

    init {
        reset()
    }

    fun enqueue(method: String, path: String, responses: List<Map<String, Any?>>) = lock.withLock {
        val k = "${method.uppercase()} ${norm(path)}"
        q.getOrPut(k) { mutableListOf() }.addAll(responses)
    }

    fun dequeue(method: String, path: String): Map<String, Any?>? = lock.withLock {
        val k = "${method.uppercase()} ${norm(path)}"
        val list = q[k] ?: return@withLock null
        if (list.isEmpty()) return@withLock null
        val r = list.removeAt(0)
        if (list.isEmpty()) q.remove(k)
        r
    }

    fun issueCode(email: String, redirect: String, state: String) = lock.withLock {
        val c = "code-${UUID.randomUUID().toString().lowercase()}"
        codes[c] = AuthCode(email, redirect, state)
        c
    }

    fun peekCode(c: String) = lock.withLock { codes[c] }
    fun consumeCode(c: String) = lock.withLock { codes.remove(c) }

    fun issueHosted(redirect: String, orig: String, hint: String) = lock.withLock {
        val hs = "hosted-${UUID.randomUUID().toString().lowercase()}"
        hosted[hs] = Hosted(redirect, orig, hint)
        latestH = hs
        hs
    }

    fun hosted(h: String) = lock.withLock { hosted[h] }
    fun recordDone(h: String, e: String) = lock.withLock { done[h] = e }
    fun doneEmail(h: String) = lock.withLock { done[h] }
    fun latestHosted() = lock.withLock { latestH }

    fun configureTokenPolicy(email: String, a: Int, r: Int, sv: Int) = lock.withLock {
        pols[email.lowercase()] = Pol(a, r, sv)
    }

    fun policy(email: String) = lock.withLock { pols[email.lowercase()] ?: Pol() }

    fun issueRefresh(email: String) = lock.withLock {
        val t = "refresh-${UUID.randomUUID().toString().lowercase()}"
        val p = pols[email.lowercase()] ?: Pol()
        val rec = RtRec(email, System.currentTimeMillis() / 1000.0 + p.refreshTokenTTL, p.startingTokenVersion)
        rts[t] = rec
        Issued(t, rec)
    }

    fun validRt(t: String): RtRec? = lock.withLock {
        val r = rts[t] ?: return@withLock null
        if (r.exp <= System.currentTimeMillis() / 1000.0) {
            rts.remove(t)
            return@withLock null
        }
        r
    }

    fun refresh(t: String): RtRec? = lock.withLock {
        val r = rts[t] ?: return@withLock null
        if (r.exp <= System.currentTimeMillis() / 1000.0) {
            rts.remove(t)
            return@withLock null
        }
        val n = r.copy(tokenVersion = r.tokenVersion + 1)
        rts[t] = n
        n
    }

    fun invalidateRefreshToken(t: String) = lock.withLock { rts.remove(t) }
    fun queueEmbeddedSocialSuccessOAuthError(c: String, d: String) = lock.withLock { oauthErr = c to d }
    fun consumeOAuthErr() = lock.withLock {
        val x = oauthErr
        oauthErr = null
        x
    }

    // Phase 0-3 methods
    fun configurePasswordPolicy(ml: Int, u: Boolean, n: Boolean, s: Boolean) = lock.withLock {
        passwordPolicy = PasswordPolicyConfig(ml, u, n, s)
    }
    fun configureLockout(max: Int) = lock.withLock { lockoutConfig = LockoutConfig(max) }
    fun configureMfa(email: String, type: String) = lock.withLock { mfaConfig[email.lowercase()] = MfaConfig(type) }
    fun getMfaConfig(email: String): MfaConfig? = lock.withLock { mfaConfig[email.lowercase()] }
    fun configureAuthStrategies(pw: Boolean, mc: Boolean, ml: Boolean, sl: Boolean, sso: Boolean) = lock.withLock {
        authStrategies = AuthStrategiesConfig(pw, mc, ml, sl, sso)
    }
    fun configureSignup(terms: Boolean, emailVerify: Boolean) = lock.withLock {
        signupConfig = SignupConfig(terms, emailVerify)
    }
    fun configurePasswordExpiration(email: String, days: Int) = lock.withLock {
        passwordExpiration[email.lowercase()] = days
    }
    fun getPasswordExpiration(email: String): Int? = lock.withLock { passwordExpiration[email.lowercase()] }
    fun configureBreachedPassword(email: String) = lock.withLock { breachedEmails.add(email.lowercase()) }
    fun isBreachedEmail(email: String): Boolean = lock.withLock { email.lowercase() in breachedEmails }
    fun configureMagicCodeTTL(seconds: Int) = lock.withLock { magicCodeTTLSeconds = seconds }
    fun incrementLockout(email: String): Int = lock.withLock {
        val count = (lockoutState[email.lowercase()] ?: 0) + 1
        lockoutState[email.lowercase()] = count
        count
    }
    fun issueMagicCode(email: String): String = lock.withLock {
        val code = "%06d".format((100000..999999).random())
        val ttl = magicCodeTTLSeconds
        magicCodes[email.lowercase()] = MagicCodeEntry(code, email, System.currentTimeMillis() + ttl * 1000)
        code
    }
    fun getIssuedMagicCode(email: String): String? = lock.withLock { magicCodes[email.lowercase()]?.code }
    fun validateMagicCode(email: String, code: String): String? = lock.withLock {
        val entry = magicCodes[email.lowercase()] ?: return@withLock "Invalid code"
        if (entry.used) return@withLock "Code already used"
        if (System.currentTimeMillis() > entry.expiresAt) return@withLock "Code expired"
        if (entry.code != code) return@withLock "Invalid code"
        entry.used = true
        null
    }
    fun issueMagicLink(email: String): String = lock.withLock {
        val token = "magiclink-${UUID.randomUUID().toString().lowercase()}"
        val ttl = magicCodeTTLSeconds
        magicLinks[token] = MagicLinkEntry(token, email, System.currentTimeMillis() + ttl * 1000)
        token
    }
    fun getIssuedMagicLinkUrl(email: String): String? = lock.withLock {
        magicLinks.values.firstOrNull { it.email.equals(email, ignoreCase = true) }?.token
    }
    fun validateMagicLink(token: String): String? = lock.withLock {
        val entry = magicLinks[token] ?: return@withLock "Invalid link"
        if (entry.used) return@withLock "Link already used"
        if (System.currentTimeMillis() > entry.expiresAt) return@withLock "Link expired"
        null
    }
    fun consumeMagicLink(token: String): MagicLinkEntry? = lock.withLock {
        val entry = magicLinks[token] ?: return@withLock null
        entry.used = true
        entry
    }

    private fun norm(p: String) = if (p.isEmpty() || p == "/") p else if (p.startsWith("/")) p else "/$p"
}
