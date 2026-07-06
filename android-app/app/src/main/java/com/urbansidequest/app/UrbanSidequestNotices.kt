package com.urbansidequest.app

import com.urbansidequest.app.domain.model.RouteGeneration

internal sealed interface RouteGenerationNotice {
    data object Submitted : RouteGenerationNotice
    data class Completed(val routeGeneration: RouteGeneration) : RouteGenerationNotice
    data object Failed : RouteGenerationNotice
}

internal sealed interface RouteShareNotice {
    data object Submitting : RouteShareNotice
    data object Completed : RouteShareNotice
    data object Failed : RouteShareNotice
}
