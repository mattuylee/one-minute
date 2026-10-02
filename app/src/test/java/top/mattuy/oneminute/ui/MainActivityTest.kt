package top.mattuy.oneminute.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.mattuy.oneminute.MainActivity
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun `first launch shows setup and saves chosen duration`() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("调整").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("从一个应用开始").assertIsDisplayed()
        compose.onNodeWithText("开启等待保护").assertIsDisplayed()
        compose.onNodeWithText("调整").performClick()
        compose.onNodeWithText("30秒").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("30").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("30").assertIsDisplayed()
        compose.onNodeWithText("+  选择应用").performClick()
        compose.onNodeWithText("搜索应用名称或包名").assertIsDisplayed()
        compose.onNodeWithText("完成选择").performClick()
        compose.onNodeWithText("使用说明").performClick()
        compose.onNodeWithText("前往无障碍设置").assertIsDisplayed()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `home screen renders with native graphics`() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("调整").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("从一个应用开始").assertIsDisplayed()
        capture("home-light")
    }

    @Test fun `return grace accepts custom minutes and retains saved settings`() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("返回免等待").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("返回免等待时长").assertIsDisplayed()
        compose.onNodeWithText("自定义分钟数").performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("自定义分钟数").performTextReplacement("61")
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("自定义分钟数").performTextReplacement("7")
        capture("return-grace")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("离开 7 分钟内返回").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("离开 7 分钟内返回").assertIsDisplayed()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("7").assertIsDisplayed()
    }

    @Test fun `diagnostics can be opened and copied without accessibility access`() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("运行诊断").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("运行诊断").performScrollTo().performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("无障碍开关：关闭", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("复制诊断").performClick()
        compose.onNodeWithText("已复制诊断").assertIsDisplayed()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
            org.junit.Assert.assertTrue(clipboard.primaryClip!!.getItemAt(0).text.contains("本进程服务连接"))
        }
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val file = File("build/reports/ui/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }
}
