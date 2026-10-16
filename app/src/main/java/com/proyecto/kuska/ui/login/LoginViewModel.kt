package com.proyecto.kuska.ui.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.repositorio.AuthRepositorio
import com.proyecto.kuska.util.Resultado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginEstado(
    val cargando: Boolean = false,
    @param:StringRes val errorCorreo: Int? = null,
    @param:StringRes val errorContrasena: Int? = null,
    /** Mensaje de la API, listo para mostrar en el aviso encima del botón. */
    val errorApi: String? = null,
)

class LoginViewModel(private val repositorio: AuthRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(LoginEstado())
    val estado: StateFlow<LoginEstado> = _estado.asStateFlow()

    fun iniciarSesion(correo: String, contrasena: String) {
        if (_estado.value.cargando) return

        val errorCorreo = ValidacionLogin.validarCorreo(correo)
        val errorContrasena = ValidacionLogin.validarContrasena(contrasena)
        val valido = errorCorreo == null && errorContrasena == null
        _estado.update {
            it.copy(
                cargando = valido,
                errorCorreo = errorCorreo,
                errorContrasena = errorContrasena,
                errorApi = null,
            )
        }
        if (!valido) return

        viewModelScope.launch {
            when (val resultado = repositorio.login(correo, contrasena)) {
                // MainActivity reacciona a la sesión guardada y cambia de pantalla;
                // se deja "cargando" para que el botón no se reactive mientras tanto.
                is Resultado.Exito -> Unit
                is Resultado.Error -> _estado.update {
                    it.copy(cargando = false, errorApi = resultado.mensaje)
                }
            }
        }
    }

    fun limpiarErrorCorreo() {
        if (_estado.value.errorCorreo != null) _estado.update { it.copy(errorCorreo = null) }
    }

    fun limpiarErrorContrasena() {
        if (_estado.value.errorContrasena != null) _estado.update { it.copy(errorContrasena = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                LoginViewModel((this[APPLICATION_KEY] as KumoraApp).authRepositorio)
            }
        }
    }
}
