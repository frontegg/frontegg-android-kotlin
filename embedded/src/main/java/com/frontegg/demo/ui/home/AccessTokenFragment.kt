package com.frontegg.demo.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.frontegg.android.FronteggAuth
import com.frontegg.demo.databinding.FragmentAccessTokenBinding

class AccessTokenFragment : Fragment() {

    private var _binding: FragmentAccessTokenBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentAccessTokenBinding.inflate(inflater, container, false)

        val root: View = binding.root

        homeViewModel.user.observe(viewLifecycleOwner) {
            if (it != null) {

                binding.name.text = it.name
                binding.email.text = it.email
            }
        }

        homeViewModel.accessToken.observe(viewLifecycleOwner) {

            // this one will be disable in background
            // will resume on foreground
            Log.w("AccessTokenFragment", "Access token (viewModel): $it")
            binding.accessToken.text = it
        }

        return root
    }

    override fun onResume() {
        super.onResume()

        FronteggAuth.instance.accessToken.subscribe {
            Log.w("AccessTokenFragment", "Access token(subscribe): ${it.value}")
            Log.w("AccessTokenFragment", "Access token(direct): ${FronteggAuth.instance.accessToken.value}")
        };
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}