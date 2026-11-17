package com.pako.eminus4

import com.pako.eminus4.data.model.StudentCredential
import org.junit.Assert.assertEquals
import org.junit.Test

class StudentCredentialTest {

    @Test fun otraPersonaNuncaRecibeLaFotoEmpaquetada() {
        val c = StudentCredential(user = "zS99999999", name = "Otra Persona")
        assertEquals(R.drawable.ic_nav_profile, c.getFotoValida())
    }

    @Test fun duenoRecibeSuFotoEmpaquetada() {
        val c = StudentCredential(user = "zS24022198", name = "Dueño")
        assertEquals(R.drawable.foto_credencial_oficial, c.getFotoValida())
    }

    @Test fun avatarDeEminusSeDescarta() {
        val c = StudentCredential(user = "zS99999999", name = "X", photo = "https://eminus.uv.mx/eminusapi/profiles/a.png")
        assertEquals(R.drawable.ic_nav_profile, c.getFotoValida())
    }

    @Test fun fotoLocalDeMiuvSeUsaAunqueLaRutaDigaEminus() {
        val ruta = "file:///data/user/0/com.pako.eminus4/files/credencial/foto_ZS99999999_1.jpg"
        val c = StudentCredential(user = "zS99999999", name = "X", photo = ruta)
        assertEquals(ruta, c.getFotoValida())
        assertEquals(ruta, c.getFotoOficial())
    }

    @Test fun avatarDeEminusNoEsFotoOficial() {
        val c = StudentCredential(user = "zS99999999", name = "X", photo = "https://eminus.uv.mx/eminusapi/profiles/a.png")
        assertEquals(null, c.getFotoOficial())
    }

    @Test fun sinCarreraNoInventaUna() {
        assertEquals("", StudentCredential(user = "zS1", name = "X").getProgramaOficial())
    }

    @Test fun matriculaSinZ() {
        assertEquals("S24022198", StudentCredential(user = "zs24022198", name = "X").getMatriculaOficial())
    }
}
