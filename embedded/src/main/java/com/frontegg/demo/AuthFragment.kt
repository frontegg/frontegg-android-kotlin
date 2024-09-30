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

        binding.loginButton.setOnClickListener {
            FronteggAuth.instance.login(requireActivity())
        }

        binding.googleLoginButton.setOnClickListener {

            FronteggAuth.instance.directLoginAction(requireActivity(), "social-login", "google", callback = {
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