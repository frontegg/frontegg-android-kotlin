package com.frontegg.demo.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.frontegg.android.FronteggAuth
import com.frontegg.demo.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    // Binding variable for fragment's views, nullable to handle lifecycle properly
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    // It gives access to the views from the fragment's layout.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Initialize the ViewModel associated with this fragment
        val homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        // Inflate the layout for the fragment using the generated binding class
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        // Get the root view from the binding to return it to the parent container
        val root: View = binding.root

        // Set up logout button appearance
        binding.logoutButton.setBackgroundColor(Color.RED)

        // Observe the user data from the ViewModel to update the UI with user info
        homeViewModel.user.observe(viewLifecycleOwner) {
            // When the user data changes, update the UI with profile picture, name, email, and active tenant
            if (it != null) {
                Glide.with(requireContext()).load(it.profilePictureUrl)
                    .into(binding.image) // Load the profile picture using Glide
                binding.name.text = it.name
                binding.email.text = it.email
                binding.tenant.text = it.activeTenant.name
            }
        }

        // Set up the logout button functionality
        binding.logoutButton.setOnClickListener {
            FronteggAuth.instance.logout()
        }

        // Set up the register passkeys button functionality
        binding.registerPasskeysButton.setOnClickListener {
            FronteggAuth.instance.registerPasskeys(requireActivity()) {
                if (it == null) {
                    Toast.makeText(
                        requireContext(),
                        "Passkeys registered successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "ERROR: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Set the binding to null to avoid memory leaks
        _binding = null
    }
}
