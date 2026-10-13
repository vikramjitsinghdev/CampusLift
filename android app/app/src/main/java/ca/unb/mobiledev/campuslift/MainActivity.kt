package ca.unb.mobiledev.campuslift

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Spinner
import android.widget.ArrayAdapter

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enables the app to draw behind the system bars.
        enableEdgeToEdge()

        // Connects MainActivity.kt to activity_main.xml.
        setContentView(R.layout.activity_main)

        // Adjusts the layout so content is not hidden
        // behind the status bar or navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        // CONNECT XML ELEMENTS TO KOTLIN
        // Finds the pickup location input field
        // using the ID defined in activity_main.xml.
        val pickupLocation =
            findViewById<Spinner>(R.id.pickupLocation)

// List of designated pickup locations.
// The first option is a placeholder.
        val pickupOptions = listOf(
            "Select pickup location",
            "Student Union Building (SUB)",
            "Sir James Dunn Hall",
            "Head Hall / Science Library - IUC"
        )

// ArrayAdapter connects the list of locations
// to the Spinner dropdown.
        val pickupAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            pickupOptions
        )

// Defines how the options appear when
// the student opens the dropdown.
        pickupAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

// Assigns the adapter to the pickup dropdown.
        pickupLocation.adapter = pickupAdapter


        // Finds the destination input field.
        val destinationLocation =
            findViewById<EditText>(R.id.destinationLocation)

        // Finds the Plan Ride button.
        val planRideButton =
            findViewById<Button>(R.id.planRideButton)


        // setOnClickListener runs when the user
        // presses the Plan Ride button.
        planRideButton.setOnClickListener {

            // Get the text entered in each field.
            // trim() removes extra spaces at the
            // beginning and end of the text.
            // Gets the selected item from the dropdown.
            val pickup =
                pickupLocation.selectedItem.toString()

            val destination =
                destinationLocation.text.toString().trim()

            // Check whether the pickup field is empty.
            // Position 0 is the placeholder,
            // so the student must choose a real location.
            if (pickupLocation.selectedItemPosition == 0) {

                Toast.makeText(
                    this,
                    "Please select a pickup location",
                    Toast.LENGTH_SHORT
                ).show()

                // Check whether the destination field is empty.
            } else if (destination.isEmpty()) {

                destinationLocation.error =
                    "Please enter a destination"

            } else {

                // Both fields contain text.
                // Display a temporary confirmation message.
                // briefly at the bottom of the screen.

                Toast.makeText(
                    this,
                    "Planning ride from $pickup to $destination",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
