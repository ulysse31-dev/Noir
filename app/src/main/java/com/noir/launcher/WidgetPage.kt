package com.noir.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PendingWidget(val id: Int, val info: AppWidgetProviderInfo)

/**
 * Page de droite : le widget Google Agenda, affiché en plein écran.
 * Au premier passage, le widget « jour » est choisi automatiquement ; « Changer de widget » ouvre la liste (avec aperçu).
 */
@Composable
fun WidgetPage(
    controller: WidgetController,
    configure: (Int, (Boolean) -> Unit) -> Unit,
) {
    var widgetId by remember { mutableIntStateOf(controller.widgetId) }
    var providers by remember { mutableStateOf<List<AppWidgetProviderInfo>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingWidget?>(null) }
    // Premier passage : on choisit tout seul le widget « jour » (le choix manuel reste dans « Changer de widget »).
    var auto by remember { mutableStateOf(true) }

    fun finish(id: Int) {
        controller.widgetId = id
        widgetId = id
        pending = null
    }

    fun abort(id: Int) {
        controller.deleteId(id)
        pending = null
    }

    fun afterBound(p: PendingWidget) {
        if (p.info.configure != null) {
            configure(p.id) { ok -> if (ok) finish(p.id) else abort(p.id) }
        } else {
            finish(p.id)
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val p = pending
        if (p != null) {
            if (result.resultCode == Activity.RESULT_OK) afterBound(p) else abort(p.id)
        }
    }

    fun pick(info: AppWidgetProviderInfo) {
        val id = controller.host.allocateAppWidgetId()
        val p = PendingWidget(id, info)
        pending = p
        if (controller.manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            afterBound(p)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            bindLauncher.launch(intent)
        }
    }

    LaunchedEffect(widgetId) {
        if (widgetId == -1) {
            providers = withContext(Dispatchers.Default) { controller.providers() }
            loaded = true
            val choice = if (auto) controller.preferred(providers) else null
            if (choice != null) pick(choice)
            auto = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(NoirColors.Black),
    ) {
        if (widgetId == -1 && auto) {
            // Sélection automatique en cours : rien à afficher.
        } else if (widgetId == -1) {
            Picker(
                providers = providers,
                loaded = loaded,
                onPick = { pick(it) },
            )
        } else {
            val info = remember(widgetId) { controller.manager.getAppWidgetInfo(widgetId) }
            if (info == null) {
                // Le widget n'existe plus (appli désinstallée, par exemple).
                LaunchedEffect(widgetId) {
                    controller.clear()
                    widgetId = -1
                }
            } else {
                key(widgetId) {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        val w = maxWidth.value.toInt()
                        val h = maxHeight.value.toInt()
                        AndroidView(
                            factory = { ctx -> controller.host.createView(ctx, widgetId, info) },
                            update = { view -> view.updateAppWidgetSize(null, w, h, w, h) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        BasicText(
                            text = stringResource(R.string.widget_change),
                            style = NoirType.EventPlace,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .tappable {
                                    auto = false
                                    controller.clear()
                                    widgetId = -1
                                }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Picker(
    providers: List<AppWidgetProviderInfo>,
    loaded: Boolean,
    onPick: (AppWidgetProviderInfo) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BasicText(
            text = stringResource(if (loaded && providers.isEmpty()) R.string.widget_none else R.string.widget_choose),
            style = NoirType.Body,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
        ) {
            items(providers, key = { it.provider.flattenToString() }) { info ->
                ProviderRow(info) { onPick(info) }
            }
        }
    }
}

@Composable
private fun ProviderRow(info: AppWidgetProviderInfo, onPick: () -> Unit) {
    val context = LocalContext.current
    val label = remember(info) {
        runCatching { info.loadLabel(context.packageManager) }.getOrDefault("Widget")
    }
    val preview: ImageBitmap? = remember(info) {
        runCatching { info.loadPreviewImage(context, 0)?.toBitmap()?.asImageBitmap() }.getOrNull()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .tappable(onClick = onPick)
            .padding(vertical = 14.dp),
    ) {
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        BasicText(text = label, style = NoirType.EventTitle)
    }
}
