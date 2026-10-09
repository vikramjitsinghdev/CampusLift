"""Pydantic models for the clean CampusRide route JSON."""

from pydantic import BaseModel, ConfigDict, Field


class StartInfo(BaseModel):
    id: int = Field(ge=1, le=4)
    name: str
    address: str


class DestinationInfo(BaseModel):
    query: str


class RouteStep(BaseModel):
    instruction: str
    distance_meters: int | None = None
    duration_seconds: int | None = None


class RouteDetails(BaseModel):
    summary: str
    distance_meters: int | None = None
    distance_km: float | None = None
    duration_seconds: int | None = None
    duration_minutes: int | None = None
    static_duration_seconds: int | None = None
    route_labels: list[str] = Field(default_factory=list)
    encoded_polyline: str | None = None
    steps: list[RouteStep] = Field(default_factory=list)


class RouteResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    provider: str
    travel_mode: str
    start: StartInfo
    destination: DestinationInfo
    route: RouteDetails
