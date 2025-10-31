package com.frontegg.demo.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.frontegg.android.fronteggAuth
import com.frontegg.demo.databinding.FragmentHomeBinding
import java.util.Timer
import kotlin.time.DurationUnit
import kotlin.time.toDuration


class HomeFragment : Fragment() {
    private var messageTimer = Timer()

    // Binding variable for fragment's views, nullable to handle lifecycle properly
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    // It gives access to the views from the fragment's layout.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Initialize the ViewModel associated with this fragment
        val homeViewModel: HomeViewModel by viewModels { HomeFragmentFactory(requireContext().fronteggAuth) }

        // Inflate the layout for the fragment using the generated binding class
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        // Get the root view from the binding to return it to the parent container
        val root: View = binding.root

        // Observe the user data from the ViewModel to update the UI with user info
        homeViewModel.user.observe(viewLifecycleOwner) { user ->
            // When the user data changes, update the UI with profile picture, name, email, and active tenant
            if (user != null) {
                binding.helloText.text = "Hello, ${user.name.split(" ")[0]}!"
                binding.userInfo.apply {
                    // Load profile image
                    Glide.with(requireContext()).load(user.profilePictureUrl)
                        .into(userProfileImage) // Load the profile picture using Glide

                    // Set user name
                    userNameText.text = user.name

                    // Set user details
                    userFullNameText.text = user.name
                    userEmailText.text = user.email
                    userRolesText.text = if (user.roles.isNotEmpty()) {
                        user.roles.joinToString(", ") { it.name }
                    } else {
                        "No roles assigned"
                    }
                }

                binding.tenantInfo.apply {
                    // Set up tenant dropdown
                    val adapter = TenantAdapter(requireContext(), user.tenants, user.activeTenant)
                    tenantDropdownText.setAdapter(adapter)
                    tenantDropdownText.setText(user.activeTenant.name, false)

                    // Set tenant details
                    tenantIdText.text = user.activeTenant.id
                    tenantWebsiteText.text = user.activeTenant.website ?: "No website"
                    tenantCreatorText.text = user.activeTenant.creatorName ?: "Unknown"

                    // Set up copy button
                    copyTenantIdButton.setOnClickListener {
                        val clipboard =
                            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Tenant ID", user.activeTenant.id)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(
                            requireContext(),
                            "Tenant ID was copied to clipboard",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    // Handle tenant selection
                    tenantDropdownText.setOnItemClickListener { _, _, position, _ ->
                        val selectedTenant = user.tenants[position]
                        if (selectedTenant != user.activeTenant) {
                            // Call your tenant switching function
                            requireContext().fronteggAuth.switchTenant(selectedTenant.tenantId)
                        }
                    }
                }
            }
        }


        // Set up the step-up button functionality
        binding.sensitiveActionButton.setOnClickListener {
            val maxAge = 60.toDuration(DurationUnit.MINUTES)

            val isSteppedUp = requireContext().fronteggAuth.isSteppedUp(maxAge)
            if (isSteppedUp) {
                showSuccessMessage("Already stepped up, no need to do it again")
                return@setOnClickListener
            }
            activity?.let { activity ->
                requireContext().fronteggAuth.stepUp(
                    activity,
                    maxAge,
                )
                { exception ->
                    Log.d(TAG, "Should show success message? : $exception")
                    if (exception != null) {
                        showMessageOnResume = "Action completed with failure"
                        isShowMessageOnResumeError = true
                    } else {
                        showMessageOnResume = "Action completed successfully"
                        isShowMessageOnResumeError = false
                    }
                }
            }
        }

        binding.toolbar.btnLogout.visibility = View.VISIBLE

        binding.toolbar.btnLogout.setOnClickListener {
            requireContext().fronteggAuth.logout {
                Log.d(TAG, "Logout successful")
            }
        }

        binding.receiveTokenButton.setOnClickListener {
            val token = requireContext().fronteggAuth.accessToken.value
            if (token.isNullOrEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Access token is not available yet",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val clipboard =
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Access Token", token)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    requireContext(),
                    "Access token was copied to clipboard",
                    Toast.LENGTH_SHORT
                ).show()
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

        val isDefaultCredentials =
            requireContext().fronteggAuth.baseUrl == "https://autheu.davidantoon.me"
        Log.d(TAG, "isDefaultCredentials: $isDefaultCredentials")

        if (isDefaultCredentials) {
            binding.footer.signUpBanner.visibility = View.VISIBLE
        } else {
            Log.d(TAG, "hiding signUpBanner")
            binding.footer.signUpBanner.visibility = View.GONE
        }

        return root
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "onResume: HomeFragment, msg: $showMessageOnResume")
        if (showMessageOnResume != null) {
            if (isShowMessageOnResumeError) {
                showErrorMessage(showMessageOnResume!!)
            } else {
                showSuccessMessage(showMessageOnResume!!)
            }
            showMessageOnResume = null
        }
    }

    override fun onPause() {
        super.onPause()
        messageTimer.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Set the binding to null to avoid memory leaks
        _binding = null
    }

    private fun showSuccessMessage(message: String) =
        showMessage(
            message,
            com.frontegg.demo.R.color.greenForeground,
            com.frontegg.demo.R.color.greenBackground,
            com.frontegg.demo.R.drawable.ic_check_circle
        )

    private fun showErrorMessage(message: String) =
        showMessage(
            message,
            com.frontegg.demo.R.color.redForeground,
            com.frontegg.demo.R.color.redBackground,
            com.frontegg.demo.R.drawable.ic_error
        )


    private fun showMessage(
        message: String,
        foregroundColor: Int,
        backgroundColor: Int,
        icon: Int
    ) {
        Log.d(TAG, "showSuccessMessage: $message")
        binding.messageText.text = message
        binding.messageText.setTextColor(
            resources.getColor(
                foregroundColor,
                null
            )
        )

        binding.messageImage.setImageResource(icon)
        binding.messageImage.setColorFilter(
            resources.getColor(
                foregroundColor,
                null
            ), android.graphics.PorterDuff.Mode.SRC_IN
        )

        binding.messageCard.setCardBackgroundColor(
            resources.getColor(
                backgroundColor,
                null
            )
        )

        binding.messageCard.visibility = View.VISIBLE

        messageTimer.cancel()
        messageTimer = Timer()
        messageTimer.schedule(
            object : java.util.TimerTask() {
                override fun run() {
                    activity?.runOnUiThread {
                        binding.messageCard.visibility = View.GONE
                    }
                }
            },
            10_000
        )
    }

    companion object {
        private val TAG = HomeFragment::class.java.simpleName

        private var showMessageOnResume: String? = null
        private var isShowMessageOnResumeError: Boolean = false
    }
}
