package com.lottiefiles.sample.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Monitors CPU usage for the application.
 * Uses multiple monitoring strategies with fallbacks.
 */
class CpuMonitor(private val context: Context) {
    private val TAG = "CpuMonitor"
    
    // Atomic variables for thread safety
    private val monitoringActive = AtomicBoolean(false)
    private val latestCpuUsage = AtomicReference<Float>(0f)
    
    // Thread management
    private var cpuExecutor: ScheduledExecutorService? = null
    private var cpuMonitorTask: ScheduledFuture<*>? = null
    
    // Monitoring method tracking
    private var currentMonitorMethod = MonitoringMethod.PROC_STAT
    private var methodSwitchCount = 0
    
    // Activity Manager for fallback monitoring
    private val activityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    
    enum class MonitoringMethod {
        PROC_STAT,      // Primary: Read from /proc/stat (requires root on newer Android)
        PROC_SELF_STAT, // Secondary: Read from /proc/self/stat (more limited info)
        TOP_COMMAND,    // Tertiary: Use 'top' command output (requires root) 
        ACTIVITY_MANAGER // Fallback: Use ActivityManager metrics (less accurate)
    }
    
    /**
     * Start CPU usage monitoring
     */
    fun startMonitoring() {
        if (monitoringActive.getAndSet(true)) return
        
        if (cpuExecutor == null || cpuExecutor?.isShutdown == true) {
            cpuExecutor = Executors.newSingleThreadScheduledExecutor()
        }
        
        currentMonitorMethod = MonitoringMethod.PROC_STAT
        methodSwitchCount = 0
        
        cpuMonitorTask = cpuExecutor?.scheduleWithFixedDelay(
            { updateCpuUsage() },
            0,
            1000,
            TimeUnit.MILLISECONDS
        )
        
        Log.d(TAG, "CPU monitoring started")
    }
    
    /**
     * Stop CPU usage monitoring
     */
    fun stopMonitoring() {
        if (!monitoringActive.getAndSet(false)) return
        
        cpuMonitorTask?.cancel(false)
        cpuExecutor?.apply {
            shutdown()
            try {
                if (!awaitTermination(1, TimeUnit.SECONDS)) {
                    shutdownNow()
                }
            } catch (e: InterruptedException) {
                shutdownNow()
            }
        }
        cpuExecutor = null
        
        Log.d(TAG, "CPU monitoring stopped")
    }
    
    /**
     * Get the current CPU usage percentage
     */
    fun getCpuUsage(): Float = latestCpuUsage.get()
    
