package foi.nloncar.IoTAndroidApp.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

data class PagerFragment(
    val title: String,
    val icon: Int,
    val fragmentClass: () -> Fragment
)

class ViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {

    val fragmentList = ArrayList<PagerFragment>()

    override fun getItemCount(): Int {
        return fragmentList.size
    }

    override fun createFragment(position: Int): Fragment {
        return fragmentList[position].fragmentClass()
    }

    fun addFragment(pagerFragment: PagerFragment) {
        fragmentList.add(pagerFragment)
    }
}