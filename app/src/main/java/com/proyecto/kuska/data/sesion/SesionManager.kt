package com.proyecto.kuska.data.sesion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.proyecto.kuska.data.api.modelos.Usuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStoreSesion: DataStore<Preferences> by preferencesDataStore(name = "sesion")

data class Sesion(
    val access: String,
    val refresh: String,
    val usuario: Usuario,
)

/** Guarda tokens y usuario en DataStore. */
class SesionManager(context: Context) {

    private val dataStore = context.applicationContext.dataStoreSesion
    private val gson = Gson()

    private val _sesionExpirada = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emite cuando el refresh falla y la sesión se borró. */
    val sesionExpirada: SharedFlow<Unit> = _sesionExpirada.asSharedFlow()

    /** Sesión actual, o null si no hay sesión iniciada. */
    val sesion: Flow<Sesion?> = dataStore.data.map { prefs ->
        val access = prefs[ACCESS] ?: return@map null
        val refresh = prefs[REFRESH] ?: return@map null
        val usuario = prefs[USUARIO]?.let { leerUsuario(it) } ?: return@map null
        Sesion(access, refresh, usuario)
    }

    suspend fun guardarSesion(access: String, refresh: String, usuario: Usuario) {
        dataStore.edit { prefs ->
            prefs[ACCESS] = access
            prefs[REFRESH] = refresh
            prefs[USUARIO] = gson.toJson(usuario)
        }
    }

    suspend fun actualizarTokens(access: String, refresh: String) {
        dataStore.edit { prefs ->
            prefs[ACCESS] = access
            prefs[REFRESH] = refresh
        }
    }

    suspend fun cerrarSesion() {
        dataStore.edit { it.clear() }
    }

    suspend fun obtenerAccess(): String? = dataStore.data.first()[ACCESS]

    suspend fun obtenerRefresh(): String? = dataStore.data.first()[REFRESH]

    fun notificarSesionExpirada() {
        _sesionExpirada.tryEmit(Unit)
    }

    // Versiones bloqueantes para OkHttp (sus interceptores corren en hilos de fondo).
    fun accessBloqueante(): String? = runBlocking { obtenerAccess() }

    fun refreshBloqueante(): String? = runBlocking { obtenerRefresh() }

    fun actualizarTokensBloqueante(access: String, refresh: String) =
        runBlocking { actualizarTokens(access, refresh) }

    fun cerrarSesionBloqueante() = runBlocking { cerrarSesion() }

    private fun leerUsuario(json: String): Usuario? = try {
        gson.fromJson(json, Usuario::class.java)
    } catch (_: Exception) {
        null
    }

    private companion object {
        val ACCESS = stringPreferencesKey("access")
        val REFRESH = stringPreferencesKey("refresh")
        val USUARIO = stringPreferencesKey("usuario")
    }
}
