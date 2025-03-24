package com.frontegg.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.frontegg.android.FronteggAuth
import com.frontegg.demo.databinding.FragmentAuthBinding

/**
 * Fragment responsible for handling authentication-related UI and logic.
 * This fragment manages the login button and authentication state using Frontegg SDK.
 */
class AuthFragment : Fragment() {

    // View binding instance for fragment_auth.xml layout
    // Null when view is destroyed
    private var _binding: FragmentAuthBinding? = null

    // Safe access to binding with validity check
    // Only valid between onCreateView and onDestroyView
    private val binding get() = _binding!!

    /**
     * Creates and inflates the fragment's view hierarchy.
     * Initializes view binding for the auth fragment layout.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Sets up view interactions after the view is created.
     * Configures the login button to initiate Frontegg authentication.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginButton.setOnClickListener {
            // Launch Frontegg's login flow using the current activity context
            FronteggAuth.instance.login(requireActivity())
        }
    }

    /**
     * Cleans up resources when the fragment's view is destroyed.
     * Prevents memory leaks by nullifying the view binding.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}