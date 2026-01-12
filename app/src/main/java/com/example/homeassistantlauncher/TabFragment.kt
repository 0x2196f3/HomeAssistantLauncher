package com.example.homeassistantlauncher

import android.annotation.SuppressLint
import android.graphics.Bitmap
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

class TabFragment : Fragment(), FragmentOnBackPressed {

    private var _binding: FragmentTabBinding? = null
    private val binding get() = _binding!!

    private lateinit var currentUrl: String

    companion object {
        private const val ARG_URL = "arg_url"

        fun newInstance(url: String): TabFragment {
            val fragment = TabFragment()
            val args = Bundle().apply {
                putString(ARG_URL, url)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentUrl = savedInstanceState?.getString(ARG_URL)
            ?: arguments?.getString(ARG_URL)
                    ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (currentUrl.isEmpty()) {
            Toast.makeText(context, "Error: Empty URL", Toast.LENGTH_SHORT).show()
            return
        }

        setupWebView()
        setupSwipeRefresh()

        if (binding.webView.url == null) {
            binding.webView.loadUrl(currentUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    _binding?.swipeRefreshLayout?.isRefreshing = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    _binding?.swipeRefreshLayout?.isRefreshing = false
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame && _binding != null) {
                        Toast.makeText(context, "Error: ${error.description}", Toast.LENGTH_LONG)
                            .show()
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                }
            }

            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                _binding?.swipeRefreshLayout?.isEnabled = scrollY == 0
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.webView.loadUrl(currentUrl)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ARG_URL, currentUrl)
        _binding?.webView?.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        _binding?.webView?.onResume()
    }

    override fun onPause() {
        _binding?.webView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        _binding?.webView?.let { webView ->
            webView.stopLoading()
            webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
            webView.clearHistory()

            (webView.parent as? ViewGroup)?.removeView(webView)

            webView.destroy()
        }
        _binding = null
        super.onDestroyView()
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