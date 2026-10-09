import json
from pathlib import Path

from dotenv import load_dotenv
from pydantic import ValidationError

from backend.config.locations import START_LOCATIONS
from backend.services.maps_api import MapsAPIError, get_route
from backend.services.route_processor import process_route
from backend.utils.input_handler import (
    get_destination,
    get_start_location,
    show_start_locations,
)

PROJECT_ROOT = Path(__file__).resolve().parent
OUTPUT_FILE = PROJECT_ROOT / "data" / "route_result.json"


def main() -> None:
    load_dotenv(PROJECT_ROOT / ".env")

    print("=" * 44)
    print("       CAMPUSRIDE ROUTING PROTOTYPE")
    print("=" * 44)

    show_start_locations(START_LOCATIONS)
    start_id = get_start_location(START_LOCATIONS)
    start = START_LOCATIONS[start_id]
    destination = get_destination()

    print(f"\nStarting point: {start['name']}")
    print(f"Destination:    {destination}")
    print("Requesting a driving route from OpenRouteService...\n")

    try:
        raw_response = get_route(
            origin_address=start["address"],
            destination_address=destination,
        )
        result = process_route(
            raw_response=raw_response,
            start_id=start_id,
            start_name=start["name"],
            start_address=start["address"],
            destination_query=destination,
        )
    except (MapsAPIError, ValidationError, ValueError) as exc:
        print(f"Could not create route: {exc}")
        return

    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    result_json = result.model_dump_json(indent=2)
    OUTPUT_FILE.write_text(result_json + "\n", encoding="utf-8")

    print("-" * 44)
    print("ROUTE FOUND")
    print("-" * 44)
    print(f"Start:       {result.start.name}")
    print(f"Destination: {result.destination.query}")
    print(f"Mode:        {result.travel_mode}")
    print(f"Distance:    {result.route.distance_km:.2f} km")
    print(f"Duration:    {result.route.duration_minutes} minutes")
    print(f"Route:       {result.route.summary or 'Recommended driving route'}")

    if result.route.steps:
        print("\nBasic directions:")
        for number, step in enumerate(result.route.steps, start=1):
            distance = (
                f" ({step.distance_meters} m)"
                if step.distance_meters is not None
                else ""
            )
            print(f"  {number}. {step.instruction}{distance}")

    print(f"\nStructured JSON saved to: {OUTPUT_FILE}")
    print("Powered by © OpenRouteService")


if __name__ == "__main__":
    main()
