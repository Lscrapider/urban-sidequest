package com.urbansidequest.app.data.region

import com.urbansidequest.app.data.api.RegionApi
import com.urbansidequest.app.data.auth.AuthSessionStore
import com.urbansidequest.app.domain.model.DiscoverRegion

class RegionRepository(
    private val regionApi: RegionApi,
    private val authSessionStore: AuthSessionStore
) {

    suspend fun fetchRegions(parentAdcode: String?): List<DiscoverRegion> {
        return regionApi.fetchRegions(
            parentAdcode = parentAdcode,
            authorizationHeader = authSessionStore.requireAuthorizationHeader()
        )
    }
}
