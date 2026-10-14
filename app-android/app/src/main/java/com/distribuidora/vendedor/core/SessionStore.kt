package com.distribuidora.vendedor.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Code and name of the logged-in vendor, shown in the header. */
data class VendorInfo(val code: String, val name: String)

/**
 * Tiny SharedPreferences store with the vendor's code and name, so the header can
 * be rendered without network. Cleared on logout and when the session stops being valid.
 */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _vendor = MutableStateFlow(read())
    val vendor: StateFlow<VendorInfo?> = _vendor

    fun save(code: String, name: String) {
        prefs.edit().putString(KEY_CODE, code).putString(KEY_NAME, name).apply()
        _vendor.value = VendorInfo(code, name)
    }

    fun clear() {
        prefs.edit().remove(KEY_CODE).remove(KEY_NAME).apply()
        _vendor.value = null
    }

    private fun read(): VendorInfo? {
        val code = prefs.getString(KEY_CODE, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        return VendorInfo(code, name)
    }

    private companion object {
        const val PREFS_NAME = "session"
        const val KEY_CODE = "vendor_code"
        const val KEY_NAME = "vendor_name"
    }
}
