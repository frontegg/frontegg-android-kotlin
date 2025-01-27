package com.frontegg.demo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.frontegg.android.FronteggAuth
import com.frontegg.demo.databinding.FragmentAuthBinding
import io.reactivex.rxjava3.disposables.Disposable

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val disposables: ArrayList<Disposable> = arrayListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentAuthBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // binding.loginButton.setOnClickListener {
        //     FronteggAuth.instance.login(requireActivity()) {
        //         Log.d("AuthFragment", "Login callback")
        //     }
        // }

        binding.loginButton.setOnClickListener {
            Log.d("AuthFragment", "Login button clicked")
            FronteggAuth.instance.requestAuthorize(
                refreshToken = "bca924ae-678a-4d84-9362-36d1bb8b33b0",
                deviceTokenCookie = "45dee82f-154e-4430-9fbf-951700f77e14"
            ) { result ->
                result.onSuccess { user ->
                    Log.d("FronteggAuth", "User authorized: ${user.name}")
                }.onFailure { error ->
                    Log.e("FronteggAuth", "Authorization failed: ${error.message}")
                }
            }
        }

        binding.googleLoginButton.setOnClickListener {
            FronteggAuth.instance.directLoginAction(requireActivity(), "social-login", "google", callback = {
                Log.d("AuthFragment", "Direct login action callback")
            })
        }

        binding.passkeysButton.setOnClickListener {
            FronteggAuth.instance.loginWithPasskeys(requireActivity(), callback = {
                Log.d("AuthFragment", "Direct login action callback")
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        disposables.forEach {
            it.dispose()
        }
        disposables.clear()
    }
}