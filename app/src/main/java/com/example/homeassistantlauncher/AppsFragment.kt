package com.example.homeassistantlauncher

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsFragment : Fragment() {

    private lateinit var recyclerViewApps: RecyclerView
    private lateinit var appAdapter: AppAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    @SuppressLint("NotifyDataSetChanged", "InlinedApi")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_apps, container, false)

        recyclerViewApps = view.findViewById(R.id.recycler_view_apps)
        recyclerViewApps.layoutManager = GridLayoutManager(requireContext(), 4)
        appAdapter = AppAdapter(emptyList()) { packageName ->
            val launchIntent = requireActivity().packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
        }
        recyclerViewApps.adapter = appAdapter

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        swipeRefreshLayout.setOnRefreshListener {
            lifecycleScope.launch {
                val appData = loadAppsInBackground()
                withContext(Dispatchers.Main) {
                    appAdapter.appData = appData
                    appAdapter.notifyDataSetChanged()
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.QUERY_ALL_PACKAGES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.QUERY_ALL_PACKAGES), 123)
        } else {
            lifecycleScope.launch {
                val appData = loadAppsInBackground()
                withContext(Dispatchers.Main) {
                    appAdapter.appData = appData
                    appAdapter.notifyDataSetChanged()
                }
            }
        }

        return view
    }

    @SuppressLint("NotifyDataSetChanged")
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 123) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                lifecycleScope.launch {
                    val appData = loadAppsInBackground()
                    withContext(Dispatchers.Main) {
                        appAdapter.appData = appData
                        appAdapter.notifyDataSetChanged()
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }


    @SuppressLint("QueryPermissionsNeeded")
    private suspend fun loadAppsInBackground(): List<AppData> {
        return withContext(Dispatchers.IO) {
            val packageManager = requireActivity().packageManager
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            apps.filter { app ->
                packageManager.getLaunchIntentForPackage(app.packageName) != null &&
                        app.packageName != context?.packageName
            }.map { app ->
                AppData(
                    app.loadLabel(packageManager).toString(),
                    app.loadIcon(packageManager),
                    app.packageName
                )
            }.sortedBy { it.name }
        }
    }
}

data class AppData(val name: String, val icon: Drawable, val packageName: String)

class AppAdapter(var appData: List<AppData>, private val onAppClick: (String) -> Unit) :
    RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.app_icon)
        val nameView: TextView = itemView.findViewById(R.id.app_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = appData[position]
        holder.iconView.setImageDrawable(app.icon)
        holder.nameView.text = app.name
        holder.itemView.setOnClickListener { onAppClick(app.packageName) }
    }

    override fun getItemCount(): Int = appData.size
}