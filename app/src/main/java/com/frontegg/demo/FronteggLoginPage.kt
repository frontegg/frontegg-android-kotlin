package com.frontegg.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class FronteggLoginPage : AppCompatActivity() {

    lateinit var webView: FronteggWebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_frontegg_login_page)
        webView = findViewById(R.id.custom_webview)
    }


    override fun onResume() {
        super.onResume()
        val str = "<html>\n" +
                "<head>\n" +
                "  <script type='text/javascript'>\n" +
                "    window.contextOptions = {" +
                "        baseUrl: FronteggNative.getBaseUrl()," +
                "        clientId: FronteggNative.getClientId()" +
                "    };" +
                "    const HOSTED_LOGIN_VERIFIER_KEY = 'TESTING';\n" +
                "\n" +
                "\n" +
                "\n" +
                "    function createRandomString(length = 16) {\n" +
                "      let text = '';\n" +
                "      const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';\n" +
                "      for (let i = 0; i < length; i++) {\n" +
                "        text += possible.charAt(Math.floor(Math.random() * possible.length));\n" +
                "      }\n" +
                "      return text;\n" +
                "    }\n" +
                "\n" +
                "    async function generateCodeChallenge(codeVerifier) {\n" +
                "      const digest = await FronteggNative.digest(codeVerifier);\n" +
                "\n" +
                "      // @ts-ignore\n" +
                "      return btoa(String.fromCharCode(...new Uint8Array(digest)))\n" +
                "        .replace(/=/g, '')\n" +
                "        .replace(/\\+/g, '-')\n" +
                "        .replace(/\\//g, '_');\n" +
                "    }\n" +
                "\n" +
                "    async function requestHostedLoginAuthorize() {\n" +
                "      const nonce = createRandomString();\n" +
                "      const code_verifier = createRandomString();\n" +
                "      const code_challenge = await generateCodeChallenge(code_verifier);\n" +
                "\n" +
                "      // We are saving the verifier in session storage to be able to validate the response\n" +
                "      \n" +
                "      console.log('code_verifier', code_verifier);\n" +
                "      localStorage.setItem(HOSTED_LOGIN_VERIFIER_KEY, code_verifier);\n" +
                "      \n" +
                "\n" +
                "      const redirectUrl = `frontegg://oauth/callback`;\n" +
                "\n" +
                "      // Hard coded for now\n" +
                "      const oauthUrl = `\${window.contextOptions.baseUrl}/oauth/authorize`;\n" +
                "\n" +
                "\n" +
                "      const params = {\n" +
                "        response_type: 'code',\n" +
                "        client_id: window.contextOptions.clientId || 'INVALID-CLIENT-ID',\n" +
                "        scope: 'openid email profile',\n" +
                "        redirect_uri: redirectUrl,\n" +
                "        code_challenge: code_challenge,\n" +
                "        code_challenge_method: 'S256',\n" +
                "        nonce,\n" +
                "      };\n" +
                "\n" +
                "      const searchParams = new URLSearchParams(params);\n" +
                "      return `\${oauthUrl}?\${searchParams.toString()}`;\n" +
                "    }\n" +
                "\n" +
                "  //    console.log(\"Testing: 1\");\n" +
                "  //setTimeout(()=>{\n" +
                "          \n" +
                "          // console.log('first: ', localStorage)\n" +
                "    requestHostedLoginAuthorize().then(authorizeUrl => {\n" +
                "        \n" +
                "//console.log('first: ', localStorage.getItem(HOSTED_LOGIN_VERIFIER_KEY))-->\n" +
                "//        document.getElementById('code').innerText = authorizeUrl;-->\n" +
                "//        console.log(localStorage);-->\n" +
                "        \n" +
                "        \n" +
                "      window.location.href = authorizeUrl\n" +
                "      return false;\n" +
                "    }).catch(e => {\n" +
                "        console.error(e)\n" +
                "    });\n" +
                "      //}, 8000)\n" +
                "  </script>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "<!--<h1 id='code'></h1>-->\n" +
                "\n" +
                "</body>\n" +
                "\n" +
                "</html>\n"
        webView.loadDataWithBaseURL("frontegg://oauth/authenticate",str, "text/html", "utf-8", null);

//        webView.loadUrl("https://portal.frontegg.com/")
    }


}