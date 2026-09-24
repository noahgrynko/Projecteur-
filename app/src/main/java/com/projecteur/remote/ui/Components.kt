package com.projecteur.remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projecteur.remote.core.ConnectionState
import com.projecteur.remote.ui.theme.ErrorRed
import com.projecteur.remote.ui.theme.OkGreen
import com.projecteur.remote.ui.theme.WarnAmber

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun Notice(text: String, color: Color = WarnAmber) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun StatusLine(symbol: String, title: String, detail: String?, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Text(symbol, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun ConnectionState.describe(): Pair<String, Color> = when (this) {
    ConnectionState.Disconnected -> "Non connecté" to ErrorRed
    is ConnectionState.Connecting -> detail to WarnAmber
    is ConnectionState.Connected -> "Connecté — $detail" to OkGreen
    is ConnectionState.ReadyOneWay -> "Émetteur prêt (sans retour) — $detail" to WarnAmber
    is ConnectionState.Error -> message to ErrorRed
}
