package com.example.homeassistantlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class TabFragment(private val url: String) : Fragment() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tab, container, false)

        webView = view.findViewById(R.id.web_view)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)


        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        val swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {}
        }

        swipeRefreshLayout.setOnRefreshListener {
            webView.loadUrl(url)
            swipeRefreshLayout.isRefreshing = false
        }

        webView.setOnTouchListener { _, _ ->
            swipeRefreshLayout.isEnabled = !webView.canScrollVertically(-1)
            false
        }

        webView.loadUrl(url)

        return view
    }
}
