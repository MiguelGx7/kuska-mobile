package com.proyecto.kuska.ui.main

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Usuario
import com.proyecto.kuska.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    /** El splash se mantiene hasta saber si hay sesión. */
    private var listo = false

    /** Rol con el que está armada la navegación (null = sin sesión). */
    private var rolActual: String? = null
    private var insetInferior = 0

    private val destinosSinBarra = setOf(
        R.id.loginFragment,
        R.id.registroFragment,
        R.id.recuperarFragment,
        R.id.crearDonacionFragment,
        R.id.alimentoFragment,
        R.id.detalleDonacionFragment,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !listo }

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarInsets()

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destino, _ ->
            binding.bottomNav.isVisible = destino.id !in destinosSinBarra
            ViewCompat.requestApplyInsets(binding.root)
        }

        val sesionManager = (application as KumoraApp).sesionManager

        // Arma la navegación según el rol: al abrir, al iniciar sesión y al cerrarla.
        lifecycleScope.launch {
            sesionManager.sesion
                .map { it?.usuario?.rol }
                .distinctUntilChanged()
                .collect { rol ->
                    if (!listo || rol != rolActual) aplicarNavegacion(rol)
                    listo = true
                }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sesionManager.sesionExpirada.collect {
                    Snackbar.make(binding.root, R.string.sesion_expirada, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun aplicarNavegacion(rol: String?) {
        val (inicio, menu) = when (rol) {
            Usuario.ROL_BENEFICIARIO -> R.id.inicioBeneficiarioFragment to R.menu.menu_beneficiario
            Usuario.ROL_DONANTE -> R.id.inicioDonanteFragment to R.menu.menu_donante
            else -> R.id.loginFragment to null
        }

        binding.bottomNav.menu.clear()
        menu?.let { binding.bottomNav.inflateMenu(it) }

        // Un grafo nuevo reinicia la pila. La primera vez, NavController restaura
        // la pila guardada (p. ej. tras rotar) si existe.
        val grafo = navController.navInflater.inflate(R.navigation.nav_graph)
        grafo.setStartDestination(inicio)
        navController.graph = grafo
        rolActual = rol
    }

    private fun configurarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { vista, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            vista.updatePadding(left = barras.left, top = barras.top, right = barras.right)
            insetInferior = barras.bottom
            // La BottomNavigationView maneja su propio inset inferior; sin ella, lo toma el contenido.
            binding.navHost.updatePadding(
                bottom = if (binding.bottomNav.isVisible) 0 else insetInferior
            )
            insets
        }
    }
}
