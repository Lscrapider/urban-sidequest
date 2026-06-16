package com.urbansidequest.app.domain.model

data class GeoPoint(
    val longitudeGcj02: Double,
    val latitudeGcj02: Double
)

data class RouteStep(
    val order: Int,
    val instruction: String,
    val roadName: String?,
    val distanceMeters: Int,
    val durationMinutes: Int,
    val polyline: List<GeoPoint> = emptyList()
)

data class RouteSegment(
    val order: Int,
    val originStopId: String,
    val destinationStopId: String,
    val mode: String,
    val distanceMeters: Int,
    val durationMinutes: Int,
    val polyline: List<GeoPoint> = emptyList(),
    val steps: List<RouteStep> = emptyList(),
    val summary: String
)

data class RouteSummary(
    val id: String,
    val code: String,
    val title: String,
    val totalDurationMinutes: Int,
    val totalDistanceMeters: Int?
)

data class RouteStop(
    val id: String,
    val order: Int,
    val name: String,
    val location: GeoPoint,
    val reason: String?,
    val slotLabel: String? = null,
    val description: String? = null,
    val imageUrls: List<String> = emptyList(),
    val category: String? = null,
    val rating: Double? = null,
    val stayMinutes: Int? = null,
    val transportToNext: String? = null,
    val distanceToNextMeters: Int? = null,
    val durationToNextMinutes: Int? = null,
    val riskNote: String? = null
)

data class RouteArea(
    val areaMode: String,
    val areaLabel: String,
    val center: GeoPoint,
    val radiusMeters: Int,
    val description: String?
)

data class GeneratedRoute(
    val routeCode: String,
    val title: String,
    val summary: String,
    val totalDurationMinutes: Int,
    val totalDistanceMeters: Int,
    val budgetCent: Int?,
    val riskLevel: String,
    val explanation: String,
    val stops: List<RouteStop>,
    val segments: List<RouteSegment> = emptyList()
)

data class RouteGeneration(
    val requestId: String,
    val status: String,
    val area: RouteArea,
    val routes: List<GeneratedRoute>,
    val warnings: List<String>
)
