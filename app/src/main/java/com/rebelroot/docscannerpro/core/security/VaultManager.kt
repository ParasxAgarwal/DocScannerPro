package com.rebelroot.docscannerpro.core.security
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
class VaultManager(context: Context) {
    private val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()
    fun isPinSet(): Boolean {
        return prefs.contains(KEY_PIN_HASH)
    }
    fun setPin(pin: String): Boolean {
        if (pin.length < 4) return false
        val hash = hashPin(pin)
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
        _isUnlocked.value = true
        return true
    }
    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val inputHash = hashPin(pin)
        val matches = savedHash == inputHash
        if (matches) {
            _isUnlocked.value = true
        }
        return matches
    }
    fun lock() {
        _isUnlocked.value = false
    }
    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    companion object {
        private const val KEY_PIN_HASH = "vault_pin_hash"
    }
}
