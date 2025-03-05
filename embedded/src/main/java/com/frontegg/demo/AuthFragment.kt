package com.frontegg.demo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.frontegg.android.FronteggAuth
import com.frontegg.demo.databinding.FragmentAuthBinding

class AuthFragment : Fragment() {

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
         */
        binding.loginButton.setOnClickListener {
            FronteggAuth.instance.login(requireActivity()) {
                Log.d("AuthFragment", "Login callback")
            }
        }

        /**
         * Handles login via Google using Frontegg's direct social login.
         * Opens a WebView login dialog for Google authentication.
         */
        binding.googleLoginButton.setOnClickListener {
            FronteggAuth.instance.directLoginAction(
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
            FronteggAuth.instance.directLoginAction(
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
            FronteggAuth.instance.directLoginAction(
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
         * Optionally, a device token cookie can be provided for additional security.
         */
        binding.requestAuthorizedWithTokensButton.setOnClickListener {
            FronteggAuth.instance.requestAuthorize(
                refreshToken = "f3291a85-7cfd-4319-9e24-fab68d3eba1f",
                deviceTokenCookie = "45dee82f-154e-4430-9fbf-951700f77e14"
            ) { result ->
                result.onSuccess { user ->
                    Log.d("FronteggAuth", "User authorized: ${user.name}")
                }.onFailure { error ->
                    Log.e("FronteggAuth", "Authorization failed: ${error.message}")
                }
            }
        }

        /**
         * Initiates authentication using Passkeys.
         * Passkeys allow for passwordless login using secure device authentication.
         */
        binding.passkeysButton.setOnClickListener {
            FronteggAuth.instance.loginWithPasskeys(
                requireActivity(),
                callback = {
                    Log.d("AuthFragment", "Passkeys Login Callback")
                },
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Prevents memory leaks by nullifying the binding reference.
    }
}
