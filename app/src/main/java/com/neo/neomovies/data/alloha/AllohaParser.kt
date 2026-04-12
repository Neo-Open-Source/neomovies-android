package com.neo.neomovies.data.alloha

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.net.URL
import java.util.Locale

class AllohaParser(context: Context) {

    private val userAgents = (0..19).map {
        val os = listOf(
            "Windows NT 10.0; Win64; x64",
            "Windows NT 11.0; Win64; x64",
            "Macintosh; Intel Mac OS X 10_15_7",
            "Macintosh; Intel Mac OS X 14_4_1",
            "X11; Linux x86_64",
            "X11; Ubuntu; Linux x86_64",
        ).random()
        val cv = (130..135).random()
        val fv = (130..136).random()
        when ((0..2).random()) {
            0 -> "Mozilla/5.0 ($os) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$cv.0.0.0 Safari/537.36"
            1 -> "Mozilla/5.0 ($os; rv:$fv.0) Gecko/20100101 Firefox/$fv.0"
            else -> "Mozilla/5.0 ($os) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$cv.0.0.0 Safari/537.36 Edg/$cv.0.0.0"
        }
    }
    private var uaIndex = userAgents.indices.random()
    private val userAgent get() = userAgents[uaIndex]

    var lastIframeUrl: String = ""

