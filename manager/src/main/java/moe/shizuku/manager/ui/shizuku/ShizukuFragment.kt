package moe.shizuku.manager.ui.shizuku

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import moe.shizuku.manager.R
import moe.shizuku.manager.home.HomeAdapter
import moe.shizuku.manager.home.HomeViewModel
import moe.shizuku.manager.management.appsViewModel
import rikka.lifecycle.viewModels
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.addItemSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.widget.borderview.BorderRecyclerView

/**
 * ShizukuFragment — واجهة Shizuku المنفصلة تماماً.
 * تعرض حالة Shizuku وأدوات ADB/Root لتشغيل الخدمة.
 * لا تعتمد على SuperuserFragment بأي شكل.
 */
class ShizukuFragment : Fragment() {

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
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()
        recyclerView.addItemSpacing(top = 3f, bottom = 3f, left = 3f, right = 3f, unit = TypedValue.COMPLEX_UNIT_DIP)
        recyclerView.addEdgeSpacing(top = 4f, bottom = 4f, left = 12f, right = 12f, unit = TypedValue.COMPLEX_UNIT_DIP)

        homeModel.reload()
        appsModel.load()
    }

    override fun onResume() {
        super.onResume()
        homeModel.reload()
    }

    fun scrollToTop() {
        view?.findViewById<BorderRecyclerView>(android.R.id.list)?.smoothScrollToPosition(0)
    }
}
