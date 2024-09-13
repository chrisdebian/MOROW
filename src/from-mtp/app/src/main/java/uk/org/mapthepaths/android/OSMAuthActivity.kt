package uk.org.mapthepaths.android

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope


class OSMAuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        setContentView(wv)
        val oauthManager = OAuthManager(wv, lifecycleScope, {
            val intent = Intent()
            intent.putExtra("oauthConsumer", it)
            setResult(RESULT_OK, intent)
            finish()
        }, { e -> Util.showAlertDialog(this, e) })
        oauthManager.getRequestToken()
    }
}