package com.proyecto.kuska.ui.registro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.RegistroRequest
import com.proyecto.kuska.data.api.modelos.Usuario
import com.proyecto.kuska.databinding.FragmentRegistroBinding
import com.proyecto.kuska.ui.comun.resolver
import kotlinx.coroutines.launch

/** Registro en 3 pasos (RN-04d). Al crear la cuenta, MainActivity cambia a la navegación del rol. */
class RegistroFragment : Fragment() {

    private var _binding: FragmentRegistroBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegistroViewModel by viewModels { RegistroViewModel.Factory }

    /** Campos de texto del paso 2, para enlazarlos con el ViewModel y mostrar sus errores. */
    private lateinit var camposTexto: Map<CampoRegistro, Pair<TextInputLayout, EditText>>
    private lateinit var atrasPorPasos: OnBackPressedCallback
    private var pasoMostrado = 0

    private val etiquetasDocumento = mapOf(
        "CC" to R.string.tipo_documento_cc,
        "CE" to R.string.tipo_documento_ce,
        "PAS" to R.string.tipo_documento_pas,
        "PPT" to R.string.tipo_documento_ppt,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentRegistroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarNavegacion()
        configurarPasoRol()
        configurarPasoDatos()
        configurarPasoCodigo()
        binding.botonPrincipal.setOnClickListener { accionPrincipal() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch {
                    viewModel.eventos.collect { evento ->
                        when (evento) {
                            EventoRegistro.CodigoReenviado -> Snackbar
                                .make(binding.root, R.string.registro_codigo_reenviado, Snackbar.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }
        }
    }

    private fun configurarNavegacion() {
        // En los pasos 2 y 3, "atrás" vuelve al paso anterior; en el paso 1 cierra la pantalla.
        atrasPorPasos = requireActivity().onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, enabled = false) { viewModel.atras() }
        binding.barra.setNavigationOnClickListener {
            if (!viewModel.atras()) findNavController().navigateUp()
        }
    }

    private fun configurarPasoRol() {
        binding.tarjetaDonante.setOnClickListener { viewModel.elegirRol(Usuario.ROL_DONANTE) }
        binding.tarjetaBeneficiario.setOnClickListener { viewModel.elegirRol(Usuario.ROL_BENEFICIARIO) }
    }

    private fun configurarPasoDatos() = with(binding) {
        camposTexto = mapOf(
            CampoRegistro.NOMBRE_NEGOCIO to (campoNombreNegocio to entradaNombreNegocio),
            CampoRegistro.NIT to (campoNit to entradaNit),
            CampoRegistro.NOMBRES to (campoNombres to entradaNombres),
            CampoRegistro.APELLIDOS to (campoApellidos to entradaApellidos),
            CampoRegistro.NUMERO_DOCUMENTO to (campoNumeroDocumento to entradaNumeroDocumento),
            CampoRegistro.TELEFONO to (campoTelefono to entradaTelefono),
            CampoRegistro.DIRECCION to (campoDireccion to entradaDireccion),
            CampoRegistro.CORREO to (campoCorreo to entradaCorreo),
            CampoRegistro.CONTRASENA to (campoContrasena to entradaContrasena),
            CampoRegistro.CONFIRMAR_CONTRASENA to (campoConfirmarContrasena to entradaConfirmarContrasena),
        )
        camposTexto.forEach { (campo, par) ->
            par.second.doAfterTextChanged { viewModel.cambiarCampo(campo, it?.toString().orEmpty()) }
        }

        grupoTipoDonante.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.opcion_persona -> viewModel.elegirTipoDonante(RegistroRequest.TIPO_DONANTE_PERSONA)
                R.id.opcion_negocio -> viewModel.elegirTipoDonante(RegistroRequest.TIPO_DONANTE_NEGOCIO)
            }
        }

        entradaTipoDocumento.setSimpleItems(
            RegistroRequest.TIPOS_DOCUMENTO.map { getString(etiquetasDocumento.getValue(it)) }.toTypedArray()
        )
        entradaTipoDocumento.setOnItemClickListener { _, _, posicion, _ ->
            viewModel.elegirTipoDocumento(RegistroRequest.TIPOS_DOCUMENTO[posicion])
        }

        entradaConfirmarContrasena.alPulsarListo { accionPrincipal() }
    }

