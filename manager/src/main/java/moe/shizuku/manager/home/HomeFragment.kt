package moe.shizuku.manager.home

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import moe.shizuku.manager.R
import moe.shizuku.manager.management.appsViewModel
import rikka.lifecycle.viewModels
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.addItemSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.widget.borderview.BorderRecyclerView

/**
 * Home tab fragment — Shizuku status and ADB/root start cards.
 */
class HomeFragment : Fragment() {

    private val homeModel by viewModels { HomeViewModel() }
    private val appsModel by appsViewModel()
    private val adapter by lazy { HomeAdapter(homeModel, appsModel) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        homeModel.serviceStatus.observe(viewLifecycleOwner) { adapter.updateData() }
        appsModel.grantedCount.observe(viewLifecycleOwner) { adapter.updateData() }

        val recyclerView = view.findViewById<BorderRecyclerView>(android.R.id.list)
        val gridLayoutManager = GridLayoutManager(requireContext(), 2)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (adapter.getItemId(position)) {
                    HomeAdapter.ID_STATUS,
                    HomeAdapter.ID_ADB_PERMISSION_LIMITED,
                    HomeAdapter.ID_START_ROOT,
                    HomeAdapter.ID_START_WADB,
                    HomeAdapter.ID_START_ADB,
                    HomeAdapter.ID_MAGISK_ROOT,
                    HomeAdapter.ID_ROOT_HIDE,
                    HomeAdapter.ID_LEARN_MORE -> 2
                    else -> 1
                }
            }
        }
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()
        recyclerView.addItemSpacing(top = 3f, bottom = 3f, left = 3f, right = 3f, unit = TypedValue.COMPLEX_UNIT_DIP)
        recyclerView.addEdgeSpacing(top = 4f, bottom = 4f, left = 12f, right = 12f, unit = TypedValue.COMPLEX_UNIT_DIP)

        // ← ده اللي كان ناقص — بيطلب البيانات من Shizuku
        homeModel.reload()
        appsModel.load()
    }

    override fun onResume() {
        super.onResume()
        // Refresh every time we come back to home
        homeModel.reload()
    }

    fun scrollToTop() {
        view?.findViewById<BorderRecyclerView>(android.R.id.list)?.smoothScrollToPosition(0)
    }
}
