package uk.org.mapthepaths.android

import android.webkit.WebView
import android.webkit.WebViewClient

class OSMOAuthWebViewClient (val onReceivedRequestToken: (String) -> Unit) : WebViewClient() {

    private val callbackUrl = "mapthepaths://oauth/"

    override fun shouldOverrideUrlLoading(view: WebView, stringUrl: String) : Boolean {

        // this will run when we get the onReceivedRequestToken url

        val get = hashMapOf<String, String>()
        if(stringUrl.startsWith(callbackUrl)) {

            val components = stringUrl.split('?')
            if(components.size == 2) {
                components[1].split('&').forEach {
                    val kv = it.split('=')
                    if(kv.size == 2) {
                        get[kv[0]] = kv[1]
                    }
                }
            }
            if(get["oauth_verifier"]!=null) {
                onReceivedRequestToken(get["oauth_verifier"]!!)
                return true
            }
        }
        return false
    }
}