    private fun configurarPasoCodigo() = with(binding) {
        entradaCodigo.doAfterTextChanged { viewModel.cambiarCodigo(it?.toString().orEmpty()) }
        entradaCodigo.alPulsarListo { accionPrincipal() }
        botonReenviar.setOnClickListener { viewModel.reenviarCodigo() }
    }

    private fun accionPrincipal() {
        WindowCompat.getInsetsController(requireActivity().window, binding.root)
            .hide(WindowInsetsCompat.Type.ime())
        viewModel.accionPrincipal()
    }

    private fun mostrar(estado: RegistroEstado) = with(binding) {
        val datos = estado.datos
        val contexto = requireContext()

        // Encabezado y pasos
        barra.subtitle = getString(R.string.registro_paso, estado.paso)
        progreso.setProgressCompat(estado.paso, true)
        pasoRol.isVisible = estado.paso == 1
        pasoDatos.isVisible = estado.paso == 2
        pasoCodigo.isVisible = estado.paso == 3
        atrasPorPasos.isEnabled = estado.paso > 1
        if (estado.paso != pasoMostrado) {
            desplazamiento.scrollTo(0, 0)
            pasoMostrado = estado.paso
        }

        // Paso 1
        tarjetaDonante.isChecked = datos.rol == Usuario.ROL_DONANTE
        tarjetaBeneficiario.isChecked = datos.rol == Usuario.ROL_BENEFICIARIO

        // Paso 2
        grupoDonante.isVisible = datos.esDonante
        grupoNegocio.isVisible = datos.esNegocio
        val idTipoDonante = when (datos.tipoDonante) {
            RegistroRequest.TIPO_DONANTE_PERSONA -> R.id.opcion_persona
            RegistroRequest.TIPO_DONANTE_NEGOCIO -> R.id.opcion_negocio
            else -> View.NO_ID
        }
        if (grupoTipoDonante.checkedRadioButtonId != idTipoDonante) {
            if (idTipoDonante == View.NO_ID) grupoTipoDonante.clearCheck() else grupoTipoDonante.check(idTipoDonante)
        }
        val mensajeTipoDonante = estado.errores[CampoRegistro.TIPO_DONANTE]?.resolver(contexto)
        errorTipoDonante.text = mensajeTipoDonante
        errorTipoDonante.isVisible = mensajeTipoDonante != null

        val textoDocumento = datos.tipoDocumento?.let { getString(etiquetasDocumento.getValue(it)) }.orEmpty()
        if (entradaTipoDocumento.text.toString() != textoDocumento) {
            entradaTipoDocumento.setText(textoDocumento, false)
        }
        campoTipoDocumento.error = estado.errores[CampoRegistro.TIPO_DOCUMENTO]?.resolver(contexto)

        camposTexto.forEach { (campo, par) ->
            par.first.error = estado.errores[campo]?.resolver(contexto)
        }

        // Paso 3
        textoCodigoEnviado.text = getString(R.string.registro_codigo_enviado, estado.correoConCodigo.orEmpty())
        campoCodigo.error = estado.errorCodigo?.resolver(contexto)
        botonReenviar.isEnabled = estado.segundosReenvio == 0 && !estado.cargando
        botonReenviar.text = if (estado.segundosReenvio > 0) {
            getString(R.string.registro_reenviar_en, estado.segundosReenvio)
        } else {
            getString(R.string.registro_reenviar)
        }

        // Aviso y botón principal
        avisoError.text = estado.errorApi
        avisoError.isVisible = estado.errorApi != null

        val textoBoton = getString(
            when (estado.paso) {
                1 -> R.string.registro_continuar
                2 -> if (estado.codigoYaEnviado) R.string.registro_continuar else R.string.registro_enviar_codigo
                else -> R.string.registro_crear_cuenta
            }
        )
        botonPrincipal.isEnabled = !estado.cargando && (estado.paso != 1 || datos.rol != null)
        botonPrincipal.text = if (estado.cargando) null else textoBoton
        botonPrincipal.contentDescription = if (estado.cargando) getString(R.string.registro_cargando) else null
        indicadorCarga.isVisible = estado.cargando
    }

    private fun EditText.alPulsarListo(accion: () -> Unit) {
        setOnEditorActionListener { _, idAccion, _ ->
            if (idAccion == EditorInfo.IME_ACTION_DONE) {
                accion()
                true
            } else {
                false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
