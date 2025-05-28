package com.frontegg.demo

import android.content.Intent
import android.net.Uri
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
