package com.frontegg.demo.ui.tenants

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.Tenant
import com.frontegg.demo.R

// Adapter class for displaying a list of tenants in a ListView
class TenantCustomAdapter(context: Context, private val dataSource: MutableList<Tenant>) : BaseAdapter() {

    // Layout inflater to create views from XML layout resources
    private val inflater: LayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    // Stores the currently active tenant
    private var activeTenant: Tenant? = null

    // Stores the tenant that is currently in the process of switching
    private var switchingTenant: Tenant? = null

    // Returns the number of items in the data source
    override fun getCount(): Int = dataSource.size

    // Returns the tenant object at the specified position
    override fun getItem(position: Int): Any = dataSource[position]

    // Returns the unique item ID for the given position (position itself in this case)
    override fun getItemId(position: Int): Long = position.toLong()

    // Updates the list of tenants and sorts them alphabetically
    fun setItems(items: List<Tenant>) {
        this.dataSource.clear()
        this.dataSource.addAll(items.sortedBy { it.name })
        notifyDataSetChanged()
    }

    // Sets the currently active tenant and refreshes the UI
    fun setActiveTenant(activeTenant: Tenant?) {
        this.activeTenant = activeTenant
        notifyDataSetChanged()
    }

    // Creates or reuses a view for each item in the ListView
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        // Reuse an existing view if available, otherwise inflate a new one
        val view = convertView ?: inflater.inflate(R.layout.tenant_row_item, parent, false)

        // Get references to UI components inside the item view
        val name: TextView = view.findViewById(R.id.name)
        val info: TextView = view.findViewById(R.id.info)

        // Get the tenant object for the current position
        val item = getItem(position) as Tenant
        name.text = item.name

        // Handle click events on the item (switching tenants)
        view.setOnClickListener {
            activeTenant = null  // Clear active tenant
            switchingTenant = item  // Set the switching tenant
            notifyDataSetChanged()  // Refresh UI

            // Call Frontegg API to switch to the selected tenant
            FronteggAuth.instance.switchTenant(item.tenantId) {
                switchingTenant = null  // Reset switching state after completion
                notifyDataSetChanged()
            }
        }

        // Update UI to indicate the status of the tenant (active or switching)
        when {
            activeTenant?.tenantId == item.tenantId -> {
                info.text = " (active)"
                info.visibility = View.VISIBLE
            }
            switchingTenant?.tenantId == item.tenantId -> {
                info.text = " (switching...)"
                info.visibility = View.VISIBLE
            }
            else -> {
                info.text = ""
                info.visibility = View.GONE
            }
        }

        return view
    }
}