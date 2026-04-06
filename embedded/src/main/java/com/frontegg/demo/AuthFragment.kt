package com.frontegg.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.frontegg.android.fronteggAuth
import com.frontegg.android.models.SocialLoginProvider
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.demo.databinding.FragmentAuthBinding
import kotlinx.coroutines.launch

class AuthFragment : Fragment() {

    companion object {
        /** Set by E2E "seed" button; consumed by Request Authorize flow. */
        var e2eRequestAuthorizeRefreshToken: String? = null
    }

    private var _binding: FragmentAuthBinding? = null

    /**
     * Binding property, only valid between onCreateView and onDestroyView.
     * This ensures safe access to UI elements within the fragment's lifecycle.
     */
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /**
         * Handles user login via the Embedded Frontegg WebView login dialog.
         * Organization (for Login per Account) is read from BuildConfig at SDK init via FronteggConstantsProvider.
         */
        binding.loginButton.setOnClickListener {
            requireContext().fronteggAuth.login(requireActivity()) {
                Log.d("AuthFragment", "Login callback")
            }
        }

        if (DemoEmbeddedTestMode.isEnabled) {
            binding.root.contentDescription = "LoginPageRoot"
            binding.e2eControls.visibility = View.VISIBLE

            binding.e2eEmbeddedPasswordButton.setOnClickListener {
                requireContext().fronteggAuth.login(
                    requireActivity(),
                    loginHint = DemoEmbeddedTestMode.EMBEDDED_PASSWORD_EMAIL,
                ) { Log.d("AuthFragment", "E2E password login") }
            }
            binding.e2eEmbeddedSamlButton.setOnClickListener {
                requireContext().fronteggAuth.login(
                    requireActivity(),
                    loginHint = DemoEmbeddedTestMode.EMBEDDED_SAML_EMAIL,
                ) { Log.d("AuthFragment", "E2E SAML login") }
            }
            binding.e2eEmbeddedOidcButton.setOnClickListener {
                requireContext().fronteggAuth.login(
                    requireActivity(),
                    loginHint = DemoEmbeddedTestMode.EMBEDDED_OIDC_EMAIL,
                ) { Log.d("AuthFragment", "E2E OIDC login") }
            }
            binding.e2eEmbeddedGoogleSocialButton.setOnClickListener {
                val act = requireActivity()
                requireContext().fronteggAuth.login(act) {
                    Log.d("AuthFragment", "E2E google: login opened")
                }
                // Mirror Swift: open embedded login then trigger Google social in WebView pipeline
                (requireContext().fronteggAuth as? FronteggAuthService)?.let { svc ->
                    lifecycleScope.launch {
                        svc.loginWithSocialLoginProvider(act, SocialLoginProvider.GOOGLE)
                    }
                }
            }
            binding.e2eSeedRequestAuthorizeButton.setOnClickListener {
                e2eRequestAuthorizeRefreshToken = DemoEmbeddedTestMode.REQUEST_AUTHORIZE_REFRESH_TOKEN
            }
            binding.e2eCustomSsoButton.setOnClickListener {
                val url = DemoEmbeddedTestMode.customSsoUrl(requireContext().fronteggAuth.baseUrl)
                requireContext().fronteggAuth.directLoginAction(requireActivity(), "direct", url) {
                    Log.d("AuthFragment", "E2E custom SSO")
                }
            }
            binding.e2eDirectSocialButton.setOnClickListener {
                val url = DemoEmbeddedTestMode.directSocialLoginUrl(requireContext().fronteggAuth.baseUrl)
                requireContext().fronteggAuth.directLoginAction(
                    requireActivity(),
                    "direct",
                    url,
                    callback = { Log.d("AuthFragment", "E2E direct social") },
                )
            }
        }

        /**
         * Handles login via Google using Frontegg's direct social login.
         * Opens a WebView login dialog for Google authentication.
         */
        binding.googleLoginButton.setOnClickListener {
            requireContext().fronteggAuth.directLoginAction(
                requireActivity(),
                "social-login",
                "google",
                callback = {
                    Log.d("AuthFragment", "Google Social Login Callback")
                },
            )
        }

