package com.example.homeassistantlauncher

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.homeassistantlauncher.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUIRED_FINGER_COUNT_FOR_INPUT_ENABLE = 4
        private const val INPUT_DISABLE_DELAY_MS = 300L
        private const val DEFAULT_STARTING_TAB_INDEX = 1
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewPagerAdapter: ViewPagerAdapter
    private lateinit var appSettings: AppSettings

    private var disableUserInputJob: Job? = null
    private var idleTimeoutMillis = 0L
    private var lastTouchTimestampMillis = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val previousCrashTime = intent.getLongExtra(BaseApplication.EXTRA_PREVIOUS_CRASH_TIME, 0L)
        if (previousCrashTime != 0L) {
            BaseApplication.previousCrashTimeFromIntent = previousCrashTime
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appSettings = AppSettings(this)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        configureFullScreenMode()

        setupViewPager()
        loadAndApplySettings()
        setupSystemBackButton()
    }

    private fun configureFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupViewPager() {
        viewPagerAdapter = ViewPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.adapter = viewPagerAdapter
        binding.viewPager.setUserInputEnabled(false)
    }

    private fun loadAndApplySettings() {
        val urls = appSettings.getUrls()
        idleTimeoutMillis = appSettings.getSwitchDelay().toLong() * 1000L

        viewPagerAdapter.addFragment(AppsFragment())
        urls.forEach { url ->
            viewPagerAdapter.addFragment(TabFragment.newInstance(url))
        }
        viewPagerAdapter.addFragment(SettingsFragment())

        if (viewPagerAdapter.itemCount > DEFAULT_STARTING_TAB_INDEX) {
            binding.viewPager.setCurrentItem(DEFAULT_STARTING_TAB_INDEX, false)
        } else if (viewPagerAdapter.itemCount > 0) {
            binding.viewPager.setCurrentItem(0, false)
        }
    }

    private fun setupSystemBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentPosition = binding.viewPager.currentItem
                val currentFragment = viewPagerAdapter.getFragment(currentPosition)

                if (!(currentFragment is FragmentOnBackPressed && currentFragment.onBackPressed())) {
                    if (viewPagerAdapter.itemCount > DEFAULT_STARTING_TAB_INDEX) {
                        if (currentPosition != DEFAULT_STARTING_TAB_INDEX) {
                            binding.viewPager.setCurrentItem(DEFAULT_STARTING_TAB_INDEX, true)
                        }
                    } else if (viewPagerAdapter.itemCount > 0) {
                        if (currentPosition != 0) {
                            binding.viewPager.setCurrentItem(0, true)
                        }
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (idleTimeoutMillis > 0 && !binding.viewPager.isUserInputEnabled && System.currentTimeMillis() - lastTouchTimestampMillis > idleTimeoutMillis
        ) {
            if (viewPagerAdapter.itemCount > DEFAULT_STARTING_TAB_INDEX) {
                binding.viewPager.setCurrentItem(DEFAULT_STARTING_TAB_INDEX, false)
            }
        }
        lastTouchTimestampMillis = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        disableUserInputJob?.cancel()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev ?: return super.dispatchTouchEvent(null)

        val currentPointerCount = ev.pointerCount
        val isUserInputCurrentlyEnabled = binding.viewPager.isUserInputEnabled

        if (currentPointerCount >= REQUIRED_FINGER_COUNT_FOR_INPUT_ENABLE && !isUserInputCurrentlyEnabled) {
            binding.viewPager.setUserInputEnabled(true)
            disableUserInputJob?.cancel()
        } else if (isUserInputCurrentlyEnabled && ev.actionMasked == MotionEvent.ACTION_UP) {
            scheduleDisableUserInput()
        }

        lastTouchTimestampMillis = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    private fun scheduleDisableUserInput() {
        disableUserInputJob?.cancel()
        disableUserInputJob = lifecycleScope.launch {
            delay(INPUT_DISABLE_DELAY_MS)
            binding.viewPager.setUserInputEnabled(false)
        }
    }
}

class ViewPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    private val fragments = mutableListOf<Fragment>()

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]

    fun addFragment(fragment: Fragment) {
        fragments.add(fragment)
        notifyItemInserted(fragments.size - 1)
    }

    fun getFragment(position: Int): Fragment? {
        return if (position >= 0 && position < fragments.size) {
            fragments[position]
        } else {
            null
        }
    }
}
