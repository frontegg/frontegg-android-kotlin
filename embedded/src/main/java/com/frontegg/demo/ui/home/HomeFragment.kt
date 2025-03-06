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
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        val root: View = binding.root

        binding.logoutButton.setBackgroundColor(Color.RED)
        homeViewModel.user.observe(viewLifecycleOwner) {

            if (it != null) {
                Glide.with(requireContext()).load(it.profilePictureUrl)
                    .into(binding.image)
                binding.name.text = it.name
                binding.email.text = it.email
                binding.tenant.text = it.activeTenant.name
            }
        }

        binding.logoutButton.setOnClickListener {
            FronteggAuth.instance.logout()
        }

        binding.stepUpButton.setOnClickListener {
            activity?.let { it1 ->
                val maxAge = 1.toDuration(DurationUnit.MINUTES)
                val isSteppedUp = FronteggAuth.instance.isSteppedUp(maxAge)
                if (!isSteppedUp) {
                    FronteggAuth.instance.stepUp(
                        activity = it1,
                        callback = { exception ->
                            if (exception != null) {
                                Toast.makeText(
                                    requireContext(),
                                    "ERROR: ${exception.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "SUCCESS",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        maxAge = maxAge,
                    )
                } else {
                    Toast.makeText(
                        requireContext(),
                        "No need step up right now!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

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
        _binding = null
    }
}