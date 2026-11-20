package de.v404.honorarcraft.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.v404.honorarcraft.shared.InMemorySettings
import de.v404.honorarcraft.shared.MainViewModel
import de.v404.honorarcraft.shared.data.AppDatabase
import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.DATABASE_FILE_NAME
import de.v404.honorarcraft.shared.data.createDesktopDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Das Satzfeld unter „Finanzdaten“ nimmt nur Beträge über null an. */
@OptIn(ExperimentalTestApi::class)
class HonorarsatzUiTest {

    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel

    @BeforeTest
    fun setUp() {
        val dir = Files.createTempDirectory("honorarcraft-satz")
        db = createDesktopDatabase(dir.resolve(DATABASE_FILE_NAME).toAbsolutePath().toString())
        runBlocking { db.companyDao().insertCompanyData(CompanyData(rate = BigDecimal("23.00"))) }
        viewModel = MainViewModel(db, InMemorySettings())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private val satzLabel = "Honorarsatz € a 45 Minuten"

    private fun ComposeUiTest.satzEingeben(text: String) {
        val feld = hasSetTextAction() and hasText(satzLabel)
        onNode(hasScrollAction() and hasAnyDescendant(hasSetTextAction()))
            .performScrollToNode(feld)
        onNode(feld).performTextReplacement(text)
        onNodeWithContentDescription("Speichern").performClick()
        waitForIdle()
    }

    private fun gespeichert() = runBlocking { db.companyDao().getCompanyData().first()!!.rate }

    @Test
    fun `leeres oder ungueltiges Satzfeld speichert nicht, gueltiges mit zwei Stellen`() =
        runDesktopComposeUiTest(width = 1200, height = 820) {
            setContent { HonorarCraftApp(viewModel) }
            onNodeWithText("Daten").performClick()
            waitUntil(timeoutMillis = 5_000) { viewModel.companyData.value != null }

            satzEingeben("")
            assertEquals(BigDecimal("23.00"), gespeichert())
            onNodeWithText("Bitte einen Betrag wie 25,00 eingeben.").assertExists()

            satzEingeben("0")
            assertEquals(BigDecimal("23.00"), gespeichert())

            satzEingeben("24")
            waitUntil(timeoutMillis = 5_000) { gespeichert() != BigDecimal("23.00") }
            assertEquals(BigDecimal("24.00"), gespeichert())
        }
}

