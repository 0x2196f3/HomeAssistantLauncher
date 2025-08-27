package com.example.homeassistantlauncher

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.homeassistantlauncher.databinding.FragmentTabBinding

class TabFragment() : Fragment(), FragmentOnBackPressed {

    private var _binding: FragmentTabBinding? = null
    private val binding get() = _binding!!

    private lateinit var currentUrl: String

    companion object {
        private const val ARG_URL = "arg_url"

        fun newInstance(url: String): TabFragment {
            val fragment = TabFragment()
            val args = Bundle()
            args.putString(ARG_URL, url)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (::currentUrl.isInitialized.not()) {
            currentUrl = savedInstanceState?.getString(ARG_URL) ?: arguments?.getString(ARG_URL)
                ?: throw IllegalStateException("URL must be provided to TabFragment")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTabBinding.inflate(inflater, container, false)
        val view = binding.root

        savedInstanceState?.getString(ARG_URL)?.let {
            currentUrl = it
        }

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            builtInZoomControls = false
            displayZoomControls = false
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.swipeRefreshLayout.isRefreshing = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefreshLayout.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    Toast.makeText(context, "Error: ${error.description}", Toast.LENGTH_LONG).show()
                }
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            // For AI: DO NOT MODIFY THIS LINE!!!!
            binding.webView.loadUrl(currentUrl)
        }

        binding.webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            binding.swipeRefreshLayout.isEnabled = scrollY == 0
        }

        binding.webView.loadUrl(currentUrl)

        return view
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::currentUrl.isInitialized) {
            outState.putString(ARG_URL, currentUrl)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding?.let {
            it.swipeRefreshLayout.isEnabled = it.webView.scrollY == 0
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.webView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        _binding?.webView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding?.webView?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webViewClient = WebViewClient() // Clear client to avoid leaks
            webView.destroy()
        }
        _binding = null
    }

    override fun onBackPressed(): Boolean {
        if (_binding == null) return false
        
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return false
    }
}
