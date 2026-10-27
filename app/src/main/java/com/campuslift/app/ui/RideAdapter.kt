package com.campuslift.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.campuslift.app.R
import com.campuslift.app.data.RideListItem
import com.campuslift.app.databinding.ItemRideBinding

class RideAdapter(private val book: (Int) -> Unit) : RecyclerView.Adapter<RideAdapter.Holder>() {
    private var items = emptyList<RideListItem>()
    private var busy = false
    fun submit(rides: List<RideListItem>, booking: Boolean) {
        items = rides
        busy = booking
        notifyDataSetChanged()
    }
    class Holder(val binding: ItemRideBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemRideBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ride = items[position]
        with(holder.binding) {
            details.text = root.context.getString(R.string.ride_details, ride.driverName, ride.carModel,
                ride.pickup, ride.destination, ride.departureTime, ride.seatsLeft)
            action.isEnabled = !busy && ride.seatsLeft > 0
            action.setOnClickListener { book(ride.rideId) }
        }
    }
}
