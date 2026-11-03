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
    private lateinit var txtStudentCampus: TextView
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
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        imgAvatar = view.findViewById(R.id.imgAvatar)
        txtStudentName = view.findViewById(R.id.txtStudentName)
        txtStudentId = view.findViewById(R.id.txtStudentId)
        txtStudentCampus = view.findViewById(R.id.txtStudentCampus)
        txtEmailUv = view.findViewById(R.id.txtEmailUv)
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
        txtStudentName.text = profile.getNombreCompleto()
        txtStudentId.text = if (profile.userId.isNotBlank()) profile.userId else "Sin matrícula"
        txtStudentCampus.text = if (profile.userCampus.isNotBlank()) profile.userCampus else "Universidad Veracruzana"

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
}
