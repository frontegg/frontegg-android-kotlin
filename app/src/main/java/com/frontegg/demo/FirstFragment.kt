package com.frontegg.demo

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.frontegg.android.FronteggAuth
import com.frontegg.demo.databinding.FragmentFirstBinding
import io.reactivex.rxjava3.disposables.Disposable

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val disposables: ArrayList<Disposable> = arrayListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.logoutButton.setOnClickListener {

            FronteggAuth.instance.logout()
        }

        binding.loginButton.setOnClickListener {

            FronteggAuth.instance.login(requireActivity())
        }

        disposables.add(FronteggAuth.instance.user.subscribe {
            activity?.runOnUiThread {
                binding.textviewFirst.text = it.value?.email
            }
        })
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