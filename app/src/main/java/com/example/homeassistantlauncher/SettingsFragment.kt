package com.example.homeassistantlauncher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.compose.ui.geometry.isEmpty
import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.text.isNotEmpty
import kotlin.text.trim


class SettingsFragment : Fragment() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        sharedPreferences = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)

        val switchDelaySpinner = view.findViewById<Spinner>(R.id.switch_delay_spinner)
        val editTextUrls = view.findViewById<EditText>(R.id.edit_text_urls)
        val buttonSave = view.findViewById<Button>(R.id.button_save)
        val swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout)
        val buttonClearCookies = view.findViewById<Button>(R.id.button_clear_cookies)

        val switchDelayEntries = resources.getStringArray(R.array.switch_delay_entries)
        val switchDelayValues = resources.getIntArray(R.array.switch_delay_values)
        switchDelaySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, switchDelayEntries).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        switchDelaySpinner.setSelection(switchDelayValues.indexOf(sharedPreferences.getInt("switch_delay", 0)))
        switchDelaySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedValue = switchDelayValues[position]
                sharedPreferences.edit().putInt("switch_delay", selectedValue).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        editTextUrls.setText(sharedPreferences.getString("urls", "") ?: "")

        buttonSave.setOnClickListener { saveUrls(editTextUrls) }

        swipeRefreshLayout.setOnRefreshListener {
            editTextUrls.setText(sharedPreferences.getString("urls", "") ?: "")
            swipeRefreshLayout.isRefreshing = false
        }

        buttonClearCookies.setOnClickListener { clearCookiesThoroughly() }

        return view
    }

    private fun saveUrls(editTextUrls: EditText) {
        val urls = editTextUrls.text.toString().trim()
        val lines = urls.lines()
        val invalidLines = mutableListOf<Int>()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isNotEmpty() && !Patterns.WEB_URL.matcher(line).matches()) {
                invalidLines.add(i + 1)
            }
        }
        if (invalidLines.isEmpty()) {
            sharedPreferences.edit().putString("urls", urls).apply()
            Toast.makeText(requireContext(), "URLs saved successfully", Toast.LENGTH_SHORT).show()
            restartApp()
        } else {
            val invalidLineMessage = "Invalid URLs on lines: ${invalidLines.joinToString(", ")}"
            Toast.makeText(requireContext(), invalidLineMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun clearCookiesThoroughly() {
        val webView = WebView(requireContext())
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        Toast.makeText(requireContext(), "Cookies cleared", Toast.LENGTH_SHORT).show()
        restartApp()
    }

    private fun restartApp() {
        val intent = requireActivity().packageManager.getLaunchIntentForPackage(requireActivity().packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent!!)
        requireActivity().finish()
    }
}