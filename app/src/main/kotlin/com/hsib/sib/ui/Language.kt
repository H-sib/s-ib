package com.hsib.sib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hsib.sib.Ready
import com.hsib.sib.data.Lang
import com.hsib.sib.domain.AppState
import kotlinx.coroutines.launch

/** The first thing after the opening: choose a language. The title is shown in every language at once, so anyone can find theirs. */
@Composable
fun LanguageScreen(current: Lang, onChoose: (Lang) -> Unit) {
    val c = SibTheme.colors
    Column(Modifier.fillMaxSize().background(c.paper).statusBarsPadding().navigationBarsPadding().cuedVerticalScroll().padding(horizontal = 28.dp)) {
        Gap(48.dp)
        AppName()
        Gap(28.dp)
        Text("Choose your language", style = SibType.display, color = c.ink)
        Text("আপনার ভাষা বেছে নিন", style = SibType.headline, color = c.inkSoft)
        Text("अपनी भाषा चुनें", style = SibType.headline, color = c.inkSoft)
        Gap(28.dp)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Lang.values().forEach { l ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(if (l == current) c.violet.copy(alpha = 0.18f) else c.raised).bounceClick { onChoose(l) }.padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(l.native, style = SibType.headline, color = c.ink, modifier = Modifier.weight(1f))
                    Text(if (l == Lang.EN) "Complete" else "Main screens", style = SibType.small, color = c.inkSoft)
                }
            }
        }
        Gap(16.dp)
        Text("You can change it later in Settings.", style = SibType.small, color = c.inkSoft)
        Gap(40.dp)
    }
}

/** Change the language, and switch easy mode on or off. */
@Composable
fun LanguageSettings(st: AppState, ready: Ready, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    Page("Language", onBack) {
        Explain("English is complete. Bengali and Hindi cover the main screens, buttons and settings, and more is added with each version. Anything not yet translated stays in English.")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Lang.values().forEach { l ->
                NavRow(l.native, l.label, { scope.launch { ready.settings.update { it.copy(language = l, languageChosen = true) } } }, trailing = if (l == st.settings.language) "In use" else null, icon = SibIcon.GLOBE)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
