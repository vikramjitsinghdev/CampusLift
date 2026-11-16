package com.campuslift.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.campuslift.app.R
import com.campuslift.app.data.RideListItem
import com.campuslift.app.databinding.ItemRideBinding

/** ListAdapter compares old and new lists, so only cards that changed are redrawn. */
class RideAdapter(private val book: (RideListItem) -> Unit) :
    ListAdapter<RideListItem, RideAdapter.Holder>(Diff) {

    private var busy = false

    fun submit(rides: List<RideListItem>, booking: Boolean) {
        if (busy != booking) {
            busy = booking
            notifyItemRangeChanged(0, itemCount)   // only the Book buttons change state
        }
        submitList(rides)
    }

    class Holder(val binding: ItemRideBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemRideBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ride = getItem(position)
        with(holder.binding) {
            val context = root.context
            details.text = context.getString(R.string.ride_details, ride.driverName, ride.carModel,
                ride.pickup, ride.destination,
                context.departureText(ride.serviceDate, ride.departureTime), ride.seatsLeft)
            action.isEnabled = !busy && ride.seatsLeft > 0
            action.setOnClickListener { book(ride) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<RideListItem>() {
        override fun areItemsTheSame(old: RideListItem, new: RideListItem) =
            old.rideId == new.rideId && old.serviceDate == new.serviceDate
        override fun areContentsTheSame(old: RideListItem, new: RideListItem) = old == new
    }
}
