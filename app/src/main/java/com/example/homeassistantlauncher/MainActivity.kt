package com.example.homeassistantlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2


class MainActivity : AppCompatActivity() {

    companion object {
        private const val FINGER_COUNT = 4
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: ViewPagerAdapter

    private var pendingRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        @Suppress("DEPRECATION") window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_main)
        viewPager = findViewById(R.id.view_pager)
        viewPager.setUserInputEnabled(false)

        adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val urlsString = sharedPreferences.getString("urls", "") ?: ""
        val urls = urlsString.lines().filter { it.isNotBlank() }

        for (url in urls) {
            addTab(TabFragment(url))
        }

        addTab(SettingsFragment())
        addTab(AppsFragment())
    }

    private fun addTab(fragment: Fragment) {
        adapter.addFragment(fragment)
        adapter.notifyItemInserted(adapter.itemCount - 1)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        if (viewPager.currentItem > 0) {
            viewPager.setCurrentItem(viewPager.currentItem - 1, true)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return super.dispatchTouchEvent(null)
        if (ev.pointerCount >= FINGER_COUNT && !viewPager.isUserInputEnabled) {
            viewPager.setUserInputEnabled(true)
        } else if (viewPager.isUserInputEnabled && ev.action == MotionEvent.ACTION_UP && ev.pointerCount < FINGER_COUNT) {
            viewPager.setCurrentItem(viewPager.currentItem, true)
            startDelayedTask()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun startDelayedTask() {
        pendingRunnable?.let { handler.removeCallbacks(it) }

        pendingRunnable = Runnable {
            viewPager.setUserInputEnabled(false)
        }
        handler.postDelayed(pendingRunnable!!, 300)
    }
}

class ViewPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    private val fragments = mutableListOf<Fragment>()

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]

    fun addFragment(fragment: Fragment) {
        fragments.add(fragment)
    }
}