    /**
     * Update CPU usage using the current monitoring method
     */
    private fun updateCpuUsage() {
        if (!monitoringActive.get()) return
        
        try {
            val cpuUsage = when (currentMonitorMethod) {
                MonitoringMethod.PROC_STAT -> getCpuUsageFromProcStat()
                MonitoringMethod.PROC_SELF_STAT -> getCpuUsageFromProcSelfStat()
                MonitoringMethod.TOP_COMMAND -> getCpuUsageFromTopCommand()
                MonitoringMethod.ACTIVITY_MANAGER -> getCpuUsageFromActivityManager()
            }
            
            if (cpuUsage >= 0) {
                latestCpuUsage.set(cpuUsage)
            } else {
                // If current method failed, try next method
                switchToNextMonitorMethod()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating CPU usage: ${e.message}")
            // Try next method on error
            switchToNextMonitorMethod()
        }
    }
    
    /**
     * Rotate through monitoring methods on failure
     */
    private fun switchToNextMonitorMethod() {
        methodSwitchCount++
        
        // Don't switch more than 3 times to avoid thrashing
        if (methodSwitchCount > 3) return
        
        currentMonitorMethod = when (currentMonitorMethod) {
            MonitoringMethod.PROC_STAT -> MonitoringMethod.PROC_SELF_STAT
            MonitoringMethod.PROC_SELF_STAT -> MonitoringMethod.TOP_COMMAND
            MonitoringMethod.TOP_COMMAND -> MonitoringMethod.ACTIVITY_MANAGER
            MonitoringMethod.ACTIVITY_MANAGER -> MonitoringMethod.ACTIVITY_MANAGER
        }
        
        Log.d(TAG, "Switched to CPU monitoring method: $currentMonitorMethod")
    }
    
    /**
     * Method 1: Read from /proc/stat
     * Most accurate but requires root access on newer Android
     */
    private fun getCpuUsageFromProcStat(): Float {
        try {
            // First reading
            val statFile = RandomAccessFile("/proc/stat", "r")
            val firstLine = statFile.readLine() ?: throw IOException("Could not read /proc/stat")
            statFile.close()
            
            val firstCpuData = extractCpuData(firstLine)
            val firstTotalTime = firstCpuData.sum()
            val firstIdleTime = firstCpuData[3] + firstCpuData[4] // idle + iowait
            
            // Wait a bit for second sample
            Thread.sleep(100)
            
            // Second reading
            val statFile2 = RandomAccessFile("/proc/stat", "r")
            val secondLine = statFile2.readLine() ?: throw IOException("Could not read /proc/stat")
            statFile2.close()
            
            val secondCpuData = extractCpuData(secondLine)
            val secondTotalTime = secondCpuData.sum()
            val secondIdleTime = secondCpuData[3] + secondCpuData[4] // idle + iowait
            
            // Calculate CPU usage
            val totalTimeDiff = secondTotalTime - firstTotalTime
            val idleTimeDiff = secondIdleTime - firstIdleTime
            
            if (totalTimeDiff <= 0) return 0f
            
            val cpuUsagePercentage = 100f * (1f - idleTimeDiff.toFloat() / totalTimeDiff.toFloat())
            return cpuUsagePercentage
        } catch (e: FileNotFoundException) {
            // Handle missing file/permission error
            Log.e(TAG, "Cannot access /proc/stat, switching method: ${e.message}")
            return -1f
        } catch (e: Exception) {
            Log.e(TAG, "Error reading /proc/stat: ${e.message}")
            return -1f
        }
    }
    
    /**
     * Helper to parse CPU data from /proc/stat line
     */
    private fun extractCpuData(line: String): LongArray {
        val parts = line.split("\\s+".toRegex()).dropWhile { it.isEmpty() || it == "cpu" }
        return LongArray(10) { index -> 
            if (index < parts.size) parts[index].toLongOrNull() ?: 0L else 0L 
        }
    }
    
    /**
     * Method 2: Read from /proc/self/stat
     * More limited but might work on some devices without root
     */
    private fun getCpuUsageFromProcSelfStat(): Float {
        try {
            val pid = Process.myPid()
            val procFile = RandomAccessFile("/proc/$pid/stat", "r")
            val firstLine = procFile.readLine() ?: throw IOException("Could not read /proc/$pid/stat")
            procFile.close()
            
            val parts = firstLine.split(" ")
            if (parts.size < 17) {
                return -1f
            }
            
            // Extract utime (14) and stime (15) values (user and system CPU time)
            val utime = parts[13].toLong()
            val stime = parts[14].toLong()
            val firstCpuTime = utime + stime
            
            // Wait a bit
            Thread.sleep(100)
            
            val procFile2 = RandomAccessFile("/proc/$pid/stat", "r")
            val secondLine = procFile2.readLine() ?: throw IOException("Could not read /proc/$pid/stat")
            procFile2.close()
            
            val parts2 = secondLine.split(" ")
            if (parts2.size < 17) {
                return -1f
            }
            
            val utime2 = parts2[13].toLong()
            val stime2 = parts2[14].toLong()
            val secondCpuTime = utime2 + stime2
            
            // Calculate CPU usage based on time diff and number of cores
            val cpuTimeDiff = secondCpuTime - firstCpuTime
            val numCores = Runtime.getRuntime().availableProcessors()
            
            // Convert to percentage (considering time slice and number of cores)
            // The value is normalized to a percentage of a single CPU core
            val cpuUsage = (cpuTimeDiff * 1000f / 100f) / numCores
            
            return cpuUsage.coerceIn(0f, 100f)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading /proc/self/stat: ${e.message}")
            return -1f
        }
    }
    
    /**
     * Method 3: Use the 'top' command (requires root)
     */
    private fun getCpuUsageFromTopCommand(): Float {
        // This needs root access, so likely won't work on most devices
        // Only used as a fallback method
        try {
            val pid = Process.myPid()
            val process = Runtime.getRuntime().exec("top -n 1 -p $pid")
            
            val reader = process.inputStream.bufferedReader()
            var line: String?
            var cpuUsage = -1f
            
            // Skip header lines
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains(pid.toString()) == true) {
                    // Extract CPU percentage (format varies by Android version)
                    // Example format: "$pid user app_name state cpu_percentage memory ..."
                    val parts = line!!.trim().split("\\s+".toRegex())
                    if (parts.size >= 9) {
                        // CPU percentage is typically in column 9 on many versions
                        cpuUsage = parts[8].replace("%", "").toFloatOrNull() ?: -1f
                    }
                    break
                }
            }
            
            reader.close()
            process.destroy()
            
            return cpuUsage
        } catch (e: Exception) {
            Log.e(TAG, "Error using top command: ${e.message}")
            return -1f
        }
    }
    
    /**
     * Method 4: Use ActivityManager (least accurate but most compatible)
     */
    private fun getCpuUsageFromActivityManager(): Float {
        try {
            val pid = Process.myPid()
            val pids = IntArray(1) { pid }
            
            // Get CPU usage information
            val cpuInfoMap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activityManager.getProcessCpuUsage(pids)
            } else {
                null
            }
            
            // On older devices, we approximate based on memory pressure
            if (cpuInfoMap == null) {
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                
                // Get running app process info
                val processInfo = activityManager.runningAppProcesses?.find { it.pid == pid }
                
                // Approximate CPU usage based on memory pressure and process importance
                val memoryPressure = memInfo.totalMem / memInfo.availMem.toFloat()
                val importanceFactor = when (processInfo?.importance) {
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> 0.8f
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> 0.6f
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> 0.4f
                    else -> 0.2f
                }
                
                // Simple approximation based on memory pressure and process importance
                return (memoryPressure * importanceFactor * 100f).coerceIn(0f, 100f)
            }
            
            // On newer devices, use the CPU usage info
            val cpuInfo = cpuInfoMap[pid]
            return cpuInfo?.let { it.toFloat() * 100f } ?: 0f
        } catch (e: Exception) {
            Log.e(TAG, "Error getting CPU usage from ActivityManager: ${e.message}")
            return 0f // Return 0 as fallback
        }
    }
    
    /**
     * Check if the device needs root access for CPU monitoring
     */
    fun needsRootForFullMonitoring(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && 
               !File("/proc/stat").canRead()
    }
} 