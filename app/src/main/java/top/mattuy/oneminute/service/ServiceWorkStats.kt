package top.mattuy.oneminute.service

import android.os.SystemClock

/** Main-thread counters, read by diagnostics. No sampling loop and no per-event disk writes. */
internal object ServiceWorkStats {
    private val since = SystemClock.elapsedRealtime()
    @Volatile var events = 0L
    @Volatile var scans = 0L
    @Volatile var roots = 0L
    @Volatile var ticks = 0L
    fun summary(): String = "本进程工作计数（非耗电测量）：\n" +
        "观察时长 ${(SystemClock.elapsedRealtime() - since) / 1000} 秒；事件 $events；窗口查询 $scans；根节点读取 $roots；计时回调 $ticks"
}
