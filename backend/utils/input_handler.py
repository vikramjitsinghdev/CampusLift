"""Command-line input and validation helpers."""


def show_start_locations(locations: dict) -> None:
    """Display each starting location with its physical address."""

    print("\nChoose a starting location:\n")

    for location_id, location in locations.items():
        print(f"[{location_id}] {location['name']}")
        print(f"    Address: {location['address']}\n")


def get_start_location(locations: dict) -> int:
    """Ask the user to select one of the predefined locations."""

    while True:
        value = input("Select starting location (1-4): ").strip()

        try:
            location_id = int(value)
        except ValueError:
            print("Please enter a number from 1 to 4.")
            continue

        if location_id in locations:
            return location_id

        print("Invalid selection. Please choose 1, 2, 3, or 4.")


def get_destination() -> str:
    """Ask the user to enter their destination."""

    while True:
        destination = input(
            "\nEnter your destination address: "
        ).strip()

        if destination:
            return destination

        print("Destination cannot be empty. Please try again.")