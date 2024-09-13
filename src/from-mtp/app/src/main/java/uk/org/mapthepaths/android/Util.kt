package uk.org.mapthepaths.android

import android.app.AlertDialog
import android.content.Context
import android.os.Environment
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat

object Util {
    fun showAlertDialog(ctx: Context, msg: String) {
        AlertDialog.Builder(ctx).setPositiveButton("OK", null).setMessage(msg).show()
    }

    fun logException(e: Exception, where: String) {
        val loggerFile = "${Environment.getExternalStorageDirectory().absolutePath}/mapthepaths_err.txt"
        val pwe = PrintWriter(FileWriter(loggerFile, true), true)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        pwe.println("\n\nException in $where at ${sdf.format(System.currentTimeMillis())}:\n")
        e.printStackTrace(pwe)
        pwe.close()
    }
}