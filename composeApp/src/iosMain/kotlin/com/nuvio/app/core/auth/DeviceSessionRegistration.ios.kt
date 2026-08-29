package com.nuvio.app.core.auth

import platform.UIKit.UIDevice

internal actual fun currentDeviceClientMetadata(): DeviceClientMetadata {
    val device = UIDevice.currentDevice
    val deviceName = device.name
        .trim()
        .ifBlank { device.localizedModel.trim() }
        .ifBlank { "Apple device" }
    val osVersion = device.systemVersion.trim().ifBlank { "unknown" }
    val platform = "${device.systemName()} $osVersion".trim()

    return DeviceClientMetadata(
        deviceName = deviceName,
        platform = platform,
        osVersion = osVersion,
        apiPlatform = "ios",
    )
}
