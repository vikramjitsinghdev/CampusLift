package com.campuslift.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.campuslift.app.R
import com.campuslift.app.data.AppDatabase
import com.campuslift.app.data.MyBookingItem
import com.campuslift.app.databinding.ActivityMyRidesBinding
import com.campuslift.app.databinding.ItemRideBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MyRidesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyRidesBinding
    private val db by lazy { AppDatabase.getInstance(this) }
    private val adapter = BookingAdapter { cancel(it) }
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyRidesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setTitle(R.string.my_rides)
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationContentDescription(R.string.back)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rides.layoutManager = LinearLayoutManager(this)
        binding.rides.adapter = adapter
        binding.retry.setOnClickListener { load() }
    }

    override fun onStart() { super.onStart(); load() }

    private fun load() {
        if (busy) return
        busy = true
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try { db.seedIfEmpty(applicationContext); refresh()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { showFailure()
            } finally { busy = false; binding.progress.visibility = View.GONE }
        }
    }

    private suspend fun refresh() {
        val bookings = db.dao().myBookings()
        adapter.submit(bookings)
        binding.status.text = if (bookings.isEmpty()) getString(R.string.no_bookings) else ""
        binding.retry.visibility = View.GONE
    }

    private fun showFailure() {
        binding.status.setText(R.string.database_error)
        binding.retry.visibility = View.VISIBLE
    }

    private fun cancel(bookingId: Int) {
        if (busy) return
        busy = true
        adapter.setBusy(true)
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val changed = db.dao().cancelBooking(bookingId)
                Toast.makeText(this@MyRidesActivity,
                    if (changed > 0) R.string.cancel_success else R.string.cancel_missing, Toast.LENGTH_SHORT).show()
                refresh()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                Toast.makeText(this@MyRidesActivity, R.string.operation_error, Toast.LENGTH_LONG).show()
            } finally {
                busy = false
                adapter.setBusy(false)
                binding.progress.visibility = View.GONE
            }
        }
    }
}

private class BookingAdapter(private val cancel: (Int) -> Unit) : RecyclerView.Adapter<BookingAdapter.Holder>() {
    private var items = emptyList<MyBookingItem>()
    private var busy = false
    fun submit(bookings: List<MyBookingItem>) { items = bookings; notifyDataSetChanged() }
    fun setBusy(value: Boolean) { busy = value; notifyDataSetChanged() }
    class Holder(val binding: ItemRideBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemRideBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val booking = items[position]
        with(holder.binding) {
            details.text = root.context.getString(R.string.booking_details, booking.driverName, booking.carModel,
                booking.phone, booking.pickup, booking.destination, booking.departureTime)
            action.setText(R.string.cancel_ride)
            action.isEnabled = !busy
            action.setOnClickListener { cancel(booking.bookingId) }
        }
    }
}
