package com.frontegg.demo.ui.tenants

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.Tenant
import com.frontegg.demo.R
import com.frontegg.demo.databinding.FragmentTenantsBinding

class CustomAdapter(context: Context, private val dataSource: MutableList<Tenant>) : BaseAdapter() {
    private val inflater: LayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    private var activeTenant: Tenant? = null
    private var switchingTenant: Tenant? = null


    override fun getCount(): Int = dataSource.size

    override fun getItem(position: Int): Any = dataSource[position]

    override fun getItemId(position: Int): Long = position.toLong()

    fun setItems(items: List<Tenant>) {
        this.dataSource.clear()
        this.dataSource.addAll(items.sortedBy { it.name })
        notifyDataSetChanged()
    }

    fun setActiveTenant(activeTenant: Tenant?) {
        this.activeTenant = activeTenant
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.tenant_row_item, parent, false)
        val name: TextView = view.findViewById(R.id.name)
        val info: TextView = view.findViewById(R.id.info)

        val item = getItem(position) as Tenant
        name.text = item.name

        view.setOnClickListener {
            activeTenant = null
            switchingTenant = item
            notifyDataSetChanged()
            FronteggAuth.instance.switchTenant(item.tenantId) {
                switchingTenant = null
                notifyDataSetChanged()
            }
        }
        if (activeTenant?.tenantId == item.tenantId) {
            info.text = " (active)"
            info.visibility = View.VISIBLE
        } else if (switchingTenant?.tenantId == item.tenantId) {
            info.text = " (switching...)"
            info.visibility = View.VISIBLE
        } else {
            info.text = ""
            info.visibility = View.GONE
        }

        return view
    }
}

class TenantsFragment : Fragment() {

    private var _binding: FragmentTenantsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var tenants: MutableList<Tenant> = mutableListOf()

    private lateinit var tenantsViewModel: TenantsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        tenantsViewModel = ViewModelProvider(this)[TenantsViewModel::class.java]

        _binding = FragmentTenantsBinding.inflate(inflater, container, false)

        val listView: ListView = binding.tenantsList
        val adapter = CustomAdapter(requireContext(), tenants)
        listView.adapter = adapter

        tenantsViewModel.tenants.observe(viewLifecycleOwner) {
            adapter.setItems(it)
        }

        tenantsViewModel.activeTenant.observe(viewLifecycleOwner) {
            adapter.setActiveTenant(it)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}