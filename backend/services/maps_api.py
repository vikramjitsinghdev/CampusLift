import os
from typing import Any

import requests


BASE_URL = "https://api.openrouteservice.org"

GEOCODING_ENDPOINT = f"{BASE_URL}/geocode/search"
DIRECTIONS_ENDPOINT = f"{BASE_URL}/v2/directions/driving-car/json"


class MapsAPIError(RuntimeError):
    """Raised when an OpenRouteService request fails."""


def get_api_key() -> str:
    """Retrieve the OpenRouteService API key from environment variables."""

    api_key = os.getenv("OPENROUTESERVICE_API_KEY")

    if not api_key:
        raise MapsAPIError(
            "OPENROUTESERVICE_API_KEY is missing from your .env file."
        )

    return api_key


def geocode_address(address: str) -> list[float]:
    """
    Convert an address into [longitude, latitude].

    OpenRouteService expects coordinates in longitude, latitude order.
    """

    api_key = get_api_key()

    headers = {
        "Authorization": api_key,
        "Accept": "application/json",
    }

    params = {
        "text": address,
        "size": 1,
        "boundary.country": "CA",
    }

    try:
        response = requests.get(
            GEOCODING_ENDPOINT,
            headers=headers,
            params=params,
            timeout=20,
        )

    except requests.RequestException as exc:
        raise MapsAPIError(
            f"Could not connect to OpenRouteService Geocoding API: {exc}"
        ) from exc

    if not response.ok:
        raise MapsAPIError(
            f"Geocoding API error {response.status_code}: "
            f"{response.text}"
        )

    data = response.json()

    features = data.get("features", [])

    if not features:
        raise MapsAPIError(
            f"No matching location found for: {address}. "
            "Check the spelling or provide a more specific address."
        )

    coordinates = features[0]["geometry"]["coordinates"]

    longitude = coordinates[0]
    latitude = coordinates[1]

    return [longitude, latitude]


def get_route(
    origin_address: str,
    destination_address: str,
) -> dict[str, Any]:
    """
    Geocode both addresses and retrieve a driving route.

    Returns the raw OpenRouteService directions response.
    """

    api_key = get_api_key()

    # Step 1: Convert the starting address into coordinates.
    origin_coordinates = geocode_address(origin_address)

    # Step 2: Convert the destination into coordinates.
    destination_coordinates = geocode_address(destination_address)

    # Step 3: Request the driving route.
    headers = {
        "Authorization": api_key,
        "Content-Type": "application/json",
        "Accept": "application/json",
    }

    payload = {
        "coordinates": [
            origin_coordinates,
            destination_coordinates,
        ],
        "instructions": True,
        "units": "km",
    }

    try:
        response = requests.post(
            DIRECTIONS_ENDPOINT,
            headers=headers,
            json=payload,
            timeout=30,
        )

    except requests.RequestException as exc:
        raise MapsAPIError(
            f"Could not connect to OpenRouteService Directions API: {exc}"
        ) from exc

    if not response.ok:
        raise MapsAPIError(
            f"Directions API error {response.status_code}: "
            f"{response.text}"
        )

    data = response.json()

    if not data.get("routes"):
        raise MapsAPIError(
            "OpenRouteService did not return any routes."
        )

    return data