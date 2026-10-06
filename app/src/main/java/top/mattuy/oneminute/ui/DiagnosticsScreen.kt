package top.mattuy.oneminute.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mattuy.oneminute.service.ServiceDiagnostics

@Composable
fun DiagnosticsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(generation) {
        report = null; error = null; copied = false
        try { report = withContext(Dispatchers.IO) { ServiceDiagnostics.report(context) } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) {
            Log.e("OneMinute.Diagnostics", "Failed to load diagnostic report", failure)
            error = "诊断读取失败：${failure.javaClass.simpleName}。请重试。"
        }
    }
    Scaffold(bottomBar = {
        Button(onClick = {
            report?.let {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("稍等运行诊断", it))
                copied = true
            }
        }, enabled = report != null,
            modifier = Modifier.navigationBarsPadding().padding(24.dp).fillMaxWidth()) {
            Text(if (copied) "已复制诊断" else "复制诊断")
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClose) { Text("返回") }
                TextButton(onClick = { generation++ }) { Text("刷新诊断") }
            }
            Text("运行诊断", style = MaterialTheme.typography.headlineMedium)
            Text("服务掉线后先打开这里，复制诊断以排查原因。记录仅保存在本机，不会自动上传。", fontSize = 14.sp)
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }) { Text("打开应用信息与电池设置") }
            Text("如系统提供自启动或后台运行选项，可检查是否限制了「稍等」。这不能代替崩溃排查，也不能保证服务不被终止。", fontSize = 13.sp)
            if (report == null && error == null) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            report?.let { SelectionContainer { Text(it, fontSize = 12.sp) } }
            Spacer(Modifier.height(16.dp))
        }
    }
}
