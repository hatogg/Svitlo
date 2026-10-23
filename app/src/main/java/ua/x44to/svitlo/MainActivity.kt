@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ua.x44to.svitlo

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import ua.x44to.svitlo.alarm.Alarms
import ua.x44to.svitlo.alarm.Notify
import ua.x44to.svitlo.alarm.RefreshWorker
import ua.x44to.svitlo.alarm.Sync
import ua.x44to.svitlo.data.Place
import ua.x44to.svitlo.data.Repo
import ua.x44to.svitlo.data.Store
import ua.x44to.svitlo.data.t
import ua.x44to.svitlo.ui.HomeScreen
import ua.x44to.svitlo.ui.PlaceEditor
import ua.x44to.svitlo.ui.SettingsScreen
import ua.x44to.svitlo.ui.SvitloTheme
import ua.x44to.svitlo.ui.isDark
import ua.x44to.svitlo.widget.LightWidget

class MainActivity : ComponentActivity() {
    /** Збільшується, коли людина повертається з налаштувань, — щоб екрани перевірили дозволи знову. */
    private var tick by mutableIntStateOf(0)
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        Notify.channels(this)
        Repo.loadCached(this, Store.places.mapNotNull { it.region })
        intent?.getStringExtra(EXTRA_PLACE)?.let { Store.select(it) }
        RefreshWorker.schedule(this)
        setContent {
            val dark = isDark(Store.theme)
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            SvitloTheme(Store.theme) {
                key(Store.lang) { App(tick, ::askNotifications) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PLACE)?.let { Store.select(it) }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else ua.x44to.svitlo.ui.Links.start(this, Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
    }

    companion object {
        const val EXTRA_PLACE = "svitlo.place"
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Edit(val place: Place?) : Screen
}

@Composable
private fun App(tick: Int, askNotifications: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by androidx.compose.runtime.remember { mutableStateOf<Screen>(Screen.Home) }

    fun refresh() = scope.launch {
        Repo.refresh(ctx, Store.places.mapNotNull { it.region })
        Sync.afterRefresh(ctx)
    }
    LaunchedEffect(Unit) {
        refresh()
        // перша адреса — і одразу просимо дозвіл на сповіщення, бо без нього нагадувань не буде
        if (Store.places.isEmpty()) screen = Screen.Edit(null)
    }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (val s = screen) {
                            Screen.Home -> t("Світло", "Svitlo")
                            Screen.Settings -> t("Налаштування", "Settings")
                            is Screen.Edit -> if (s.place == null) t("Нова адреса", "New address") else s.place.name
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (screen != Screen.Home && Store.places.isNotEmpty()) IconButton(onClick = { screen = Screen.Home }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Назад", "Back"))
                    }
                },
                actions = {
                    if (screen == Screen.Home) {
                        if (Repo.loading) CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = { refresh() }) { Icon(Icons.Rounded.Refresh, t("Оновити", "Refresh")) }
                        IconButton(onClick = { screen = Screen.Settings }) { Icon(Icons.Rounded.Settings, t("Налаштування", "Settings")) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { pad: PaddingValues ->
        when (val s = screen) {
            Screen.Home -> HomeScreen(pad, onAdd = { screen = Screen.Edit(null) }, onEdit = { screen = Screen.Edit(it) })
            Screen.Settings -> SettingsScreen(pad, tick, askNotifications)
            is Screen.Edit -> PlaceEditor(pad, s.place) { saved ->
                screen = Screen.Home
                if (saved != null && !Notify.allowed(ctx)) askNotifications()
                refresh()
                scope.launch { LightWidget.update(ctx) }
                Alarms.reschedule(ctx)
            }
        }
    }
}
