package com.frontegg.demo.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.frontegg.android.models.Tenant
import com.frontegg.demo.R
import com.frontegg.demo.createTextDrawable
import com.google.android.material.imageview.ShapeableImageView

class TenantAdapter(
    context: Context,
    private val tenants: List<Tenant>,
    private val activeTenant: Tenant
) : ArrayAdapter<String>(context, R.layout.item_tenant_dropdown, tenants.map { it.name }) {


    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_tenant_dropdown, parent, false)

        val tenant = tenants[position]
        val tenantName = view.findViewById<TextView>(R.id.tenantName)
        tenantName.text = tenant.name

        // Set avatar with first letter
        val avatarView = view.findViewById<ShapeableImageView>(R.id.tenantAvatar)
        avatarView.setBackgroundColor(ContextCompat.getColor(context, R.color.gray))
        tenant.name.firstOrNull()?.toString()?.uppercase()?.let { firstLetter ->
            avatarView.setImageDrawable(createTextDrawable(firstLetter, context))
        }

        val checkIcon = view.findViewById<ImageView>(R.id.checkIcon)
        if (tenant == activeTenant) {
            checkIcon.visibility = View.VISIBLE
            tenantName.setTextColor(ContextCompat.getColor(context, R.color.primary))
        } else {
            checkIcon.visibility = View.GONE
            tenantName.setTextColor(ContextCompat.getColor(context, R.color.text))
        }
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }
}