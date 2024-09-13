

package uk.org.mapthepaths.android

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class UserGuide: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wv = WebView(this)
        setContentView(wv)
        wv.loadUrl("file:///android_asset/userguide.html")
    }
}