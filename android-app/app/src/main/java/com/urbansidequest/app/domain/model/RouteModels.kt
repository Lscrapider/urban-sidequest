package com.urbansidequest.app.domain.model

data class GeoPoint(
    val longitudeGcj02: Double,
    val latitudeGcj02: Double
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
    val reason: String?
)

