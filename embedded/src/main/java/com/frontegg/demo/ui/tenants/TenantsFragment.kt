package com.frontegg.demo.ui.tenants

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.frontegg.android.models.Tenant
import com.frontegg.demo.databinding.FragmentTenantsBinding

// Fragment for displaying and managing tenants
class TenantsFragment : Fragment() {

    // View binding instance (only valid between onCreateView and onDestroyView)
    private var _binding: FragmentTenantsBinding? = null
    private val binding get() = _binding!!

    // List to store tenant objects
    private var tenants: MutableList<Tenant> = mutableListOf()

    // ViewModel instance for managing tenant data
    private lateinit var tenantsViewModel: TenantsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Initialize ViewModel
        tenantsViewModel = ViewModelProvider(this)[TenantsViewModel::class.java]

        // Inflate the layout for this fragment using View Binding
        _binding = FragmentTenantsBinding.inflate(inflater, container, false)

        // Get reference to ListView and set up the adapter
        val listView: ListView = binding.tenantsList
        val adapter = TenantCustomAdapter(requireContext(), tenants)
        listView.adapter = adapter

        // Observe tenant list updates and refresh the adapter when changes occur
        tenantsViewModel.tenants.observe(viewLifecycleOwner) {
            adapter.setItems(it)
        }

        // Observe changes in the active tenant and update the adapter accordingly
        tenantsViewModel.activeTenant.observe(viewLifecycleOwner) {
            adapter.setActiveTenant(it)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null  // Clear binding reference to prevent memory leaks
    }
}
