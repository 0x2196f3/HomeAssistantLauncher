package com.example.homeassistantlauncher

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        appAdapter = AppAdapter(emptyList(), { packageName ->
            val launchIntent = requireActivity().packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
        }, { packageName ->
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:${packageName}")
            startActivity(intent)
            loadApps()
        })
        recyclerViewApps.adapter = appAdapter

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        swipeRefreshLayout.setOnRefreshListener {
            loadApps()
        }
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.QUERY_ALL_PACKAGES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.QUERY_ALL_PACKAGES, android.Manifest.permission.DELETE_PACKAGES), 123)
        } else {
            loadApps()
        }

        return view
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadApps() {
        lifecycleScope.launch {
            val appData = loadAppsInBackground()
            appAdapter.appData = appData
            withContext(Dispatchers.Main) {
                appAdapter.notifyDataSetChanged()
                swipeRefreshLayout.isRefreshing = false
            }

        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 123) {
            if (grantResults.size >= 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
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

class AppAdapter(var appData: List<AppData>, private val onAppClick: (String) -> Unit, private val onAppUninstall: (String) -> Unit) :
    RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var isUninstallMode = false


    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.app_icon)
        val nameView: TextView = itemView.findViewById(R.id.app_name)
        val uninstallIcon: ImageView = itemView.findViewById(R.id.uninstall_icon)
        val appItemLayout: LinearLayout = itemView.findViewById(R.id.app_item_layout)
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
        holder.appItemLayout.setOnClickListener {
            if (isUninstallMode) {
                setUninstallMode(false)
            } else {
                onAppClick(app.packageName)
            }
        }

        holder.appItemLayout.setOnLongClickListener {

            setUninstallMode(true)
            true
        }

        holder.uninstallIcon.setOnClickListener {
            onAppUninstall(app.packageName)
            setUninstallMode(false)
        }

        if (isUninstallMode) {
            holder.uninstallIcon.visibility = View.VISIBLE
        } else {
            holder.uninstallIcon.visibility = View.GONE
        }

    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setUninstallMode(isUninstallMode: Boolean) {
        this.isUninstallMode = isUninstallMode
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = appData.size
}