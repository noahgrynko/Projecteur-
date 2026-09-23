package com.projecteur.remote.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projecteur.remote.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Démarre la vraie activité (Robolectric) et parcourt chaque écran : aucun plantage, et les
 * messages attendus sur un téléphone sans émetteur IR ni réseau local sont affichés.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun remoteKeysAreDisabledWithoutActiveMethod() {
        rule.onNodeWithText("Méthode : aucune").assertExists()
        rule.onNodeWithText("⏻").assertIsNotEnabled()
        rule.onNodeWithText("VOL +").assertIsNotEnabled()
        rule.onNodeWithText("OK").assertIsNotEnabled()
    }

    @Test
    fun everyScreenOpens() {
        rule.onNodeWithText("⚙ Configuration").performClick()
        rule.onNodeWithText("🔴 Infrarouge (émetteur, profils)").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Aucun émetteur infrarouge intégré détecté. Un émetteur IR externe compatible USB-C est nécessaire pour utiliser ce mode.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("🔴 Utiliser l'infrarouge avec ce profil").performScrollTo().performClick()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        rule.onNodeWithText("📡 Apprendre une commande IR").performClick()
        rule.onAllNodesWithText("Un récepteur IR compatible est nécessaire", substring = true).fetchSemanticsNodes().let {
            check(it.isNotEmpty()) { "message récepteur manquant" }
        }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        rule.onNodeWithText("🔵 Bluetooth").performClick()
        rule.onNodeWithText("Il n'existe pas de profil Bluetooth standard", substring = true).assertExists()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        rule.onNodeWithText("🌐 Réseau local (PJLink)").performClick()
        rule.onNodeWithText("Contrôle PJLink (TCP 4352)").assertExists()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        rule.onNodeWithText("Diagnostic").performClick()
        rule.onNodeWithText("Test automatique").assertExists()
        rule.onNodeWithText("Émetteur IR disponible — Nécessite un accessoire").assertExists()

        rule.onNodeWithText("Rechercher").performClick()
        rule.onNodeWithText("🔎 Rechercher le vidéoprojecteur").assertExists()
    }
}