    fun rotateUserAgent() {
        uaIndex = (uaIndex + 1) % userAgents.size
        webView.settings.userAgentString = userAgent
        Log.d("AllohaParser", "UA rotated to: ${userAgent.take(60)}")
    }

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = userAgent
        keepScreenOn = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)

        webViewClient = object : WebViewClient() {
            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.proceed()
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun getDefaultVideoPoster(): Bitmap {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.TRANSPARENT)
                }
            }
        }
    }

    interface Callback {
        fun onHlsLinksReceived(json: String, extraHeaders: Map<String, String>)
        fun onConfigUpdate(edgeHash: String, ttlSeconds: Int, extraHeaders: Map<String, String>)
        fun onM3u8Refreshed(url: String, extraHeaders: Map<String, String>)
        fun onStreamHeadersUpdated(extraHeaders: Map<String, String>) {}
        fun onError(error: String)
    }

    @SuppressLint("AddJavascriptInterface")
    fun parse(iframeUrl: String, callback: Callback) {
        lastIframeUrl = iframeUrl
        Handler(Looper.getMainLooper()).post {
            webView.onResume()
            webView.resumeTimers()

            webView.removeJavascriptInterface("AndroidBridge")
            webView.addJavascriptInterface(object : Any() {
                private var isParsed = false

                @JavascriptInterface
                fun onReady(jsonResponse: String, headersJson: String) {
                    if (isParsed) return
                    isParsed = true
                    val extraHeaders = parseHeaders(headersJson)
                    Handler(Looper.getMainLooper()).post {
                        CookieManager.getInstance().flush()
                        callback.onHlsLinksReceived(jsonResponse, extraHeaders)
                    }
                }

                @JavascriptInterface
                fun onConfigUpdate(edgeHash: String, ttl: Int, headersJson: String) {
                    if (!isParsed) return
                    val extraHeaders = parseHeaders(headersJson)
                    Log.d("AllohaParser", "config_update hash=$edgeHash ttl=${ttl}s")
                    Handler(Looper.getMainLooper()).post {
                        callback.onConfigUpdate(edgeHash, ttl, extraHeaders)
                    }
                }

                @JavascriptInterface
                fun onM3u8Refreshed(url: String, headersJson: String) {
                    if (!isParsed) return
                    val extraHeaders = parseHeaders(headersJson)
                    Log.d("AllohaParser", "m3u8 refreshed url=$url")
                    Handler(Looper.getMainLooper()).post {
                        callback.onM3u8Refreshed(url, extraHeaders)
                    }
                }

                @JavascriptInterface
                fun onLog(msg: String) {
                    Log.d("AllohaParserJS", msg)
                }

                private fun parseHeaders(headersJson: String): Map<String, String> {
                    val map = mutableMapOf<String, String>()
                    try {
                        val obj = JSONObject(headersJson)
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            map[k] = obj.getString(k)
                        }
                    } catch (_: Exception) {
                    }
                    return map
                }

                @JavascriptInterface
                fun onStreamHeaders(headersJson: String) {
                    if (!isParsed) return
                    val extraHeaders = parseHeaders(headersJson)
                    Handler(Looper.getMainLooper()).post {
                        CookieManager.getInstance().flush()
                        callback.onStreamHeadersUpdated(extraHeaders)
                    }
                }
            }, "AndroidBridge")

            val wrapperHtml = buildWrapperHtml(iframeUrl)
            val parsedUrl = URL(iframeUrl)
            val baseUrl = "${parsedUrl.protocol}://${parsedUrl.host.lowercase(Locale.ROOT)}/"
            webView.loadDataWithBaseURL(baseUrl, wrapperHtml, "text/html", "UTF-8", null)
        }
    }

    fun release() {
        Handler(Looper.getMainLooper()).post {
            webView.destroy()
        }
    }

    private fun buildWrapperHtml(iframeUrl: String): String = """
        <html>
        <body style="margin:0;padding:0;background:black;">
            <iframe id="alloha_iframe" src="$iframeUrl" width="100%" height="100%" frameborder="0" allowfullscreen></iframe>
            <script>
                try {
                    Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
                    Object.defineProperty(document, 'hidden', { get: () => false });
                } catch(e) {}

                var iframe = document.getElementById('alloha_iframe');
                iframe.onload = function() {
                    try {
                        var iframeWin = iframe.contentWindow;

                        try {
                            Object.defineProperty(iframeWin.document, 'visibilityState', { get: () => 'visible' });
                            Object.defineProperty(iframeWin.document, 'hidden', { get: () => false });
                        } catch(e) {}

                        var bnsiData = null;
                        var capturedHeaders = {};
                        var isDone = false;
                        var lastM3u8Url = null;

                        var _pushHdrTimer = null;
                        function schedulePushStreamHeaders() {
                            if (!isDone) return;
                            if (_pushHdrTimer) clearTimeout(_pushHdrTimer);
                            _pushHdrTimer = setTimeout(function() {
                                _pushHdrTimer = null;
                                try {
                                    AndroidBridge.onStreamHeaders(JSON.stringify(capturedHeaders));
                                } catch(e) {}
                            }, 40);
                        }

                        function putHeader(name, value) {
                            if (!name || !value) return;
                            capturedHeaders[String(name).toLowerCase()] = String(value);
                            schedulePushStreamHeaders();
                        }

                        function checkDone() {
                            if (isDone) return;
                            var hasAuth = false, hasAccept = false;
                            for (var k in capturedHeaders) {
                                if (k === 'authorizations') hasAuth = true;
                                if (k === 'accepts-controls') hasAccept = true;
                            }
                            if (bnsiData && hasAuth && hasAccept) {
                                isDone = true;
                                AndroidBridge.onReady(bnsiData, JSON.stringify(capturedHeaders));
                            }
                        }

                        putHeader('origin', iframeWin.location.origin);
                        putHeader('referer', iframeWin.location.origin + '/');
                        putHeader('user-agent', iframeWin.navigator.userAgent);
                        putHeader('accept', '*/*');
                        putHeader('sec-fetch-dest', 'empty');
                        putHeader('sec-fetch-mode', 'cors');
                        putHeader('sec-fetch-site', 'cross-site');

                        var originalOpen = iframeWin.XMLHttpRequest.prototype.open;
                        iframeWin.XMLHttpRequest.prototype.open = function(method, url) {
                            this._allohaUrl = url;
                            this.addEventListener('load', function() {
                                var rUrl = this.responseURL || '';
                                if (rUrl.indexOf('/bnsi/') !== -1 && !isDone) {
                                    bnsiData = this.responseText;
                                    checkDone();
                                }
                                if (isDone && rUrl.indexOf('master.m3u8') !== -1 && rUrl !== lastM3u8Url) {
                                    lastM3u8Url = rUrl;
                                    try {
                                        AndroidBridge.onM3u8Refreshed(rUrl, JSON.stringify(capturedHeaders));
                                    } catch(e) {
                                        AndroidBridge.onLog('onM3u8Refreshed error: ' + e);
                                    }
                                }
                            });
                            originalOpen.apply(this, arguments);
                        };

                        var originalSetHeader = iframeWin.XMLHttpRequest.prototype.setRequestHeader;
                        iframeWin.XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
                            putHeader(name, value);
                            var url = this._allohaUrl || '';
                            if (url.indexOf('.m3u8') !== -1 || url.indexOf('.ts') !== -1) {
                                checkDone();
                            }
                            return originalSetHeader.apply(this, arguments);
                        };

                        var _fallbackHost = null;
                        var _primaryHost = null;
                        var _fallbackMasterUrl = null;
                        function extractFallbackHost() {
                            if (_fallbackHost || !bnsiData) {
                                AndroidBridge.onLog('extractFallback skip: fallback=' + !!_fallbackHost + ' bnsi=' + !!bnsiData);
                                return;
                            }
                            try {
                                var d = JSON.parse(bnsiData);
                                var src = d.hlsSource;
                                if (src && src[0] && src[0].quality) {
                                    var q = src[0].quality;
                                    var key = Object.keys(q)[0];
                                    var urls = q[key].split(' or ');
                                    if (urls.length > 1) {
                                        var m = urls[0].match(/https?:\/\/([^\/]+)/);
                                        if (m) _primaryHost = m[1];
                                        var fb = urls[1].trim();
                                        var m2 = fb.match(/https?:\/\/([^\/]+)/);
                                        if (m2) { _fallbackHost = m2[1]; _fallbackMasterUrl = fb; }
                                        AndroidBridge.onLog('CDN hosts: primary=' + _primaryHost + ' fallback=' + _fallbackHost);
                                    } else {
                                        AndroidBridge.onLog('extractFallback: no " or " in url: ' + q[key].substring(0, 80));
                                    }
                                } else {
                                    AndroidBridge.onLog('extractFallback: no hlsSource quality');
                                }
                            } catch(e) { AndroidBridge.onLog('extractFallback err: ' + e); }
                        }

                        var originalFetch = iframeWin.fetch;
                        iframeWin.fetch = function(input, init) {
                            try {
                                var url = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
                                if (init && init.headers) {
                                    if (typeof init.headers.forEach === 'function') {
                                        init.headers.forEach(function(v, k) { putHeader(k, v); });
                                    } else {
                                        for (var hk in init.headers) { putHeader(hk, init.headers[hk]); }
                                    }
                                }
                                if (url && (url.indexOf('.m3u8') !== -1 || url.indexOf('.ts') !== -1)) {
                                    checkDone();
                                    extractFallbackHost();
                                    if (_primaryHost && _fallbackHost && url.indexOf(_primaryHost) !== -1) {
                                        var self = this;
                                        var fallbackUrl;
                                        if (url.indexOf('master.m3u8') !== -1 && _fallbackMasterUrl) {
                                            fallbackUrl = _fallbackMasterUrl;
                                        } else {
                                            fallbackUrl = url.replace(_primaryHost, _fallbackHost);
                                        }
                                        return originalFetch.apply(self, [input, init]).then(function(resp) {
                                            if (resp.status === 500 || resp.status === 503 || resp.status === 403) {
                                                AndroidBridge.onLog('fetch ' + resp.status + ' on primary, retrying on fallback: ' + fallbackUrl.substring(0, 60));
                                                return originalFetch.apply(iframeWin, [fallbackUrl, init]);
                                            }
                                            return resp;
                                        });
                                    }
                                }
                            } catch(e) { AndroidBridge.onLog('fetch intercept err: ' + e); }
                            return originalFetch.apply(this, arguments);
                        };

                        var _origSend = iframeWin.WebSocket.prototype.send;
                        var _allohaWs = null;
                        var _heartbeatTimer = null;
                        var _sessionStart = Date.now();
                        var _lastEdgeHash = null;

                        function startHeartbeat(ws) {
                            if (_heartbeatTimer) clearInterval(_heartbeatTimer);
                            _heartbeatTimer = setInterval(function() {
                                if (!isDone) return;
                                if (!ws || ws.readyState !== 1) return;
                                var t = Math.floor((Date.now() - _sessionStart) / 1000);
                                try {
                                    _origSend.call(ws, JSON.stringify({
                                        type: 'playing',
                                        current_time: t,
                                        resolution: '1080',
                                        track_id: '1',
                                        speed: 1,
                                        subtitle: 0,
                                        ts: Date.now()
                                    }));
                                    AndroidBridge.onLog('Heartbeat sent t=' + t);
                                } catch(e) {
                                    AndroidBridge.onLog('Heartbeat err: ' + e);
                                }
                            }, 25000);
                        }

                        iframeWin.WebSocket.prototype.send = function(data) {
                            if (!this.__alloha_hooked) {
                                this.__alloha_hooked = true;
                                var ws = this;
                                _allohaWs = ws;
                                _sessionStart = Date.now();
                                AndroidBridge.onLog('WSS hooked via send()');

                                ws.addEventListener('message', function(event) {
                                    try {
                                        var msg = JSON.parse(event.data);
                                        if (msg && msg.type === 'config_update' && msg.edge_hash) {
                                            if (msg.edge_hash !== _lastEdgeHash) {
                                                _lastEdgeHash = msg.edge_hash;
                                                var ttl = msg.ttl || 120;
                                                capturedHeaders['accepts-controls'] = msg.edge_hash;
                                                AndroidBridge.onLog('config_update hash=' + msg.edge_hash + ' ttl=' + ttl);
                                                AndroidBridge.onConfigUpdate(msg.edge_hash, ttl, JSON.stringify(capturedHeaders));
                                            }
                                        }
                                    } catch(e) {}
                                });

                                ws.addEventListener('close', function(e) {
                                    AndroidBridge.onLog('WSS closed code=' + (e.code || '?') + ' reason=' + (e.reason || ''));
                                    if (_allohaWs === ws) {
                                        _allohaWs = null;
                                        if (_heartbeatTimer) clearInterval(_heartbeatTimer);
                                    }
                                });

                                startHeartbeat(ws);
                            }
                            return _origSend.call(this, data);
                        };

                        var OrigWS = iframeWin.WebSocket;
                        iframeWin.WebSocket = function(url, protocols) {
                            var ws = protocols ? new OrigWS(url, protocols) : new OrigWS(url);
                            ws.addEventListener('open', function() {
                                AndroidBridge.onLog('WSS opened');
                            });
                            return ws;
                        };
                        iframeWin.WebSocket.prototype = OrigWS.prototype;
                        iframeWin.WebSocket.CONNECTING = OrigWS.CONNECTING;
                        iframeWin.WebSocket.OPEN = OrigWS.OPEN;
                        iframeWin.WebSocket.CLOSING = OrigWS.CLOSING;
                        iframeWin.WebSocket.CLOSED = OrigWS.CLOSED;

                        setInterval(function() {
                            if (!isDone) {
                                var playBtn = iframeWin.document.querySelector('.allplay__play-btn');
                                if (playBtn) playBtn.click();
                                var video = iframeWin.document.querySelector('video');
                                if (video) {
                                    video.muted = true;
                                    if (video.paused) video.play().catch(function(){});
                                }
                            }
                        }, 1500);

                    } catch(e) { AndroidBridge.onLog('JS Error: ' + e); }
                };
            </script>
        </body>
        </html>
    """.trimIndent()
}
