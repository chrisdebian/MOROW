package uk.org.mapthepaths.android


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat

object ServiceInitiator {

    fun startService(ctx: Context) {
        val intent = Intent(ctx, GpsService::class.java)
        ContextCompat.startForegroundService(ctx, intent)
    }

    fun bindService(ctx: Context, callback: (GpsService)->Unit) : ServiceConnection {
        val intent = Intent(ctx, GpsService::class.java)
        val sConn = object: ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                callback ((binder as GpsService.GpsBinder).getService())
            }

            override fun onServiceDisconnected(name: ComponentName?) {
            }
        }

        ctx.bindService(intent, sConn, Context.BIND_AUTO_CREATE)
        return sConn
    }
}