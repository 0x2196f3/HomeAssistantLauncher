package com.example.homeassistantlauncher

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.homeassistantlauncher.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment(), FragmentOnBackPressed {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var appSettings: AppSettings
    private lateinit var urlsAdapter: UrlsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val view = binding.root

        appSettings = AppSettings(requireContext())

        setupSwitchDelaySpinner()
        setupUrlsRecyclerView()
        loadUrlsIntoAdapter()
        setupSaveButton()
        setupClearCookiesButton()
        setupSwipeRefresh()

        return view
    }

    private fun setupSwitchDelaySpinner() {
        val switchDelayEntries = resources.getStringArray(R.array.switch_delay_entries)
        val switchDelayValues = resources.getIntArray(R.array.switch_delay_values)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            switchDelayEntries
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.switchDelaySpinner.adapter = adapter

        val currentDelayValue = appSettings.getSwitchDelay()
        val currentSelectionIndex = switchDelayValues.indexOf(currentDelayValue)
        if (currentSelectionIndex != -1) {
            binding.switchDelaySpinner.setSelection(currentSelectionIndex)
        } else {
            binding.switchDelaySpinner.setSelection(switchDelayValues.indexOf(AppSettings.DEFAULT_SWITCH_DELAY_VALUE).coerceAtLeast(0))
        }

        binding.switchDelaySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedValue = switchDelayValues[position]
                appSettings.saveSwitchDelay(selectedValue)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupUrlsRecyclerView() {
        urlsAdapter = UrlsAdapter(
            onAddNewClicked = {
                val currentList = urlsAdapter.getUrls().toMutableList()
                currentList.add("")
                urlsAdapter.setUrls(currentList)
            },
            onRemoveClicked = { position ->
                val currentList = urlsAdapter.getUrls().toMutableList()
                if (position < currentList.size) {
                    currentList.removeAt(position)
                    urlsAdapter.setUrls(currentList)
                }
            }
        )
        binding.recyclerViewUrls.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = urlsAdapter
        }
    }

    private fun loadUrlsIntoAdapter() {
        urlsAdapter.setUrls(appSettings.getUrls())
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {
            processAndSaveUrls()
        }
    }
    private fun processAndSaveUrls() {
        val urlsFromAdapter = urlsAdapter.getUrls().map { it.trim() }.filter { it.isNotEmpty() }

        val validUrlsToSave = mutableListOf<String>()
        val invalidLinesMessages = mutableListOf<String>()
        val firstOccurrenceIndices = mutableSetOf<String>()

        urlsFromAdapter.forEachIndexed { index, url ->
            if (firstOccurrenceIndices.contains(url)) {
                invalidLinesMessages.add("Line ${index + 1}: ${getString(R.string.duplicate_urls_found)}")
            } else {
                if (Patterns.WEB_URL.matcher(url).matches()) {
                    validUrlsToSave.add(url)
                    firstOccurrenceIndices.add(url)
                } else {
                    invalidLinesMessages.add("Line ${index + 1}: $url")
                }
            }
        }

        if (invalidLinesMessages.isEmpty()) {
            appSettings.saveUrls(validUrlsToSave)
            Toast.makeText(requireContext(), R.string.urls_saved_successfully, Toast.LENGTH_SHORT).show()
            restartApp()
        } else {
            val errorMessage = getString(R.string.invalid_urls_on_lines) + "\n" + invalidLinesMessages.joinToString("\n")
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupClearCookiesButton() {
        binding.buttonClearCookies.setOnClickListener {
            clearAllWebViewData()
        }
    }

    private fun clearAllWebViewData() {
        lifecycleScope.launch {
            var cookieClearSuccess = true

            withContext(Dispatchers.Main) {
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies { success ->
                    if (!success) {
                        cookieClearSuccess = false
                    }
                }

                WebStorage.getInstance().deleteAllData()

                val appContext = requireContext().applicationContext
                val tempWebView = WebView(appContext)
                tempWebView.clearCache(true)
                tempWebView.destroy()
            }

            val appContext = requireContext().applicationContext
            if (!cookieClearSuccess) {
                Toast.makeText(appContext, getString(R.string.error_clearing_cookies), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(appContext, getString(R.string.cookies_cleared_successfully), Toast.LENGTH_SHORT).show()
            }

            restartApp()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadUrlsIntoAdapter()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun restartApp() {
        val context = requireContext()
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent != null) {
            startActivity(intent)
            activity?.finish()
        } else {
            Toast.makeText(context, getString(R.string.error_restarting_app), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onBackPressed(): Boolean {
        return false
    }
}

class UrlsAdapter(
    private val onAddNewClicked: () -> Unit,
    private val onRemoveClicked: (Int) -> Unit
) : ListAdapter<String, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
        return false
    }

    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
        return oldItem == newItem
    }
}) {

    private val urlsList = mutableListOf<String>()

    companion object {
        private const val VIEW_TYPE_URL = 0
        private const val VIEW_TYPE_ADD = 1
    }

    fun setUrls(urls: List<String>) {
        urlsList.clear()
        urlsList.addAll(urls)
        submitList(urlsList.toList())
    }

    fun getUrls(): List<String> {
        return urlsList.toList()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < urlsList.size) VIEW_TYPE_URL else VIEW_TYPE_ADD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_URL) {
            val view = inflater.inflate(R.layout.list_item_url, parent, false)
            UrlViewHolder(view, onRemoveClicked, urlsList)
        } else {
            val view = inflater.inflate(R.layout.list_item_add_button, parent, false)
            AddButtonViewHolder(view, onAddNewClicked)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is UrlViewHolder) {
            holder.bind(urlsList[position], position + 1)
        }
    }

    override fun getItemCount(): Int {
        return urlsList.size + 1
    }

    class UrlViewHolder(
        itemView: View,
        private val onRemoveClicked: (Int) -> Unit,
        private val urlsList: MutableList<String>
    ) : RecyclerView.ViewHolder(itemView) {
        private val lineNumberTextView: TextView = itemView.findViewById(R.id.text_view_line_number) // Add this
        private val editTextUrl: EditText = itemView.findViewById(R.id.edit_text_url_item)
        private val removeButton: Button = itemView.findViewById(R.id.button_remove_url_item)
        private var textWatcher: TextWatcher? = null

        @SuppressLint("SetTextI18n")
        fun bind(url: String, lineNumber: Int) {
            lineNumberTextView.text = "$lineNumber."
            textWatcher?.let { editTextUrl.removeTextChangedListener(it) }
            editTextUrl.setText(url)

            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val currentPosition = adapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        if (currentPosition < urlsList.size) {
                            urlsList[currentPosition] = s.toString()
                        }
                    }
                }
            }
            editTextUrl.addTextChangedListener(textWatcher)

            removeButton.setOnClickListener {
                val currentPosition = adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onRemoveClicked(currentPosition)
                }
            }
        }
    }

    class AddButtonViewHolder(
        itemView: View,
        private val onAddNewClicked: () -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val addButton: Button = itemView.findViewById(R.id.button_add_new_url)

        init {
            addButton.setOnClickListener {
                onAddNewClicked()
            }
        }
    }
}