        /**
         * Handles login via Apple ID using Frontegg's direct login method.
         * Opens an Apple authentication WebView.
         */
        binding.directAppleLoginButton.setOnClickListener {
            requireContext().fronteggAuth.directLoginAction(
                requireActivity(),
                "direct",
                "https://appleid.apple.com/auth/authorize?response_type=code&response_mode=form_post&redirect_uri=https%3A%2F%2Fauth.davidantoon.me%2Fidentity%2Fresources%2Fauth%2Fv2%2Fuser%2Fsso%2Fapple%2Fpostlogin&scope=openid+name+email&state=%7B%22oauthState%22%3A%22eyJGUk9OVEVHR19PQVVUSF9SRURJUkVDVF9BRlRFUl9MT0dJTiI6ImNvbS5mcm9udGVnZy5kZW1vOi8vYXV0aC5kYXZpZGFudG9vbi5tZS9pb3Mvb2F1dGgvY2FsbGJhY2siLCJGUk9OVEVHR19PQVVUSF9TVEFURV9BRlRFUl9MT0dJTiI6IjQ1MDVkMzljLTg0ZTctNDhiZi1hMzY3LTVmMjhmMmZlMWU1YiJ9%22%2C%22provider%22%3A%22apple%22%2C%22appId%22%3A%22%22%2C%22action%22%3A%22login%22%7D&client_id=com.frontegg.demo.client",
                callback = {
                    Log.d("AuthFragment", "Direct Apple Login Callback")
                },
            )
        }

        /**
         * Handles login via a custom social login provider.
         * Opens a WebView login dialog for the configured provider.
         */
        binding.customSocialLogin.setOnClickListener {
            requireContext().fronteggAuth.directLoginAction(
                requireActivity(),
                "custom-social-login",
                "6fbe9b2d-bfce-4804-aa4b-a1503db588ae",
                callback = {
                    Log.d("AuthFragment", "Custom Social Login Callback")
                },
            )
        }

        /**
         * Requests authorization using refresh tokens.
         * Tokens must be obtained from Frontegg identity-server APIs (e.g. POST
         * /frontegg/identity/resources/users/v1/signUp). Optionally, a device token
         * cookie can be provided for additional security.
         */
        binding.requestAuthorizedWithTokensButton.setOnClickListener {
            val rt = if (DemoEmbeddedTestMode.isEnabled) {
                e2eRequestAuthorizeRefreshToken ?: DemoEmbeddedTestMode.REQUEST_AUTHORIZE_REFRESH_TOKEN
            } else {
                "f3291a85-7cfd-4319-9e24-fab68d3eba1f"
            }
            requireContext().fronteggAuth.requestAuthorize(
                refreshToken = rt,
                deviceTokenCookie = if (DemoEmbeddedTestMode.isEnabled) null else "45dee82f-154e-4430-9fbf-951700f77e14"
            ) { result ->
                result.onSuccess { user ->
                    Log.d("FronteggAuth", "User authorized: ${user.name}")
                }.onFailure { error ->
                    Log.e("FronteggAuth", "Authorization failed: ${error.message}")
                }
            }
        }

        /**
         * Handles login with a specific organization alias ("test").
         * Uses Login per Account flow so the user is routed to that org's login experience.
         */
        binding.loginWithOrganizationButton.setOnClickListener {
            requireContext().fronteggAuth.login(
                requireActivity(),
                organization = "test"
            ) {
                Log.d("AuthFragment", "Login with Organization callback")
            }
        }

        /**
         * Initiates authentication using Passkeys.
         * Passkeys allow for passwordless login using secure device authentication.
         */
        binding.passkeysButton.setOnClickListener {
            requireContext().fronteggAuth.loginWithPasskeys(
                requireActivity(),
                callback = {
                    Log.d("AuthFragment", "Passkeys Login Callback")
                },
            )
        }

        /**
         * Opens the Frontegg Android Kotlin documentation in the default browser.
         * This button is part of the footer and provides quick access to the official documentation.
         */
        binding.footer.docsButton.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://android-kotlin-guide.frontegg.com/#/")
            )
            startActivity(intent)
        }

        /**
         * Opens the Frontegg Android Kotlin GitHub repository in the default browser.
         * This button is part of the footer and provides quick access to the source code and issues.
         */
        binding.footer.githubButton.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/frontegg/frontegg-android-kotlin")
            )
            startActivity(intent)
        }

        /**
         * Opens the Slack OAuth authorization page in the default browser.
         * This button is part of the footer and allows users to connect with the Frontegg community on Slack.
         */
        binding.footer.slackButton.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://slack.com/oauth/authorize?client_id=1234567890.1234567890&scope=identity.basic,identity.email,identity.team,identity.avatar")
            )
            startActivity(intent)
        }

        /**
         * Opens the Frontegg sign-up page in the default browser.
         * This button is part of the footer's sign-up banner and allows users to create their own Frontegg account.
         */
        binding.footer.signUpButton.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://frontegg-prod.frontegg.com/oauth/account/sign-up")
            )
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Prevents memory leaks by nullifying the binding reference.
    }
}
