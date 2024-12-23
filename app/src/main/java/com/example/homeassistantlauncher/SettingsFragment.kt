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
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.text.isNotEmpty
import kotlin.text.trim


class SettingsFragment : Fragment() {

    private lateinit var editTextUrls: EditText
    private lateinit var buttonSave: Button
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var buttonClearCookies: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        editTextUrls = view.findViewById(R.id.edit_text_urls)
        buttonSave = view.findViewById(R.id.button_save)

        sharedPreferences = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)

        loadUrls()

        buttonSave.setOnClickListener { saveUrls() }

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        buttonClearCookies = view.findViewById(R.id.button_clear_cookies)

        swipeRefreshLayout.setOnRefreshListener {
            loadUrls()
            swipeRefreshLayout.isRefreshing = false
        }

        buttonClearCookies.setOnClickListener { clearCookiesThoroughly() }
        return view
    }

    private fun loadUrls() {
        val urls = sharedPreferences.getString("urls", "") ?: ""
        editTextUrls.setText(urls)
    }

    private fun saveUrls() {
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
