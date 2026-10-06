package top.mattuy.oneminute.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.text.Collator
import java.util.Locale

data class InstalledApp(val packageName: String, val label: String, val icon: Drawable)

object AppCatalog {
    fun protectedPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        val phones = pm.queryIntentActivities(Intent(Intent.ACTION_DIAL), PackageManager.MATCH_DEFAULT_ONLY)
        return (homes + phones).map { it.activityInfo.packageName }.toSet() +
            setOf(context.packageName, "android", "com.android.systemui", "com.android.settings",
                "com.android.permissioncontroller", "com.google.android.permissioncontroller")
    }

    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val protected = protectedPackages(context)
        val collator = Collator.getInstance(Locale.CHINA)
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName !in protected }
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
}
