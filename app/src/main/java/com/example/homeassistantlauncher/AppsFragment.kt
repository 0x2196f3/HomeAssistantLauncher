package com.example.homeassistantlauncher

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.homeassistantlauncher.databinding.FragmentAppsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsFragment : Fragment(), FragmentOnBackPressed {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppsViewModel by viewModels()
    private lateinit var appAdapter: AppAdapter
    private lateinit var uninstallAppLauncher: ActivityResultLauncher<Intent>

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                viewModel.loadInstalledApps(forceReload = true)
            } else {
                viewModel.signalPermissionDenied()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.permission_denied),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uninstallAppLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                viewModel.loadInstalledApps(forceReload = true)
                setUninstallModeInternal(false)
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions(forceReload = false)
    }

    override fun onDestroyView() {
        binding.recyclerViewApps.adapter = null
        _binding = null
        super.onDestroyView()
    }

    override fun onBackPressed(): Boolean {
        if (_binding == null) return false
        if (appAdapter.getUninstallMode()) {
            setUninstallModeInternal(false)
            return true
        }
        return false
    }


    private fun setUninstallModeInternal(enabled: Boolean) {
        appAdapter.setUninstallMode(enabled)
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter(
            onAppClick = { packageName, isUninstallMode ->
                if (isUninstallMode) {
                    setUninstallModeInternal(false)
                } else {
                    launchApp(packageName)
                }
            },
            onAppLongClick = {
                setUninstallModeInternal(!appAdapter.getUninstallMode())
            },
            onAppUninstall = { packageName ->
                requestUninstall(packageName)
            }
        )

        binding.recyclerViewApps.apply {
            layoutManager = GridLayoutManager(requireContext(), 6)
            adapter = appAdapter
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val childView = findChildViewUnder(event.x, event.y)
                    if (childView == null && appAdapter.getUninstallMode()) {
                        setUninstallModeInternal(false)
                        return@setOnTouchListener true
                    }
                }
                false
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            checkAndRequestPermissions(forceReload = true)
            setUninstallModeInternal(false)
        }
    }

    private fun observeViewModel() {
        viewModel.apps.observe(viewLifecycleOwner) { apps ->
            appAdapter.submitList(apps)
            if (apps.isEmpty() && appAdapter.getUninstallMode()) {
                setUninstallModeInternal(false)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }

        viewModel.permissionDeniedError.observe(viewLifecycleOwner) { hasError ->
            if (hasError) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.permission_denied),
                    Toast.LENGTH_LONG
                ).show()
                setUninstallModeInternal(false)
            }
        }
    }

    private fun checkAndRequestPermissions(forceReload: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val permission = android.Manifest.permission.QUERY_ALL_PACKAGES
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    permission
                ) == PackageManager.PERMISSION_GRANTED -> {
                    viewModel.loadInstalledApps(forceReload)
                }

                shouldShowRequestPermissionRationale(permission) -> {
                    requestPermissionLauncher.launch(permission)
                }

                else -> {
                    requestPermissionLauncher.launch(permission)
                }
            }
        } else {
            viewModel.loadInstalledApps(forceReload)
        }
    }

    private fun launchApp(packageName: String) {
        val launchIntent = requireActivity().packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            try {
                startActivity(launchIntent)
            } catch (_: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.launch_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(requireContext(), getString(R.string.launch_error), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun requestUninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = "package:$packageName".toUri()
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        try {
            uninstallAppLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "${getString(R.string.uninstall_error)}: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            setUninstallModeInternal(false)
        }
    }
}


data class AppData(
    val name: String,
    val icon: Drawable,
    val packageName: String
)

class AppAdapter(
    private val onAppClick: (packageName: String, isUninstallMode: Boolean) -> Unit,
    private val onAppLongClick: (packageName: String) -> Unit,
    private val onAppUninstall: (packageName: String) -> Unit
) : ListAdapter<AppData, AppAdapter.AppViewHolder>(AppDiffCallback()) {

    private var isUninstallMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position), isUninstallMode, onAppClick, onAppLongClick, onAppUninstall)
    }

    fun setUninstallMode(enabled: Boolean) {
        if (isUninstallMode != enabled) {
            isUninstallMode = enabled
            notifyDataSetChanged()
        }
    }

    fun getUninstallMode(): Boolean = isUninstallMode

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.app_icon)
        private val nameView: TextView = itemView.findViewById(R.id.app_name)
        private val uninstallIcon: ImageView = itemView.findViewById(R.id.uninstall_icon)
        private val appItemLayout: LinearLayout = itemView.findViewById(R.id.app_item_layout)

        fun bind(
            app: AppData,
            isUninstallMode: Boolean,
            onAppClick: (String, Boolean) -> Unit,
            onAppLongClick: (String) -> Unit,
            onAppUninstall: (String) -> Unit
        ) {
            iconView.setImageDrawable(app.icon)
            nameView.text = app.name

            appItemLayout.setOnClickListener { onAppClick(app.packageName, isUninstallMode) }
            appItemLayout.setOnLongClickListener {
                onAppLongClick(app.packageName)
                true
            }

            if (isUninstallMode) {
                uninstallIcon.visibility = View.VISIBLE
                uninstallIcon.setOnClickListener { onAppUninstall(app.packageName) }
            } else {
                uninstallIcon.visibility = View.GONE
                uninstallIcon.setOnClickListener(null)
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppData>() {
        override fun areItemsTheSame(oldItem: AppData, newItem: AppData): Boolean =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AppData, newItem: AppData): Boolean =
            oldItem == newItem
    }
}


class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val _apps = MutableLiveData<List<AppData>>()
    val apps: LiveData<List<AppData>> = _apps

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _permissionDeniedError = MutableLiveData<Boolean>()
    val permissionDeniedError: LiveData<Boolean> = _permissionDeniedError

    fun loadInstalledApps(forceReload: Boolean) {
        if (!forceReload && !apps.value.isNullOrEmpty()) {
            return
        }

        _isLoading.value = true
        _permissionDeniedError.value = false

        viewModelScope.launch {
            try {
                val appList = withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    val packageManager = context.packageManager
                    val currentPkg = context.packageName

                    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }

                    packageManager.queryIntentActivities(mainIntent, 0)
                        .asSequence()
                        .filter { it.activityInfo.packageName != currentPkg }
                        .mapNotNull { resolveInfo ->
                            try {
                                AppData(
                                    name = resolveInfo.loadLabel(packageManager).toString(),
                                    icon = resolveInfo.loadIcon(packageManager),
                                    packageName = resolveInfo.activityInfo.packageName
                                )
                            } catch (_: Exception) {
                                null
                            }
                        }
                        .sortedBy { it.name }
                        .toList()
                }
                _apps.postValue(appList)
            } catch (_: SecurityException) {
                _permissionDeniedError.postValue(true)
                _apps.postValue(emptyList())
            } catch (_: Exception) {
                _apps.postValue(emptyList())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun signalPermissionDenied() {
        _isLoading.value = false
        _permissionDeniedError.value = true
        _apps.value = emptyList()
    }
}