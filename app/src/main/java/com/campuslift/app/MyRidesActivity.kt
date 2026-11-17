package com.campuslift.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.campuslift.app.data.AppDatabase
import com.campuslift.app.data.DemoStudent
import com.campuslift.app.data.MyBookingItem
import com.campuslift.app.data.RideState
import com.campuslift.app.data.ScheduleTime
import com.campuslift.app.databinding.ActivityMyRidesBinding
import com.campuslift.app.databinding.ItemRideBinding
import com.campuslift.app.ui.applySystemBars
import com.campuslift.app.ui.departureText
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The student's whole booking history in three tabs: Active, Completed and Cancelled.
 * Tapping a ride opens its Ride Details screen.
 */
class MyRidesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyRidesBinding
    private val db by lazy { AppDatabase.getInstance(this) }
    private val adapter = BookingAdapter { startActivity(RideDetailsActivity.intent(this, it.bookingId)) }
    private val tabs = listOf(RideState.ACTIVE, RideState.COMPLETED, RideState.CANCELLED)
    private var allBookings: List<MyBookingItem> = emptyList()
    private var rides: Map<RideState, List<MyBookingItem>> = emptyMap()
    private var lastToday: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyRidesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars(binding.root)
        binding.toolbar.setTitle(R.string.my_rides)
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationContentDescription(R.string.back)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rides.layoutManager = LinearLayoutManager(this)
        binding.rides.adapter = adapter
        binding.retry.setOnClickListener { load() }
        tabs.forEach { binding.tabs.addTab(binding.tabs.newTab().setText(tabName(it))) }
        binding.tabs.getTabAt(savedInstanceState?.getInt(TAB) ?: 0)?.select()
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showSelectedTab()
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(1000L)
                    if (allBookings.isNotEmpty()) refreshBuckets()
                }
            }
        }
    }

    // Reload on every return, because a ride may have been cancelled on the details screen.
    override fun onStart() { super.onStart(); load() }

    private fun load() {
        binding.progress.visibility = View.VISIBLE
        binding.retry.visibility = View.GONE
        lifecycleScope.launch {
            try {
                db.syncSeed(applicationContext)
                allBookings = db.dao().allBookings(DemoStudent.ID)
                refreshBuckets()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                binding.status.visibility = View.VISIBLE
                binding.status.setText(R.string.database_error)
                binding.retry.visibility = View.VISIBLE
            } finally { binding.progress.visibility = View.GONE }
        }
    }

    private fun refreshBuckets() {
        val now = ScheduleTime.now()
        val updated = tabs.associateWith { state ->
            val matching = allBookings.filter { now.stateOf(it) == state }
            if (state == RideState.ACTIVE) matching.reversed() else matching
        }
        val todayChanged = lastToday != null && lastToday != now.today
        if (updated != rides || lastToday != now.today) {
            rides = updated
            lastToday = now.today
            showSelectedTab()
            if (todayChanged) adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    private fun showSelectedTab() {
        tabs.forEachIndexed { index, state ->
            binding.tabs.getTabAt(index)?.text =
                getString(R.string.tab_with_count, getString(tabName(state)), rides[state].orEmpty().size)
        }
        val state = tabs[binding.tabs.selectedTabPosition.coerceAtLeast(0)]
        val items = rides[state].orEmpty()
        adapter.submit(items, state)
        binding.status.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.status.setText(when (state) {
            RideState.ACTIVE -> R.string.no_bookings
            RideState.COMPLETED -> R.string.no_completed
            RideState.CANCELLED -> R.string.no_cancelled
        })
    }

    private fun tabName(state: RideState) = when (state) {
        RideState.ACTIVE -> R.string.tab_active
        RideState.COMPLETED -> R.string.tab_completed
        RideState.CANCELLED -> R.string.tab_cancelled
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(TAB, binding.tabs.selectedTabPosition)
        super.onSaveInstanceState(outState)
    }

    private companion object { const val TAB = "tab" }
}

private class BookingAdapter(private val open: (MyBookingItem) -> Unit) :
    ListAdapter<MyBookingItem, BookingAdapter.Holder>(Diff) {

    private var shownState: RideState = RideState.ACTIVE

    fun submit(items: List<MyBookingItem>, state: RideState) {
        shownState = state
        submitList(items)
    }

    class Holder(val binding: ItemRideBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemRideBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ride = getItem(position)
        with(holder.binding) {
            val context = root.context
            val textRes = if (shownState == RideState.ACTIVE && ride.scheduleUnavailable) {
                R.string.booking_card_unavailable
            } else {
                R.string.booking_card
            }
            details.text = context.getString(textRes,
                context.departureText(ride.serviceDate, ride.departureTime),
                ride.pickup, ride.destination, ride.driverName, ride.carModel)
            action.setText(R.string.view_ride)
            action.setOnClickListener { open(ride) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MyBookingItem>() {
        override fun areItemsTheSame(old: MyBookingItem, new: MyBookingItem) = old.bookingId == new.bookingId
        override fun areContentsTheSame(old: MyBookingItem, new: MyBookingItem) = old == new
    }
}
