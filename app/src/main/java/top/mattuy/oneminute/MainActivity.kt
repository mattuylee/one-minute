package top.mattuy.oneminute

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import top.mattuy.oneminute.data.InstalledApp
import top.mattuy.oneminute.service.ServiceStatus
import top.mattuy.oneminute.ui.MainViewModel
import top.mattuy.oneminute.ui.WaitOverlayView
import top.mattuy.oneminute.ui.DiagnosticsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OneMinuteTheme { App() } }
    }

    @Composable
    private fun App(model: MainViewModel = viewModel()) {
        val settings by model.settings.collectAsStateWithLifecycle()
        val apps by model.apps.collectAsStateWithLifecycle()
        val error by model.error.collectAsStateWithLifecycle()
        val connected by ServiceStatus.connected.collectAsStateWithLifecycle()
        val enabled by ServiceStatus.enabled.collectAsStateWithLifecycle()
        val serviceError by ServiceStatus.error.collectAsStateWithLifecycle()
        var picking by rememberSaveable { mutableStateOf(false) }
        var editingDuration by rememberSaveable { mutableStateOf(false) }
        var editingReturnGrace by rememberSaveable { mutableStateOf(false) }
        var showingHelp by rememberSaveable { mutableStateOf(false) }
        var showingPreview by rememberSaveable { mutableStateOf(false) }
        var showingDiagnostics by rememberSaveable { mutableStateOf(false) }
        val snack = remember { SnackbarHostState() }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            model.refreshApps()
            ServiceStatus.refreshPermission(this@MainActivity)
        }
        LaunchedEffect(error) {
            error?.let { snack.showSnackbar(it); model.dismissError() }
        }
        BackHandler(picking || showingPreview || editingDuration || editingReturnGrace || showingDiagnostics) {
            picking = false; showingPreview = false; editingDuration = false; editingReturnGrace = false
            showingDiagnostics = false
        }
        val config = settings
        if (showingDiagnostics) {
            DiagnosticsScreen { showingDiagnostics = false }
            return
        }
        if (showingPreview && config != null) {
            Preview(config.seconds) { showingPreview = false }
            return
        }
        if (editingDuration && config != null) {
            DurationScreen(config.seconds, onDismiss = { editingDuration = false }) {
                model.setSeconds(it); editingDuration = false
            }
            return
        }
        if (editingReturnGrace && config != null) {
            DurationScreen(config.returnGraceMinutes, returnGrace = true, onDismiss = { editingReturnGrace = false }) {
                model.setReturnGraceMinutes(it); editingReturnGrace = false
            }
            return
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Button(onClick = { picking = !picking }, enabled = config != null && apps != null,
                            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
                            Text(if (picking) "完成选择" else "+  选择应用", fontSize = 16.sp)
                        }
                        if (!picking) Text("只在打开前稍等，不限制使用时长", Modifier.fillMaxWidth().padding(top = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 12.sp)
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (picking) "选择应用" else "稍等", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(if (picking) "给容易顺手打开的应用，留一点距离" else "ONE MINUTE · 给冲动一点时间", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                    }
                    TextButton(onClick = { showingHelp = true }) { Text("使用说明") }
                }
                if (config == null || apps == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text("正在读取应用与设置…", Modifier.padding(16.dp))
                        }
                    }
                } else if (picking) {
                    var search by rememberSaveable { mutableStateOf("") }
                    OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        placeholder = { Text("搜索应用名称或包名") }, singleLine = true, shape = RoundedCornerShape(18.dp))
                    Text("已选择 ${config.packages.size} 个 · 自动保存", Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val filtered = apps.orEmpty().filter { it.label.contains(search, true) || it.packageName.contains(search, true) }
                    LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppRow(app, app.packageName in config.packages, onToggle = { model.select(app.packageName, it) })
                        }
                        if (filtered.isEmpty()) item { Text("没有找到应用", Modifier.padding(24.dp)) }
                        item { Text("桌面、系统设置和拨号应用不会出现在列表中。", Modifier.padding(8.dp),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                } else {
                    val selected = apps.orEmpty().filter { it.packageName in config.packages }
                    LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        item {
                            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                                    Text("让每一次打开\n都出于你的选择。", fontSize = 26.sp, lineHeight = 37.sp,
                                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(config.seconds.toString(), fontSize = 50.sp, fontWeight = FontWeight.Light,
                                            color = MaterialTheme.colorScheme.primary)
                                        Text(" 秒等待", Modifier.weight(1f).padding(start = 8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        FilledTonalButton(onClick = { editingDuration = true }, shape = CircleShape) { Text("调整") }
                                    }
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("返回免等待", fontWeight = FontWeight.Medium)
                                            Text("离开 ${config.returnGraceMinutes} 分钟内返回", fontSize = 12.sp)
                                        }
                                        TextButton(onClick = { editingReturnGrace = true }) { Text("设置") }
                                    }
                                }
                            }
                        }
                        item {
                            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(8.dp).background(if (connected && enabled != false && serviceError == null) Color(0xFF67917B) else Color(0xFFD5A452), CircleShape))
                                        Text(when {
                                            serviceError != null -> "等待服务异常"
                                            enabled == false -> "开启等待保护"
                                            connected -> "等待保护已开启"
                                            enabled == true -> "服务未连接"
                                            else -> "无法确认授权状态"
                                        }, Modifier.weight(1f).padding(start = 10.dp), fontWeight = FontWeight.Medium)
                                        TextButton(onClick = { showingHelp = true }) { Text(if (enabled == true) "管理" else "去开启") }
                                    }
                                    Text(serviceError ?: when {
                                        enabled == false -> "系统无障碍开关已关闭，需要你手动开启。"
                                        connected -> "打开选定应用时，先留一点时间想一想。"
                                        enabled == true -> "开关已开启，服务尚未连接。持续如此可查看运行诊断。"
                                        else -> "无法读取系统开关，请到无障碍设置核对。"
                                    },
                                        fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { showingDiagnostics = true }) { Text("运行诊断") }
                                }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("需要稍等的应用", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                                Text("${selected.size} 个", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (selected.isEmpty()) item {
                            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("从一个应用开始", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                    Text("选一个你经常无意识打开的应用。\n不必一次改变所有习惯。", Modifier.padding(top = 10.dp),
                                        fontSize = 13.sp, lineHeight = 22.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        items(selected, key = { it.packageName }) { app -> AppRow(app, true, "等待 ${config.seconds} 秒 · 离开 ${config.returnGraceMinutes} 分钟内返回免等待") { model.select(app.packageName, it) } }
                        item { TextButton(onClick = { showingPreview = true }, modifier = Modifier.fillMaxWidth()) { Text("体验一下等待页") } }
                    }
                }
            }
        }
        if (showingHelp) AlertDialog(
            onDismissRequest = { showingHelp = false },
            title = { Text("开始之前，只需一步") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("在系统无障碍设置中找到「稍等」，开启服务。它会识别当前应用，并显示等待页。", lineHeight = 23.sp)
                    Text("不读取页面文字或输入内容，不截图、不联网。你可以随时关闭服务。", lineHeight = 23.sp)
                    Text("如果 Android 16 提示「受限设置」，先到系统的应用信息页，按系统提示允许受限设置，再回来开启。", fontSize = 13.sp, lineHeight = 21.sp)
                    TextButton(onClick = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }) { Text("打开应用信息") }
                    HorizontalDivider()
                    Text("等待结束后点「继续」才能使用。每个应用放行后，离开不超过设定的免等待时长，返回直接使用；超过则重新计时。锁屏算离开，通知中心不算。未点继续就离开，下次重计；服务重启清空放行记录。", fontSize = 13.sp, lineHeight = 21.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showingHelp = false; openAccessibility() }) { Text("前往无障碍设置") } },
            dismissButton = { TextButton(onClick = { showingHelp = false }) { Text("稍后") } }
        )
    }

    private fun openAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

