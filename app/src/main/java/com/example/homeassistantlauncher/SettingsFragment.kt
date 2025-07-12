package com.example.homeassistantlauncher

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.homeassistantlauncher.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var appSettings: AppSettings

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val view = binding.root

        appSettings = AppSettings(requireContext())

        setupSwitchDelaySpinner()
        loadUrlsIntoEditText()
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

    private fun loadUrlsIntoEditText() {
        val urlsList = appSettings.getUrls()
        binding.editTextUrls.setText(urlsList.joinToString("\n"))
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {
            processAndSaveUrls()
        }
    }

    private fun processAndSaveUrls() {
        val urlsInputString = binding.editTextUrls.text.toString()
        val linesFromInput = urlsInputString.lines()

        val validUrlsToSave = mutableListOf<String>()
        val invalidLinesNumbers = mutableListOf<Int>()

        for ((index, lineContent) in linesFromInput.withIndex()) {
            val trimmedLine = lineContent.trim()
            if (trimmedLine.isNotEmpty()) {
                if (Patterns.WEB_URL.matcher(trimmedLine).matches()) {
                    validUrlsToSave.add(trimmedLine)
                } else {
                    invalidLinesNumbers.add(index + 1)
                }
            }
        }

        if (invalidLinesNumbers.isEmpty()) {
            appSettings.saveUrls(validUrlsToSave)
            Toast.makeText(requireContext(), R.string.urls_saved_successfully, Toast.LENGTH_SHORT).show()
            restartApp()
        } else {
            val invalidLineMessage = getString(R.string.invalid_urls_on_lines) + invalidLinesNumbers.joinToString(", ")
            Toast.makeText(requireContext(), invalidLineMessage, Toast.LENGTH_LONG).show()
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
            loadUrlsIntoEditText()
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
}
