package com.pako.eminus4.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.button.MaterialButton
import com.pako.eminus4.AppUpdater
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.UserProfile
import com.pako.eminus4.ui.main.MainActivity

class ProfileFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: EminusApiClient

    private lateinit var imgAvatar: ImageView
    private lateinit var txtStudentName: TextView
    private lateinit var txtStudentId: TextView
    private lateinit var txtStudentProgram: TextView
    private lateinit var txtStudentCampus: TextView
    private lateinit var cardStudentCredential: View
    private lateinit var btnOpenCredential: MaterialButton
    private lateinit var txtEmailUv: TextView
    private lateinit var txtEmailAlternate: TextView
    private lateinit var layoutAlternateEmail: View
    private lateinit var txtSessionStatus: TextView
    private lateinit var btnCheckUpdates: MaterialButton
    private lateinit var btnLogout: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        apiClient = EminusApiClient(sessionManager)

        inicializarVistas(view)
        cargarPerfilDesdeCache()
        actualizarPerfilDesdeApi()
    }

    private fun inicializarVistas(view: View) {
        val appBarLayout = view.findViewById<View>(R.id.appBarLayout)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val bannerVisible = (activity as? com.pako.eminus4.ui.main.MainActivity)?.isBannerVisible() == true
            v.setPadding(0, if (bannerVisible) 0 else systemBars.top, 0, 0)
            insets
        }

        imgAvatar = view.findViewById(R.id.imgAvatar)
        txtStudentName = view.findViewById(R.id.txtStudentName)
        txtStudentId = view.findViewById(R.id.txtStudentId)
        txtStudentProgram = view.findViewById(R.id.txtStudentProgram)
        txtStudentCampus = view.findViewById(R.id.txtStudentCampus)
        cardStudentCredential = view.findViewById(R.id.cardStudentCredential)
        btnOpenCredential = view.findViewById(R.id.btnOpenCredential)
        txtEmailUv = view.findViewById(R.id.txtEmailUv)

        val abrirCredencial = View.OnClickListener {
            CredentialFullscreenActivity.start(requireContext())
        }
        cardStudentCredential.setOnClickListener(abrirCredencial)
        btnOpenCredential.setOnClickListener(abrirCredencial)

        cardStudentCredential.setOnLongClickListener {
            mostrarDialogoEditarCredencial()
            true
        }
        txtEmailAlternate = view.findViewById(R.id.txtEmailAlternate)
        layoutAlternateEmail = view.findViewById(R.id.layoutAlternateEmail)
        txtSessionStatus = view.findViewById(R.id.txtSessionStatus)
        btnCheckUpdates = view.findViewById(R.id.btnCheckUpdates)
        btnLogout = view.findViewById(R.id.btnLogout)

        btnCheckUpdates.setOnClickListener {
            AppUpdater.verificarActualizaciones(requireActivity(), silent = false)
        }

        btnLogout.setOnClickListener {
            mostrarDialogoCerrarSesion()
        }
    }

    private fun cargarPerfilDesdeCache() {
        val cached = sessionManager.getUserProfile()
        mostrarPerfil(cached)
    }

    private fun actualizarPerfilDesdeApi() {
        apiClient.getUserProfile { result ->
            if (!isAdded) return@getUserProfile

            result.onSuccess { apiProfile ->
                mostrarPerfil(apiProfile)
                // Guardar datos enriquecidos en sessionManager
                sessionManager.saveSession(
                    accessToken = sessionManager.getAccessToken() ?: "",
                    userName = apiProfile.getNombreCompleto(),
                    firstName = apiProfile.nombre,
                    userId = apiProfile.userId,
                    userEmail = apiProfile.userEmail,
                    userCampus = apiProfile.userCampus,
                    avatarUrl = apiProfile.getFullAvatarUrl(),
                    tokenExpiration = sessionManager.getUserProfile().tokenExpiration
                )
            }.onFailure { error ->
                // Fallback a los datos de caché ya mostrados
            }
        }
    }

    private fun mostrarPerfil(profile: UserProfile) {
        val cred = sessionManager.getStudentCredential()
        val nombre = cred?.getNombreCompleto() ?: profile.getNombreCompleto()
        val matricula = cred?.user ?: profile.userId
        val programa = cred?.program ?: "Estudiante Activo"
        val campus = cred?.region ?: if (profile.userCampus.isNotBlank()) profile.userCampus else "Universidad Veracruzana"

        txtStudentName.text = nombre
        txtStudentId.text = if (matricula.isNotBlank()) matricula.uppercase() else "Sin matrícula"
        txtStudentProgram.text = programa
        txtStudentCampus.text = campus

        // Asegurar que la credencial quede persistida localmente para modo offline
        if (!sessionManager.hasStudentCredential() && matricula.isNotBlank()) {
            sessionManager.saveStudentCredential(
                com.pako.eminus4.data.model.StudentCredential(
                    user = matricula,
                    name = nombre,
                    program = programa,
                    region = campus,
                    monthsValidity = "FEB - JUL",
                    yearValidity = "2026",
                    photo = profile.getFullAvatarUrl(),
                    qrCode = "https://dsia.uv.mx/credencial/validar?u=$matricula",
                    barCode = matricula
                )
            )
        }

        txtEmailUv.text = if (profile.userEmail.isNotBlank()) profile.userEmail else "No disponible"

        if (!profile.alternateEmail.isNullOrBlank()) {
            layoutAlternateEmail.visibility = View.VISIBLE
            txtEmailAlternate.text = profile.alternateEmail
        } else {
            layoutAlternateEmail.visibility = View.GONE
        }

        // Cargar Avatar
        val avatarUrl = profile.getFullAvatarUrl()
        if (!avatarUrl.isNullOrBlank()) {
            imgAvatar.load(avatarUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
            }
        } else {
            imgAvatar.setImageResource(R.drawable.ic_nav_profile)
        }

        txtSessionStatus.text = "Sesión activa con token JWT institucional"
    }

    private fun mostrarDialogoCerrarSesion() {
        AlertDialog.Builder(requireContext())
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro de que deseas cerrar tu sesión de Eminus?")
            .setPositiveButton("Cerrar sesión") { _, _ ->
                (activity as? MainActivity)?.cerrarSesionDirecta()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoEditarCredencial() {
        val cred = sessionManager.getStudentCredential() ?: return
        val context = requireContext()

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        val inputProgram = com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = "Carrera / Licenciatura"
            setText(cred.program)
        }
        val layoutProgram = com.google.android.material.textfield.TextInputLayout(context).apply {
            hint = "Programa Educativo"
            addView(inputProgram)
        }

        val inputRegion = com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = "Campus / Región"
            setText(cred.region)
        }
        val layoutRegion = com.google.android.material.textfield.TextInputLayout(context).apply {
            hint = "Región / Campus"
            setPadding(0, 16, 0, 0)
            addView(inputRegion)
        }

        layout.addView(layoutProgram)
        layout.addView(layoutRegion)

        AlertDialog.Builder(context)
            .setTitle("Datos de la Credencial")
            .setMessage("Personaliza los datos académicos que se muestran en tu credencial digital:")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoPrograma = inputProgram.text.toString().trim().ifBlank { cred.program }
                val nuevaRegion = inputRegion.text.toString().trim().ifBlank { cred.region }
                val updated = cred.copy(program = nuevoPrograma, region = nuevaRegion)
                sessionManager.saveStudentCredential(updated)
                mostrarPerfil(sessionManager.getUserProfile())
                Toast.makeText(context, "Credencial actualizada y guardada en el dispositivo", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
