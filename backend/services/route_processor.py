from typing import Any

from backend.models.route import (
    DestinationInfo,
    RouteDetails,
    RouteResult,
    RouteStep,
    StartInfo,
)


def process_route(
    raw_response: dict[str, Any],
    start_id: int,
    start_name: str,
    start_address: str,
    destination_query: str,
) -> RouteResult:
    """Convert the raw OpenRouteService response into clean JSON."""

    routes = raw_response.get("routes", [])

    if not routes:
        raise ValueError("No routes were found.")

    route = routes[0]

    summary = route.get("summary", {})

    distance_km = summary.get("distance")
    duration_seconds = summary.get("duration")

    steps = []

    for segment in route.get("segments", []):
        for step in segment.get("steps", []):
            instruction = step.get("instruction", "").strip()

            if instruction:
                steps.append(
                    RouteStep(
                        instruction=instruction,
                        distance_meters=round(
                            step.get("distance", 0)
                        ),
                        duration_seconds=round(
                            step.get("duration", 0)
                        ),
                    )
                )

    return RouteResult(
        provider="OpenRouteService",
        travel_mode="driving",
        start=StartInfo(
            id=start_id,
            name=start_name,
            address=start_address,
        ),
        destination=DestinationInfo(
            query=destination_query,
        ),
        route=RouteDetails(
            summary="Recommended driving route",
            distance_meters=round(distance_km * 1000)
            if distance_km is not None
            else None,
            distance_km=round(distance_km, 2)
            if distance_km is not None
            else None,
            duration_seconds=round(duration_seconds)
            if duration_seconds is not None
            else None,
            duration_minutes=round(duration_seconds / 60)
            if duration_seconds is not None
            else None,
            encoded_polyline=route.get("geometry"),
            steps=steps,
        ),
    )