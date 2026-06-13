package com.urbansidequest.app.data.map

/**
 * 高德 Android SDK 适配边界。
 *
 * 后续接入 AMap MapView、定位、POI 搜索和路线 overlay 时，先通过本包封装，
 * 页面层不要直接散落高德 SDK 调用。
 */
interface MapSdkFacade {

    fun initialize()
}

