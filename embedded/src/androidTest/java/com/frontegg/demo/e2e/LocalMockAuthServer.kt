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

    fun configurePasswordPolicy(complexity: String) = state.setPasswordComplexity(complexity)
    fun configureMfa(email: String, type: String) = state.configureMfa(email, type)
    fun configureLoginMethod(email: String, method: String) = state.configureLoginMethod(email, method)
    fun configurePasswordExpiration(email: String, daysLeft: Int, canRemindLater: Boolean) = state.configurePasswordExpiration(email, daysLeft, canRemindLater)
    fun configureAccountLocking(email: String, maxAttempts: Int) = state.configureAccountLocking(email, maxAttempts)
    fun configureBreachedPassword(password: String) = state.addBreachedPassword(password)
    fun configureTosRequired(required: Boolean) = state.setTosRequired(required)
    fun configureEmailVerificationRedirect(enabled: Boolean) = state.setEmailVerificationRedirect(enabled)
    fun configureCustomLoginBox(enabled: Boolean) = state.setCustomLoginBox(enabled)
    fun configureMagicCodeExpiration(expirationMs: Long) = state.setMagicCodeExpiration(expirationMs)
    fun configureMagicLinkExpiration(expirationMs: Long) = state.setMagicLinkExpiration(expirationMs)

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
                json(200, JSONObject().put("embeddedMode", true).put("loginBoxVisible", !state.isCustomLoginBox()).put("customLoginBox", state.isCustomLoginBox()))
            method == "GET" && path == "/frontegg/identity/resources/configurations/v1/auth/strategies/public" ->
                json(200, JSONObject().put("password", true).put("socialLogin", true).put("sso", true))
            method == "GET" && path == "/frontegg/identity/resources/configurations/v1/sign-up/strategies" ->
                json(200, JSONObject().put("allowSignUp", true).put("tosRequired", state.isTosRequired()).put("emailVerificationRequired", state.isEmailVerificationRedirect()))
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
            method == "GET" && path == "/embedded/magic-code" -> magicCodePage(q)
            method == "POST" && path == "/embedded/magic-code/verify" -> magicCodeVerify(body)
            method == "POST" && path == "/embedded/magic-code/resend" -> magicCodeResend(body)
            method == "GET" && path == "/embedded/magic-link/sent" -> magicLinkSentPage(q)
            method == "GET" && path == "/embedded/magic-link/callback" -> magicLinkCallback(q)
            method == "GET" && path == "/embedded/mfa" -> mfaPage(q)
            method == "POST" && path == "/embedded/mfa/verify" -> mfaVerify(body)
            method == "GET" && path == "/embedded/sms-login" -> smsLoginPage(q)
            method == "POST" && path == "/embedded/sms-login/verify" -> smsLoginVerify(body)
            method == "GET" && path == "/embedded/username-login" -> usernameLoginPage(q)
            method == "POST" && path == "/embedded/username-login/submit" -> usernameLoginSubmit(body)
            method == "GET" && path == "/embedded/signup" -> signupPage(q)
            method == "POST" && path == "/embedded/signup/submit" -> signupSubmit(body)
            method == "GET" && path == "/embedded/signup/verify-email" -> signupVerifyEmailPage(q)
            method == "GET" && path == "/embedded/signup/verify-email/callback" -> signupVerifyEmailCallback(q)
            method == "GET" && path == "/embedded/forgot-password" -> forgotPasswordPage(q)
            method == "POST" && path == "/embedded/forgot-password/submit" -> forgotPasswordSubmit(body)
            method == "GET" && path == "/embedded/reset-password" -> resetPasswordPage(q)
            method == "POST" && path == "/embedded/reset-password/submit" -> resetPasswordSubmit(body)
            method == "GET" && path == "/embedded/password-expiring" -> passwordExpiringPage(q)
            method == "GET" && path == "/embedded/password-expiring/skip" -> passwordExpiringSkip(q)
            method == "GET" && path == "/embedded/password-expired" -> passwordExpiredPage(q)
            method == "GET" && path == "/embedded/confirm/activation" -> confirmationPage("activation", q)
            method == "GET" && path == "/embedded/confirm/invitation" -> confirmationPage("invitation", q)
            method == "GET" && path == "/embedded/confirm/unlock" -> confirmationPage("unlock", q)
            method == "POST" && path == "/embedded/confirm/submit" -> confirmationSubmit(body)
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
            // Use HTML auto-click instead of 302 redirect — Chrome blocks 302 chains ending in custom URL schemes
            // when initiated from Custom Tab. The HTML+JS click pattern is the same one Custom SSO uses successfully.
            if (typ.equals("social-login", ignoreCase = true) && dest.equals("google", ignoreCase = true)) {
                val href =
                    "/idp/google/authorize?redirect_uri=${enc(redirect)}&state=${enc(st)}"
                val htmlBody = """<h1>Mock Google</h1><p><a id="e2e-complete" href="$href">Continue with Mock Google</a></p>
                <script>
                setTimeout(function(){
                  var e=document.getElementById('e2e-complete');
                  if(e) e.click();
                }, 900);
                </script>"""
                return html(200, "Mock Google", htmlBody)
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
        val loginMethod = state.loginMethod(email)
        return when {
            loginMethod == "magic-code" -> {
                val ctx2 = state.hosted(hs)!!
                redir("/embedded/magic-code?email=${enc(email)}&redirect_uri=${enc(ctx2.redirect)}&state=${enc(ctx2.origState)}")
            }
            loginMethod == "magic-link" -> {
                val ctx2 = state.hosted(hs)!!
                redir("/embedded/magic-link/sent?email=${enc(email)}&redirect_uri=${enc(ctx2.redirect)}&state=${enc(ctx2.origState)}")
            }
            loginMethod == "sms" -> {
                val ctx2 = state.hosted(hs)!!
                redir("/embedded/sms-login?redirect_uri=${enc(ctx2.redirect)}&state=${enc(ctx2.origState)}&email=${enc(email)}")
            }
            loginMethod == "username" -> {
                val ctx2 = state.hosted(hs)!!
                redir("/embedded/username-login?redirect_uri=${enc(ctx2.redirect)}&state=${enc(ctx2.origState)}")
            }
            email.endsWith("@saml-domain.com") ->
                providerPage("OKTA SAML Mock Server", "Login With Okta", hs, email)
            email.endsWith("@oidc-domain.com") ->
                providerPage("OKTA OIDC Mock Server", "Login With Okta", hs, email)
            else -> passwordPage(hs, email, ctx.loginHint.isNotEmpty())
        }
    }

    private fun passwordPage(hs: String, email: String, prefill: Boolean): MockResponse {
        val pv = if (prefill) """ value="Testpassword1!"""" else ""
        val b =
            """<h1>Password Login</h1><form id="f"><input id="e" type="email" value="${htmlEsc(email)}"/>
            <input id="p" type="password" name="password"$pv/><button type="submit" id="e2e-password-submit">Sign in</button></form>
            <script>document.getElementById('f').onsubmit=async ev=>{ev.preventDefault();
            await fetch('/frontegg/identity/resources/auth/v2/user/sso/prelogin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:document.getElementById('e').value})}).catch(()=>null);
            const r=await fetch('/frontegg/identity/resources/auth/v1/user',{method:'POST',headers:{'Content-Type':'application/json'},
            body:JSON.stringify({email:document.getElementById('e').value,password:document.getElementById('p').value,invitationToken:''})});
            if(!r.ok)return;const j=await r.json();
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
        val loginMethod = state.loginMethod(email)
        return when {
            loginMethod == "magic-code" -> redir("/embedded/magic-code?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}")
            loginMethod == "magic-link" -> redir("/embedded/magic-link/sent?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}")
            loginMethod == "sms" -> redir("/embedded/sms-login?redirect_uri=${enc(ru)}&state=${enc(st)}&email=${enc(email)}")
            loginMethod == "username" -> redir("/embedded/username-login?redirect_uri=${enc(ru)}&state=${enc(st)}")
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
        val password = f["password"] ?: ""
        val ru = f["redirect_uri"] ?: ""
        val st = f["state"] ?: ""
        // Account lock check
        if (state.isAccountLocked(email)) {
            return html(200, "error", """<h1>Error</h1><p id="e2e-error">Your account is locked</p>""")
        }
        // Breached password check
        if (state.isBreachedPassword(password)) {
            return html(200, "error", """<h1>Error</h1><p id="e2e-error">Password has been breached</p>""")
        }
        // Password complexity check
        val complexityError = validatePasswordComplexity(password, state.getPasswordComplexity())
        if (complexityError != null) {
            return html(200, "error", """<h1>Error</h1><p id="e2e-error">$complexityError</p>""")
        }
        // MFA check
        val mfa = state.mfaType(email)
        if (mfa != null) {
            return redir("/embedded/mfa?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}&mfa_type=${enc(mfa)}")
        }
        // Password expiration check
        val expiry = state.passwordExpiration(email)
        if (expiry != null) {
            val (days, canRemind, isExpired) = expiry
            if (isExpired) {
                return redir("/embedded/password-expired?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}")
            } else {
                return redir("/embedded/password-expiring?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}&days=$days&can_remind=$canRemind")
            }
        }
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
        // Account lock check
        if (state.isAccountLocked(em)) {
            state.recordFailedAttempt(em)
            return json(423, JSONObject().put("errors", JSONArray().put("Your account is locked")))
        }
        // Breached password check
        if (state.isBreachedPassword(pw)) {
            return json(400, JSONObject().put("errors", JSONArray().put("Password has been breached")))
        }
        val iss = state.issueRefresh(em)
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Set-Cookie", "fe_refresh_demoembedded-e2e-client=${iss.token}; Path=/; HttpOnly")
            .setBody(JSONObject(gson.toJson(tokenJson(iss.rec, iss.token))).toString())
    }

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

    // ── New route handlers ────────────────────────────────────────────

    private fun magicCodePage(q: Map<String, List<String>>): MockResponse {
        val email = fq(q, "email", "")
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        state.issueMagicCode(email)
        val b = """<h1>Enter code</h1>
            <p>We sent a 6-digit code to $email</p>
            <form id="f" action="/embedded/magic-code/verify" method="post">
            <input type="hidden" name="email" value="${htmlEsc(email)}"/>
            <input type="hidden" name="redirect_uri" value="${htmlEsc(ru)}"/>
            <input type="hidden" name="state" value="${htmlEsc(st)}"/>
            <input type="text" id="e2e-code-input" name="code" placeholder="Enter code"/>
            <button type="submit" id="e2e-magic-code-submit">Verify</button>
            </form>
            <form action="/embedded/magic-code/resend" method="post">
            <input type="hidden" name="email" value="${htmlEsc(email)}"/>
            <input type="hidden" name="redirect_uri" value="${htmlEsc(ru)}"/>
            <input type="hidden" name="state" value="${htmlEsc(st)}"/>
            <button type="submit" id="e2e-resend-code">Resend code</button>
            </form>"""
        return html(200, "magic-code", b)
    }

    private fun magicCodeVerify(body: String): MockResponse {
        val f = parseForm(body)
        val email = f["email"] ?: ""
        val code = f["code"] ?: ""
        val ru = f["redirect_uri"] ?: ""
        val st = f["state"] ?: ""
        if (state.isMagicCodeExpired(email)) {
            return html(200, "error", """<h1>Error</h1><p id="e2e-error">Code expired</p>
                <a href="/embedded/magic-code?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}">Try again</a>""")
        }
        if (!state.verifyMagicCode(email, code)) {
            return html(200, "error", """<h1>Error</h1><p id="e2e-error">Invalid code</p>
                <a href="/embedded/magic-code?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}">Try again</a>""")
        }
        // Check MFA
        val mfa = state.mfaType(email)
        if (mfa != null) {
            return redir("/embedded/mfa?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}&mfa_type=${enc(mfa)}")
        }
        val authCode = state.issueCode(email, ru, st)
        return redir(cb(ru, authCode, st))
    }

    private fun magicCodeResend(body: String): MockResponse {
        val f = parseForm(body)
        val email = f["email"] ?: ""
        val ru = f["redirect_uri"] ?: ""
        val st = f["state"] ?: ""
        state.issueMagicCode(email)
        return redir("/embedded/magic-code?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}")
    }

    private fun magicLinkSentPage(q: Map<String, List<String>>): MockResponse {
        val email = fq(q, "email", "")
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val token = state.issueMagicLink(email)
        val callbackUrl = "/embedded/magic-link/callback?token=${enc(token)}&email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}"
        val b = """<h1>Check your email</h1>
            <p>We sent a magic link to $email</p>
            <p id="e2e-magic-link-sent">Magic link sent</p>
            <script>setTimeout(function(){ window.location.href='$callbackUrl'; }, 3000);</script>"""
        return html(200, "magic-link-sent", b)
    }

    private fun magicLinkCallback(q: Map<String, List<String>>): MockResponse {
        val token = fq(q, "token", "")
        @Suppress("UNUSED_VARIABLE") val email = fq(q, "email", "")
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val verified = state.verifyMagicLink(token)
        if (verified == null) {
            return html(200, "error", """<h1>Error</h1><p id="e2e-error">Link expired</p>""")
        }
        val mfa = state.mfaType(verified)
        if (mfa != null) {
            return redir("/embedded/mfa?email=${enc(verified)}&redirect_uri=${enc(ru)}&state=${enc(st)}&mfa_type=${enc(mfa)}")
        }
        val authCode = state.issueCode(verified, ru, st)
        return redir(cb(ru, authCode, st))
    }

    private fun mfaPage(q: Map<String, List<String>>): MockResponse {
        val email = fq(q, "email", "")
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val mfaType = fq(q, "mfa_type", "authenticator")
        val label = if (mfaType == "sms") "Enter SMS code" else "Enter authenticator code"
        val b = """<h1>MFA Verification</h1>
            <p id="e2e-mfa-label">$label</p>
            <form id="f" action="/embedded/mfa/verify" method="post">
            <input type="hidden" name="email" value="${htmlEsc(email)}"/>
            <input type="hidden" name="redirect_uri" value="${htmlEsc(ru)}"/>
            <input type="hidden" name="state" value="${htmlEsc(st)}"/>
            <input type="text" id="e2e-mfa-code-input" name="code" placeholder="$label"/>
            <button type="submit" id="e2e-mfa-submit">Verify</button>
            </form>"""
        return html(200, "mfa", b)
    }

    private fun mfaVerify(body: String): MockResponse {
        val f = parseForm(body)
        val email = f["email"] ?: ""
        val code = f["code"] ?: ""
        val ru = f["redirect_uri"] ?: ""
        val st = f["state"] ?: ""
        if (code != "123456") {
            return html(200, "error", """<h1>Error</h1><p id="e2e-error">Invalid MFA code</p>
                <a href="javascript:history.back()">Try again</a>""")
        }
        val authCode = state.issueCode(email, ru, st)
        return redir(cb(ru, authCode, st))
    }

    private fun smsLoginPage(q: Map<String, List<String>>): MockResponse {
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val step = fq(q, "step", "phone")
        val email = fq(q, "email", "")
        if (step == "code") {
            state.issueMagicCode(email)
            val b = """<h1>Enter SMS code</h1>
                <p>We sent a code to your phone</p>
                <form action="/embedded/sms-login/verify" method="post">
                <input type="hidden" name="email" value="${htmlEsc(email)}"/>
                <input type="hidden" name="redirect_uri" value="${htmlEsc(ru)}"/>
                <input type="hidden" name="state" value="${htmlEsc(st)}"/>
                <input type="text" id="e2e-sms-code-input" name="code" placeholder="Enter code"/>
                <button type="submit" id="e2e-sms-verify">Verify</button>
                </form>"""
            return html(200, "sms-code", b)
        }
        val b = """<h1>Login with SMS</h1>
            <form action="/embedded/sms-login" method="get">
            <input type="hidden" name="redirect_uri" value="${htmlEsc(ru)}"/>
            <input type="hidden" name="state" value="${htmlEsc(st)}"/>
            <input type="hidden" name="step" value="code"/>
            <input type="tel" id="e2e-phone-input" name="phone" placeholder="Phone number"/>
            <input type="hidden" name="email" value="test-sms@frontegg.com"/>
            <button type="submit" id="e2e-sms-submit-phone">Continue</button>
            </form>"""
        return html(200, "sms-login", b)
    }

    private fun smsLoginVerify(body: String): MockResponse {
        val f = parseForm(body)
        val email = f["email"] ?: "test-sms@frontegg.com"
        val code = f["code"] ?: ""
        val ru = f["redirect_uri"] ?: ""
        val st = f["state"] ?: ""
        if (!state.verifyMagicCode(email, code)) {
            return html(200, "error", """<h1>Error</h1><p id="e2e-error">Invalid code</p>""")
        }
        val authCode = state.issueCode(email, ru, st)
        return redir(cb(ru, authCode, st))
    }

    private fun usernameLoginPage(q: Map<String, List<String>>): MockResponse {
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val b = """<h1>Login with username</h1>
            <form action="/embedded/username-login/submit" method="post">
            <input type="hidden" name="redirect_uri" value="${htmlEsc(ru)}"/>
            <input type="hidden" name="state" value="${htmlEsc(st)}"/>
            <input type="text" id="e2e-username-input" name="username" placeholder="Username"/>
            <input type="password" id="e2e-username-password-input" name="password" placeholder="Password"/>
            <button type="submit" id="e2e-username-submit">Sign in</button>
            </form>"""
        return html(200, "username-login", b)
    }

    private fun usernameLoginSubmit(body: String): MockResponse {
        val f = parseForm(body)
        val ru = f["redirect_uri"] ?: ""
        val st = f["state"] ?: ""
        val email = "test-username@frontegg.com"
        val authCode = state.issueCode(email, ru, st)
        return redir(cb(ru, authCode, st))
    }

    private fun signupPage(q: Map<String, List<String>>): MockResponse {
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val method = fq(q, "method", "email") // "email"|"sms"|"username"
        val tosCheckbox = if (state.isTosRequired()) {
            """<label><input type="checkbox" id="e2e-tos-checkbox" name="tos" value="true"/> I accept the Terms of Use and Privacy Policy</label>"""
        } else ""
        val fields = when (method) {
            "sms" -> """<input type="tel" id="e2e-signup-phone" name="phone" placeholder="Phone number"/>
                <input type="text" id="e2e-signup-name" name="name" placeholder="Name"/>
                <input type="password" id="e2e-signup-password" name="password" placeholder="Password"/>"""
            "username" -> """<input type="text" id="e2e-signup-username" name="username" placeholder="Username"/>
                <input type="text" id="e2e-signup-name" name="name" placeholder="Name"/>
                <input type="password" id="e2e-signup-password" name="password" placeholder="Password"/>"""
            else -> """<input type="email" id="e2e-signup-email" name="email" placeholder="Email"/>
                <input type="text" id="e2e-signup-name" name="name" placeholder="Name"/>
                <input type="password" id="e2e-signup-password" name="password" placeholder="Password"/>
                <input type="text" id="e2e-signup-company" name="company" placeholder="Company name"/>"""
        }
        val b = """<h1>Sign up</h1>
            <form action="/embedded/signup/submit" method="post">
            <input type="hidden" name="redirect_uri" value="${htmlEsc(ru)}"/>
            <input type="hidden" name="state" value="${htmlEsc(st)}"/>
            <input type="hidden" name="method" value="${htmlEsc(method)}"/>
            $fields
            $tosCheckbox
            <button type="submit" id="e2e-signup-submit">Sign up</button>
            </form>"""
        return html(200, "signup", b)
    }

    private fun signupSubmit(body: String): MockResponse {
        val f = parseForm(body)
        val ru = f["redirect_uri"] ?: ""
        val st = f["state"] ?: ""
        val method = f["method"] ?: "email"
        val tos = f["tos"]
        if (state.isTosRequired() && tos != "true") {
            return html(200, "error", """<h1>Error</h1><p id="e2e-error">You must accept the Terms of Use and Privacy Policy</p>
                <a href="javascript:history.back()">Go back</a>""")
        }
        val email = when (method) {
            "sms" -> "test-sms-signup@frontegg.com"
            "username" -> "test-username-signup@frontegg.com"
            else -> f["email"] ?: "test-signup@frontegg.com"
        }
        if (state.isEmailVerificationRedirect()) {
            return redir("/embedded/signup/verify-email?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}")
        }
        val authCode = state.issueCode(email, ru, st)
        return redir(cb(ru, authCode, st))
    }

    private fun signupVerifyEmailPage(q: Map<String, List<String>>): MockResponse {
        val email = fq(q, "email", "")
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val callbackUrl = "/embedded/signup/verify-email/callback?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}"
        val tosRequired = state.isTosRequired()
        val tosField = if (tosRequired) {
            """<label><input type="checkbox" id="e2e-tos-checkbox" name="tos" value="true"/> I accept the Terms of Use and Privacy Policy</label>"""
        } else ""
        val b = """<h1>Verify your email</h1>
            <p>We sent a verification link to $email</p>
            <p id="e2e-verify-email-sent">Verification email sent</p>
            $tosField
            <script>setTimeout(function(){ window.location.href='$callbackUrl'; }, 3000);</script>"""
        return html(200, "verify-email", b)
    }

    private fun signupVerifyEmailCallback(q: Map<String, List<String>>): MockResponse {
        val email = fq(q, "email", "")
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val authCode = state.issueCode(email, ru, st)
        return redir(cb(ru, authCode, st))
    }

    private fun forgotPasswordPage(q: Map<String, List<String>>): MockResponse {
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val method = fq(q, "method", "email") // "email"|"sms"
        val inputField = if (method == "sms") {
            """<input type="tel" id="e2e-forgot-phone" name="phone" placeholder="Phone number"/>"""
        } else {
            """<input type="email" id="e2e-forgot-email" name="email" placeholder="Email"/>"""
        }
        val b = """<h1>Forgot password</h1>
            <form action="/embedded/forgot-password/submit" method="post">
            <input type="hidden" name="redirect_uri" value="${htmlEsc(ru)}"/>
            <input type="hidden" name="state" value="${htmlEsc(st)}"/>
            <input type="hidden" name="method" value="${htmlEsc(method)}"/>
            $inputField
            <button type="submit" id="e2e-forgot-submit">Reset password</button>
            </form>"""
        return html(200, "forgot-password", b)
    }

    private fun forgotPasswordSubmit(body: String): MockResponse {
        val f = parseForm(body)
        val ru = f["redirect_uri"] ?: ""
        val st = f["state"] ?: ""
        val email = f["email"] ?: "test-forgot@frontegg.com"
        val resetToken = "reset-${UUID.randomUUID().toString().lowercase()}"
        return redir("/embedded/reset-password?token=${enc(resetToken)}&email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}")
    }

    private fun resetPasswordPage(q: Map<String, List<String>>): MockResponse {
        val email = fq(q, "email", "")
        val token = fq(q, "token", "")
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val complexity = state.getPasswordComplexity()
        val hint = when (complexity) {
            "hard" -> "Min 12 chars, uppercase, lowercase, digit, special char"
            "medium" -> "Min 8 chars, uppercase, digit"
            else -> "Min 6 chars"
        }
        val b = """<h1>Set new password</h1>
            <p id="e2e-complexity-hint">$hint</p>
            <form action="/embedded/reset-password/submit" method="post">
            <input type="hidden" name="token" value="${htmlEsc(token)}"/>
            <input type="hidden" name="email" value="${htmlEsc(email)}"/>
            <input type="hidden" name="redirect_uri" value="${htmlEsc(ru)}"/>
            <input type="hidden" name="state" value="${htmlEsc(st)}"/>
            <input type="password" id="e2e-new-password" name="password" placeholder="New password"/>
            <button type="submit" id="e2e-reset-submit">Set password</button>
            </form>"""
        return html(200, "reset-password", b)
    }

    private fun resetPasswordSubmit(body: String): MockResponse {
        val f = parseForm(body)
        val pw = f["password"] ?: ""
        @Suppress("UNUSED_VARIABLE") val email = f["email"] ?: ""
        val ru = f["redirect_uri"] ?: ""
        val st = f["state"] ?: ""
        val complexity = state.getPasswordComplexity()
        val error = validatePasswordComplexity(pw, complexity)
        if (error != null) {
            return html(200, "error", """<h1>Error</h1><p id="e2e-error">$error</p>
                <a href="javascript:history.back()">Try again</a>""")
        }
        if (state.isBreachedPassword(pw)) {
            return html(200, "error", """<h1>Error</h1><p id="e2e-error">Password has been breached</p>
                <a href="javascript:history.back()">Try again</a>""")
        }
        return html(200, "success", """<h1>Password reset</h1><p id="e2e-success">Password changed successfully</p>
            <script>setTimeout(function(){ window.location.href='/oauth/prelogin?redirect_uri=${enc(ru)}&state=${enc(st)}'; }, 2000);</script>""")
    }

    private fun validatePasswordComplexity(pw: String, complexity: String): String? {
        return when (complexity) {
            "hard" -> {
                if (pw.length < 12) return "Password must be at least 12 characters"
                if (!pw.any { it.isUpperCase() }) return "Password must contain an uppercase letter"
                if (!pw.any { it.isLowerCase() }) return "Password must contain a lowercase letter"
                if (!pw.any { it.isDigit() }) return "Password must contain a digit"
                if (!pw.any { !it.isLetterOrDigit() }) return "Password must contain a special character"
                null
            }
            "medium" -> {
                if (pw.length < 8) return "Password must be at least 8 characters"
                if (!pw.any { it.isUpperCase() }) return "Password must contain an uppercase letter"
                if (!pw.any { it.isDigit() }) return "Password must contain a digit"
                null
            }
            else -> {
                if (pw.length < 6) return "Password must be at least 6 characters"
                null
            }
        }
    }

    private fun passwordExpiringPage(q: Map<String, List<String>>): MockResponse {
        val email = fq(q, "email", "")
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val days = fq(q, "days", "5")
        val canRemind = fq(q, "can_remind", "true") == "true"
        val remindBtn = if (canRemind) {
            """<button type="button" id="e2e-remind-later" onclick="window.location.href='/embedded/password-expiring/skip?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}'">Remind me later</button>"""
        } else ""
        val b = """<h1>Password expiring</h1>
            <p id="e2e-expiry-message">Your password will expire in $days days</p>
            $remindBtn
            <button type="button" id="e2e-change-password" onclick="window.location.href='/embedded/reset-password?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}&token=expiry-reset'">Change password</button>"""
        return html(200, "password-expiring", b)
    }

    private fun passwordExpiringSkip(q: Map<String, List<String>>): MockResponse {
        val email = fq(q, "email", "")
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val authCode = state.issueCode(email, ru, st)
        return redir(cb(ru, authCode, st))
    }

    private fun passwordExpiredPage(q: Map<String, List<String>>): MockResponse {
        val email = fq(q, "email", "")
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val b = """<h1>Password expired</h1>
            <p id="e2e-expired-message">Your password has expired</p>
            <button type="button" id="e2e-change-password" onclick="window.location.href='/embedded/reset-password?email=${enc(email)}&redirect_uri=${enc(ru)}&state=${enc(st)}&token=expired-reset'">Change password</button>"""
        return html(200, "password-expired", b)
    }

    private fun confirmationPage(type: String, q: Map<String, List<String>>): MockResponse {
        val token = fq(q, "token", "")
        val ru = fq(q, "redirect_uri", "")
        val st = fq(q, "state", "")
        val (title, message) = when (type) {
            "activation" -> "Activate your account" to "Click below to activate your account"
            "invitation" -> "Accept invitation" to "You have been invited to join a new account"
            "unlock" -> "Unlock account" to "Click below to unlock your account"
            else -> "Confirm" to "Please confirm"
        }
        val b = """<h1>$title</h1>
            <p>$message</p>
            <form action="/embedded/confirm/submit" method="post">
            <input type="hidden" name="type" value="${htmlEsc(type)}"/>
            <input type="hidden" name="token" value="${htmlEsc(token)}"/>
            <input type="hidden" name="redirect_uri" value="${htmlEsc(ru)}"/>
            <input type="hidden" name="state" value="${htmlEsc(st)}"/>
            <button type="submit" id="e2e-confirm-submit">Confirm</button>
            </form>"""
        return html(200, type, b)
    }

    private fun confirmationSubmit(body: String): MockResponse {
        val f = parseForm(body)
        val type = f["type"] ?: "activation"
        val ru = f["redirect_uri"] ?: ""
        val st = f["state"] ?: ""
        val email = when (type) {
            "activation" -> "test-activation@frontegg.com"
            "invitation" -> "test-invitation@frontegg.com"
            "unlock" -> "test-unlock@frontegg.com"
            else -> "test@frontegg.com"
        }
        val authCode = state.issueCode(email, ru, st)
        return redir(cb(ru, authCode, st))
    }

    // ── End new route handlers ───────────────────────────────────────

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

    private var passwordComplexity: String = "easy"
    private val mfaConfig = mutableMapOf<String, String>() // email -> "authenticator"|"sms"
    private val loginMethods = mutableMapOf<String, String>() // email -> "magic-code"|"magic-link"|"sms"|"username"
    private val pwExpiration = mutableMapOf<String, Triple<Int, Boolean, Boolean>>() // email -> (daysLeft, canRemindLater, isExpired)
    private val accountLockConfig = mutableMapOf<String, Int>() // email -> maxAttempts
    private val failedAttempts = mutableMapOf<String, Int>() // email -> currentAttempts
    private val breachedPasswords = mutableSetOf<String>()
    private var tosRequired: Boolean = false
    private var emailVerificationRedirect: Boolean = false
    private var customLoginBox: Boolean = false
    private var magicCodeExpirationMs: Long = 300_000
    private var magicLinkExpirationMs: Long = 300_000
    private val pendingMagicCodes = mutableMapOf<String, Pair<String, Long>>() // email -> (code, createdAtMs)
    private val pendingMagicLinks = mutableMapOf<String, Pair<String, Long>>() // email -> (token, createdAtMs)

    fun reset() = lock.withLock {
        q.clear(); codes.clear(); hosted.clear(); latestH = null; done.clear(); pols.clear(); oauthErr = null; rts.clear()
        passwordComplexity = "easy"
        mfaConfig.clear(); loginMethods.clear(); pwExpiration.clear()
        accountLockConfig.clear(); failedAttempts.clear(); breachedPasswords.clear()
        tosRequired = false; emailVerificationRedirect = false; customLoginBox = false
        magicCodeExpirationMs = 300_000; magicLinkExpirationMs = 300_000
        pendingMagicCodes.clear(); pendingMagicLinks.clear()
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

    fun setPasswordComplexity(c: String) = lock.withLock { passwordComplexity = c }
    fun configureMfa(email: String, type: String) = lock.withLock { mfaConfig[email.lowercase()] = type }
    fun configureLoginMethod(email: String, method: String) = lock.withLock { loginMethods[email.lowercase()] = method }
    fun configurePasswordExpiration(email: String, daysLeft: Int, canRemindLater: Boolean) = lock.withLock {
        pwExpiration[email.lowercase()] = Triple(daysLeft, canRemindLater, daysLeft <= 0)
    }
    fun configureAccountLocking(email: String, maxAttempts: Int) = lock.withLock { accountLockConfig[email.lowercase()] = maxAttempts }
    fun addBreachedPassword(pw: String) = lock.withLock { breachedPasswords.add(pw) }
    fun setTosRequired(r: Boolean) = lock.withLock { tosRequired = r }
    fun setEmailVerificationRedirect(e: Boolean) = lock.withLock { emailVerificationRedirect = e }
    fun setCustomLoginBox(e: Boolean) = lock.withLock { customLoginBox = e }
    fun setMagicCodeExpiration(ms: Long) = lock.withLock { magicCodeExpirationMs = ms }
    fun setMagicLinkExpiration(ms: Long) = lock.withLock { magicLinkExpirationMs = ms }

    // Accessors for routing logic
    fun loginMethod(email: String): String? = lock.withLock { loginMethods[email.lowercase()] }
    fun mfaType(email: String): String? = lock.withLock { mfaConfig[email.lowercase()] }
    fun isAccountLocked(email: String): Boolean = lock.withLock {
        val max = accountLockConfig[email.lowercase()] ?: return@withLock false
        (failedAttempts[email.lowercase()] ?: 0) >= max
    }
    fun recordFailedAttempt(email: String): Int = lock.withLock {
        val count = (failedAttempts[email.lowercase()] ?: 0) + 1
        failedAttempts[email.lowercase()] = count
        count
    }
    fun isBreachedPassword(pw: String): Boolean = lock.withLock { pw in breachedPasswords }
    fun isTosRequired(): Boolean = lock.withLock { tosRequired }
    fun isEmailVerificationRedirect(): Boolean = lock.withLock { emailVerificationRedirect }
    fun isCustomLoginBox(): Boolean = lock.withLock { customLoginBox }
    fun passwordExpiration(email: String): Triple<Int, Boolean, Boolean>? = lock.withLock { pwExpiration[email.lowercase()] }
    fun getPasswordComplexity(): String = lock.withLock { passwordComplexity }

    fun issueMagicCode(email: String): String = lock.withLock {
        val code = "123456"
        pendingMagicCodes[email.lowercase()] = code to System.currentTimeMillis()
        code
    }
    fun verifyMagicCode(email: String, code: String): Boolean = lock.withLock {
        val pending = pendingMagicCodes[email.lowercase()] ?: return@withLock false
        if (System.currentTimeMillis() - pending.second > magicCodeExpirationMs) return@withLock false
        pending.first == code
    }
    fun isMagicCodeExpired(email: String): Boolean = lock.withLock {
        val pending = pendingMagicCodes[email.lowercase()] ?: return@withLock true
        System.currentTimeMillis() - pending.second > magicCodeExpirationMs
    }
    fun issueMagicLink(email: String): String = lock.withLock {
        val token = "magic-link-${UUID.randomUUID().toString().lowercase()}"
        pendingMagicLinks[email.lowercase()] = token to System.currentTimeMillis()
        token
    }
    fun verifyMagicLink(token: String): String? = lock.withLock {
        for ((email, pair) in pendingMagicLinks) {
            if (pair.first == token) {
                if (System.currentTimeMillis() - pair.second > magicLinkExpirationMs) return@withLock null
                return@withLock email
            }
        }
        null
    }

    private fun norm(p: String) = if (p.isEmpty() || p == "/") p else if (p.startsWith("/")) p else "/$p"
}