@Composable
private fun OneMinuteTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFBBAAF3), onPrimary = Color(0xFF292038),
        primaryContainer = Color(0xFF332B44), onPrimaryContainer = Color(0xFFE7DFF9),
        background = Color(0xFF1D1B22), surface = Color(0xFF28252F),
        onSurface = Color(0xFFF4F1FA), onSurfaceVariant = Color(0xFFB8B0C5)
    ) else lightColorScheme(
        primary = Color(0xFF7563C7), onPrimary = Color.White,
        primaryContainer = Color(0xFFECE8FA), onPrimaryContainer = Color(0xFF403553),
        background = Color(0xFFF7F6FB), surface = Color.White,
        onSurface = Color(0xFF302A40), onSurfaceVariant = Color(0xFF7C748B)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun AppRow(app: InstalledApp, selected: Boolean, subtitle: String = app.packageName, onToggle: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().clickable { onToggle(!selected) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val bitmap = remember(app.packageName, app.icon) { app.icon.toBitmap(96, 96).asImageBitmap() }
            Image(bitmap, contentDescription = null, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(app.label, fontWeight = FontWeight.Medium, maxLines = 2)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, lineHeight = 16.sp)
            }
            Checkbox(checked = selected, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun DurationScreen(initial: Int, returnGrace: Boolean = false, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var input by rememberSaveable { mutableStateOf(initial.toString()) }
    val value = input.toIntOrNull()
    val range = if (returnGrace) 1..60 else 1..600
    val unit = if (returnGrace) "分钟" else "秒"
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Button(enabled = value != null && value in range, onClick = { value?.let(onSave) },
                modifier = Modifier.navigationBarsPadding().imePadding().padding(24.dp).fillMaxWidth().height(54.dp)) { Text("保存") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            TextButton(onClick = onDismiss) { Text("取消") }
            Text(if (returnGrace) "返回免等待时长" else "打开前，稍等多久？", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(if (returnGrace) "应用放行后，离开不超过这段时间，返回免等待。所有选定应用共用此设置。" else "所有选定应用使用相同的等待时长。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (if (returnGrace) listOf(1, 5, 15, 30) else listOf(10, 30, 60, 120)).forEach { duration ->
                    FilterChip(selected = value == duration, onClick = { input = duration.toString() }, label = { Text("$duration$unit") })
                }
            }
            OutlinedTextField(input, { input = it.filter(Char::isDigit).take(4) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (returnGrace) "自定义分钟数" else "自定义秒数") },
                isError = value == null || value !in range, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text(if (returnGrace) "1～60 分钟，默认 5 分钟" else "1～600 秒，建议从 60 秒开始", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Preview(seconds: Int, onClose: () -> Unit) {
    val deadline = remember { SystemClock.elapsedRealtime() + seconds * 1000L }
    var remaining by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(deadline) {
        while (remaining > 0) {
            remaining = ((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999).div(1000).toInt()
            if (remaining > 0) delay((deadline - SystemClock.elapsedRealtime()).coerceIn(1L, 1000L))
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Text("等待页预览", Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        AndroidView(factory = { WaitOverlayView(it, "示例应用", null, onContinue = onClose, onCancel = onClose) },
            update = { it.update(remaining, seconds) }, modifier = Modifier.fillMaxSize())
    }
}
