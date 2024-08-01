package foi.nloncar.IoTAndroidApp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import foi.nloncar.IoTAndroidApp.adapters.PagerFragment
import foi.nloncar.IoTAndroidApp.adapters.ViewPagerAdapter
import foi.nloncar.IoTAndroidApp.fragments.DataCollectionFragment

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabs)
        fillViewPager()
    }

    private fun fillViewPager() {
        val adapter = ViewPagerAdapter(supportFragmentManager, lifecycle)
        val pagerFragment =
            PagerFragment(
                "Prikupljanje podataka",
                R.drawable.baseline_cloud_upload_24
            ) { DataCollectionFragment() }

        adapter.addFragment(pagerFragment)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = adapter.fragmentList[position].title
            tab.setIcon(adapter.fragmentList[position].icon)
        }.attach()
    }
}




