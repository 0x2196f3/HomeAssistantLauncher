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
import androidx.recyclerview.widget.LinearLayoutManager
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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appSettings = AppSettings(requireContext())

        setupSwitchDelaySpinner()
        setupUrlsRecyclerView()
        loadUrlsIntoAdapter()
        setupButtons()
        setupSwipeRefresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onBackPressed(): Boolean = false


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
        val currentSelectionIndex =
            switchDelayValues.indexOf(currentDelayValue).takeIf { it != -1 } ?: 0
        binding.switchDelaySpinner.setSelection(currentSelectionIndex)

        binding.switchDelaySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    appSettings.saveSwitchDelay(switchDelayValues[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun setupUrlsRecyclerView() {
        urlsAdapter = UrlsAdapter(
            onAddNewClicked = { urlsAdapter.addEmptyUrl() },
            onRemoveClicked = { position -> urlsAdapter.removeUrl(position) }
        )
        binding.recyclerViewUrls.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = urlsAdapter
            itemAnimator = null
        }
    }

    private fun loadUrlsIntoAdapter() {
        urlsAdapter.setUrls(appSettings.getUrls())
    }

    private fun setupButtons() {
        binding.buttonSave.setOnClickListener { processAndSaveUrls() }
        binding.buttonClearCookies.setOnClickListener { clearAllWebViewData() }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadUrlsIntoAdapter()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }


    private fun processAndSaveUrls() {
        val urlsFromAdapter = urlsAdapter.getUrls().map { it.trim() }.filter { it.isNotEmpty() }
        val validUrlsToSave = mutableListOf<String>()
        val invalidLinesMessages = mutableListOf<String>()
        val seenUrls = mutableSetOf<String>()

        urlsFromAdapter.forEachIndexed { index, url ->
            if (seenUrls.contains(url)) {
                invalidLinesMessages.add("Line ${index + 1}: ${getString(R.string.duplicate_urls_found)}")
            } else if (!Patterns.WEB_URL.matcher(url).matches()) {
                invalidLinesMessages.add("Line ${index + 1}: $url")
            } else {
                validUrlsToSave.add(url)
                seenUrls.add(url)
            }
        }

        if (invalidLinesMessages.isEmpty()) {
            appSettings.saveUrls(validUrlsToSave)
            Toast.makeText(requireContext(), R.string.urls_saved_successfully, Toast.LENGTH_SHORT)
                .show()
            restartApp()
        } else {
            val errorMessage =
                getString(R.string.invalid_urls_on_lines) + "\n" + invalidLinesMessages.joinToString(
                    "\n"
                )
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun clearAllWebViewData() {
        lifecycleScope.launch {
            var success = true
            withContext(Dispatchers.Main) {
                try {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.removeAllCookies { if (!it) success = false }
                    WebStorage.getInstance().deleteAllData()

                    val appContext = requireContext().applicationContext
                    WebView(appContext).apply {
                        clearCache(true)
                        destroy()
                    }
                } catch (_: Exception) {
                    success = false
                }
            }

            val msg =
                if (success) getString(R.string.cookies_cleared_successfully) else getString(R.string.error_clearing_cookies)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

            if (success) restartApp()
        }
    }

    private fun restartApp() {
        val context = requireContext()
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent != null) {
            startActivity(intent)
            activity?.finish()
        } else {
            Toast.makeText(context, getString(R.string.error_restarting_app), Toast.LENGTH_SHORT)
                .show()
        }
    }

}


class UrlsAdapter(
    private val onAddNewClicked: () -> Unit,
    private val onRemoveClicked: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val urlsList = mutableListOf<String>()

    companion object {
        private const val VIEW_TYPE_URL = 0
        private const val VIEW_TYPE_ADD = 1
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setUrls(urls: List<String>) {
        urlsList.clear()
        urlsList.addAll(urls)
        notifyDataSetChanged()
    }

    fun getUrls(): List<String> = urlsList.toList()

    fun addEmptyUrl() {
        urlsList.add("")
        notifyItemInserted(urlsList.size - 1)
    }

    fun removeUrl(position: Int) {
        if (position in urlsList.indices) {
            urlsList.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, urlsList.size - position + 1)
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (position < urlsList.size) VIEW_TYPE_URL else VIEW_TYPE_ADD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_URL) {
            UrlViewHolder(
                inflater.inflate(R.layout.list_item_url, parent, false),
                onRemoveClicked,
                urlsList
            )
        } else {
            AddButtonViewHolder(
                inflater.inflate(R.layout.list_item_add_button, parent, false),
                onAddNewClicked
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is UrlViewHolder) {
            holder.bind(urlsList[position], position + 1)
        }
    }

    override fun getItemCount(): Int = urlsList.size + 1

    class UrlViewHolder(
        itemView: View,
        private val onRemoveClicked: (Int) -> Unit,
        private val urlsList: MutableList<String>
    ) : RecyclerView.ViewHolder(itemView) {
        private val lineNumberTextView: TextView = itemView.findViewById(R.id.text_view_line_number)
        private val editTextUrl: EditText = itemView.findViewById(R.id.edit_text_url_item)
        private val removeButton: Button = itemView.findViewById(R.id.button_remove_url_item)

        init {
            editTextUrl.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION && pos < urlsList.size) {
                        urlsList[pos] = s.toString()
                    }
                }
            })

            removeButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onRemoveClicked(pos)
            }
        }

        @SuppressLint("SetTextI18n")
        fun bind(url: String, lineNumber: Int) {
            lineNumberTextView.text = "$lineNumber."
            if (editTextUrl.text.toString() != url) {
                editTextUrl.setText(url)
            }
        }
    }

    class AddButtonViewHolder(itemView: View, onAddNewClicked: () -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        init {
            itemView.findViewById<Button>(R.id.button_add_new_url)
                .setOnClickListener { onAddNewClicked() }
        }
    }
}
