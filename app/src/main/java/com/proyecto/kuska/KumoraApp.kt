package com.proyecto.kuska

import android.app.Application
import com.proyecto.kuska.data.api.ClienteApi
import com.proyecto.kuska.data.repositorio.AuthRepositorio
import com.proyecto.kuska.data.repositorio.DonacionesRepositorio
import com.proyecto.kuska.data.sesion.SesionManager

/** Contenedor simple de dependencias (sin Hilt). Acceso: (requireActivity().application as KumoraApp). */
class KumoraApp : Application() {

    val sesionManager: SesionManager by lazy { SesionManager(this) }

    val clienteApi: ClienteApi by lazy { ClienteApi(sesionManager) }

    val authRepositorio: AuthRepositorio by lazy {
        AuthRepositorio(this, clienteApi.api, sesionManager)
    }

    val donacionesRepositorio: DonacionesRepositorio by lazy {
        DonacionesRepositorio(this, clienteApi.api)
    }
}
