package com.pako.eminus4.ui.profile

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.pako.eminus4.AppUpdater
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.UserProfile
import com.pako.eminus4.ui.main.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: EminusApiClient

    private lateinit var imgAvatar: ImageView
    private lateinit var txtStudentName: TextView
    private lateinit var txtStudentId: TextView
    private lateinit var txtStudentProgram: TextView
    private lateinit var txtStudentCampus: TextView
    private lateinit var txtStudentValidity: TextView
    private lateinit var clockProfileLive: TextView
    private lateinit var cardStudentCredential: View
    private lateinit var btnOpenCredential: MaterialButton
    private lateinit var txtEmailUv: TextView
    private lateinit var txtEmailAlternate: TextView
    private lateinit var layoutAlternateEmail: View
    private lateinit var txtSessionStatus: TextView
    private lateinit var btnCheckUpdates: MaterialButton
    private lateinit var btnLogout: MaterialButton
    private lateinit var layoutDarkModeToggle: View
    private lateinit var switchDarkMode: SwitchMaterial
    private lateinit var txtDarkModeStatus: TextView

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("dd/MMM/yyyy HH:mm:ss", Locale("es", "MX"))
    private val clockRunnable = object : Runnable {
        override fun run() {
            if (::clockProfileLive.isInitialized && isAdded) {
                clockProfileLive.text = clockFormat.format(Date()).lowercase(Locale("es", "MX"))
            }
            clockHandler.postDelayed(this, 1000)
        }
    }

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
        iniciarRelojPerfil()
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
        txtStudentValidity = view.findViewById(R.id.txtStudentValidity)
        clockProfileLive = view.findViewById(R.id.clockProfileLive)
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
        layoutDarkModeToggle = view.findViewById(R.id.layoutDarkModeToggle)
        switchDarkMode = view.findViewById(R.id.switchDarkMode)
        txtDarkModeStatus = view.findViewById(R.id.txtDarkModeStatus)

        val isDark = sessionManager.isDarkModeEnabled()
        switchDarkMode.isChecked = isDark
        txtDarkModeStatus.text = if (isDark) "Activado" else "Desactivado"

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            if (sessionManager.isDarkModeEnabled() != isChecked) {
                sessionManager.setDarkModeEnabled(isChecked)
                txtDarkModeStatus.text = if (isChecked) "Activado" else "Desactivado"
                AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        }

        layoutDarkModeToggle.setOnClickListener {
            switchDarkMode.toggle()
        }

        btnCheckUpdates.setOnClickListener {
            AppUpdater.verificarActualizaciones(requireActivity(), silent = false)
        }

        btnLogout.setOnClickListener {
            mostrarDialogoCerrarSesion()
        }
    }

    private fun iniciarRelojPerfil() {
        clockProfileLive.text = clockFormat.format(Date()).lowercase(Locale("es", "MX"))
        clockHandler.post(clockRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clockHandler.removeCallbacks(clockRunnable)
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
            }.onFailure { _ ->
                // Fallback a los datos de caché ya mostrados
            }
        }
    }

    private fun mostrarPerfil(profile: UserProfile) {
        // Matrícula siempre desde la sesión actual (no de la credencial cacheada)
        val matriculaSession = if (profile.userId.startsWith("z", ignoreCase = true))
            profile.userId.substring(1).uppercase() else profile.userId.uppercase()

        val credGuardada = sessionManager.getStudentCredential()
        val credEsDelUsuarioActual = credGuardada?.getMatriculaOficial() == matriculaSession

        // Si la credencial guardada es de otro usuario, ignorarla completamente
        val cred = if (credEsDelUsuarioActual) credGuardada else null

        val nombre = cred?.getNombreFormateado() ?: profile.getNombreCompleto()
        val matricula = matriculaSession
        // Campus del perfil de sesión actual (no de credencial de otro usuario)
        val campusSession = profile.userCampus.ifBlank { cred?.getCampusOficial() ?: "" }
        val programa = cred?.getProgramaOficial() ?: ""   // sin fallback hardcodeado
        val campus = campusSession.ifBlank { cred?.getCampusOficial() ?: "" }
        val vigencia = cred?.getVigenciaTexto() ?: "AGO-ENE 2027"

        txtStudentName.text = nombre
        txtStudentId.text = matricula
        txtStudentProgram.text = programa.ifBlank { "Sin programa asignado" }
        txtStudentCampus.text = campus.ifBlank { "Sin campus asignado" }
        txtStudentValidity.text = vigencia

        // Siempre persistir credencial actualizada para el usuario actual
        val vigenciaCorrecta = "AGO-ENE"
        val anioCorrect = "2027"
        val necesitaActualizar = cred == null ||
            cred.monthsValidity != vigenciaCorrecta ||
            cred.yearValidity != anioCorrect ||
            !credEsDelUsuarioActual
        if (necesitaActualizar && matricula.isNotBlank()) {
            sessionManager.saveStudentCredential(
                com.pako.eminus4.data.model.StudentCredential(
                    user = matricula,
                    name = nombre,
                    program = programa,
                    region = campus,
                    monthsValidity = vigenciaCorrecta,
                    yearValidity = anioCorrect,
                    photo = cred?.photo ?: profile.getFullAvatarUrl(),
                    qrCode = cred?.qrCode ?: "https://dsia.uv.mx/credencial/validar?u=$matricula",
                    barCode = cred?.barCode ?: matricula
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

        // Foto de perfil: usar avatar de Eminus (casual) en la tarjeta resumen
        val avatarUrl = profile.getFullAvatarUrl()
        if (!avatarUrl.isNullOrBlank()) {
            imgAvatar.load(avatarUrl) {
                crossfade(true)
                transformations(coil.transform.CircleCropTransformation())
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

        val scrollView = android.widget.ScrollView(context)
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }
        scrollView.addView(layout)

        fun createField(hintText: String, initialValue: String): Pair<com.google.android.material.textfield.TextInputLayout, com.google.android.material.textfield.TextInputEditText> {
            val editText = com.google.android.material.textfield.TextInputEditText(context).apply {
                setText(initialValue)
            }
            val textLayout = com.google.android.material.textfield.TextInputLayout(context).apply {
                hint = hintText
                setPadding(0, 8, 0, 8)
                addView(editText)
            }
            return Pair(textLayout, editText)
        }

        val (layoutName, inputName) = createField("Nombre Completo", cred.getNombreFormateado())
        val (layoutId, inputId) = createField("Matrícula", cred.getMatriculaOficial())
        val (layoutProgram, inputProgram) = createField("Programa Educativo", cred.getProgramaOficial())
        val (layoutRegion, inputRegion) = createField("Campus / Región", cred.getCampusOficial())
        val (layoutValidity, inputValidity) = createField("Vigencia (ej. AGO-ENE 2027)", cred.getVigenciaTexto())

        layout.addView(layoutName)
        layout.addView(layoutId)
        layout.addView(layoutProgram)
        layout.addView(layoutRegion)
        layout.addView(layoutValidity)

        AlertDialog.Builder(context)
            .setTitle("Datos de la Credencial Oficial")
            .setMessage("Personaliza los datos que se muestran en tu credencial digital para que coincidan con los de la UV:")
            .setView(scrollView)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoNombre = inputName.text.toString().trim().ifBlank { cred.getNombreCompleto() }
                val nuevaMatricula = inputId.text.toString().trim().ifBlank { cred.user }
                val nuevoPrograma = inputProgram.text.toString().trim().ifBlank { cred.program }
                val nuevaRegion = inputRegion.text.toString().trim().ifBlank { cred.region }
                val nuevaVigencia = inputValidity.text.toString().trim().ifBlank { cred.getVigenciaTexto() }

                val partesVigencia = nuevaVigencia.split(" ", limit = 2)
                val months = if (partesVigencia.isNotEmpty()) partesVigencia[0] else cred.monthsValidity
                val year = if (partesVigencia.size > 1) partesVigencia[1] else cred.yearValidity

                val updated = cred.copy(
                    name = nuevoNombre,
                    lastNames = "",
                    user = nuevaMatricula,
                    program = nuevoPrograma,
                    region = nuevaRegion,
                    monthsValidity = months,
                    yearValidity = year,
                    qrCode = "https://dsia.uv.mx/credencial/validar?u=$nuevaMatricula",
                    barCode = nuevaMatricula
                )
                sessionManager.saveStudentCredential(updated)
                mostrarPerfil(sessionManager.getUserProfile())
                Toast.makeText(context, "Credencial guardada y actualizada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
