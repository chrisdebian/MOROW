package uk.org.mapthepaths.android


import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oauth.signpost.OAuthConsumer
import oauth.signpost.basic.DefaultOAuthConsumer
import oauth.signpost.basic.DefaultOAuthProvider


class OAuthManager(private val webView: WebView, private val scope: CoroutineScope, val onReceivedAccessToken: (OAuthConsumer) -> Unit, val onError: (String) -> Unit) {

    private val callbackUrl = "mapthepaths://oauth/"

    private var consumer = DefaultOAuthConsumer(Constants.KEY, Constants.SECRET)

    private var provider = DefaultOAuthProvider("${Constants.OAUTH_SERVER}/oauth/request_token",
            "${Constants.OAUTH_SERVER}/oauth/access_token",
            "${Constants.OAUTH_SERVER}/oauth/authorize"
    )

    init {
        webView.webViewClient = OSMOAuthWebViewClient { verifier ->

            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        provider.retrieveAccessToken(consumer, verifier)
                    }

                    onReceivedAccessToken(consumer)

                } catch (e: Exception) {


                    onError(e.toString())

                }
            }

        }
    }

    fun getRequestToken() {
        scope.launch {
            try {
                var url = ""
                withContext(Dispatchers.IO) {
                   url = provider.retrieveRequestToken(consumer, callbackUrl)
                }

                webView.loadUrl(url)

            } catch (e: Exception) {

                onError(e.toString())

            }
        }

        // onReceivedRequestToken receives oauth_token and oauth_verifier parameters
    }
}
