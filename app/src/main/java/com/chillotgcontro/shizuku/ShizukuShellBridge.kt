package com.chillotgcontro.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object ShizukuSupport {
    const val PERMISSION_REQUEST_CODE = 7402
    private const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"

    fun hasServer(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = hasServer() && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun isReady(): Boolean = hasServer() && hasPermission()

    fun status(): String = when {
        !hasServer() -> "Shizuku chưa chạy (hoặc cần khởi động lại sau reboot)"
        hasPermission() -> "Shizuku đã chạy · quyền shell/root đã được cấp"
        else -> "Shizuku đang chạy · chưa cấp quyền cho ChillOTG"
    }

    fun isManagerInstalled(context: Context): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
    }.isSuccess

    fun launchManager(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE) ?: return false
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }
}

object ShizukuShellBridge {
    data class EventQueue(
        val lines: LinkedBlockingQueue<String>,
        val errors: LinkedBlockingQueue<String>
    )

    private val lock = Any()
    @Volatile private var remote: IShizukuInputService? = null
    private var connection: ServiceConnection? = null
    private var args: Shizuku.UserServiceArgs? = null
    private var latch: CountDownLatch? = null

    fun connect(context: Context): IShizukuInputService {
        if (!ShizukuSupport.isReady()) throw IllegalStateException("Khởi động Shizuku và cấp quyền cho ChillOTG trước")
        synchronized(lock) {
            remote?.let { if (runCatching { it.asBinder().isBinderAlive }.getOrDefault(false)) return it }
            remote = null
            val wait = CountDownLatch(1)
            latch = wait
            val userArgs = args ?: Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShizukuInputUserService::class.java.name)
            ).tag("chillotg-input-shell").version(1).daemon(false).debuggable(false).also { args = it }
            val newConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    remote = binder?.let(IShizukuInputService.Stub::asInterface)
                    latch?.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    remote = null
                    latch?.countDown()
                }
            }
            connection = newConnection
            try {
                Shizuku.bindUserService(userArgs, newConnection)
            } catch (t: Throwable) {
                connection = null
                latch = null
                throw IllegalStateException("Không bind được Shizuku UserService: ${t.message ?: t.javaClass.simpleName}", t)
            }
            // Do not wait on the main thread; mapping service calls this from its worker.
            val connected = wait.await(15, TimeUnit.SECONDS)
            latch = null
            if (!connected) throw IllegalStateException("Shizuku chưa trả kết nối UserService trong 15 giây")
            return remote ?: throw IllegalStateException("Shizuku UserService chưa sẵn sàng")
        }
    }

    fun startGetevent(context: Context): EventQueue {
        val lines = LinkedBlockingQueue<String>(512)
        val errors = LinkedBlockingQueue<String>(8)
        connect(context).startGetevent(object : IShizukuEventListener.Stub() {
            override fun onLine(line: String?) {
                if (!line.isNullOrBlank()) {
                    if (!lines.offer(line)) {
                        lines.poll()
                        lines.offer(line)
                    }
                }
            }

            override fun onError(message: String?) {
                errors.offer(message ?: "getevent Shizuku bị dừng")
            }
        })
        return EventQueue(lines, errors)
    }

    fun execute(context: Context, vararg command: String): String = connect(context).execute(command)

    fun stopGetevent() {
        runCatching { remote?.stopGetevent() }
    }

    fun close(context: Context) {
        stopGetevent()
        synchronized(lock) {
            val currentArgs = args
            val currentConnection = connection
            if (currentArgs != null && currentConnection != null) {
                runCatching { Shizuku.unbindUserService(currentArgs, currentConnection, true) }
            }
            remote = null
            connection = null
            latch = null
            args = null
        }
    }
}
