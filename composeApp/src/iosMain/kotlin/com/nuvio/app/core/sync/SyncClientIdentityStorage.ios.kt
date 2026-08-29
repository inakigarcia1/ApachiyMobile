package com.nuvio.app.core.sync

import platform.Foundation.NSUserDefaults

actual object SyncClientIdentityStorage {
    private const val clientIdKey = "client_instance_id"
    private const val registeredDeviceIdKey = "registered_device_id"

    actual fun loadClientId(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(clientIdKey)

    actual fun saveClientId(clientId: String) {
        NSUserDefaults.standardUserDefaults.setObject(clientId, forKey = clientIdKey)
    }

    actual fun loadRegisteredDeviceId(): Long? {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.objectForKey(registeredDeviceIdKey) == null) return null
        val value = defaults.integerForKey(registeredDeviceIdKey)
        return value.takeIf { it > 0L }
    }

    actual fun saveRegisteredDeviceId(deviceId: Long) {
        NSUserDefaults.standardUserDefaults.setInteger(deviceId, forKey = registeredDeviceIdKey)
    }

    actual fun clearRegisteredDeviceId() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(registeredDeviceIdKey)
    }
}
