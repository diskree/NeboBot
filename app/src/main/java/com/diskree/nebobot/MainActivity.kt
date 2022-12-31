package com.diskree.nebobot

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Vibrator
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.diskree.nebobot.databinding.ActivityMainBinding
import org.jsoup.Jsoup

class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val handler: Handler by lazy { Handler(mainLooper) }
    private var currentActiveBot: Bot? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.loadUrl(binding.webView.url ?: return@setOnRefreshListener)
        }
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; LE2123) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Mobile Safari/537.36"
        binding.webView.addJavascriptInterface(HTMLViewer(), "HtmlViewer")
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                binding.swipeRefresh.isRefreshing = false
                if (currentActiveBot == null || view == null || url == null) {
                    super.onPageFinished(view, url)
                    return
                }
                binding.webView.loadUrl("javascript:window.HtmlViewer.showHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');")
            }
        }
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, progress: Int) {
                binding.loaderView.isVisible = progress < 100
                binding.loaderView.progress = progress
            }
        }
        binding.webView.loadUrl("https://spcs.life")
    }

    inner class HTMLViewer {

        @JavascriptInterface
        fun showHTML(html: String?) {
            val document = Jsoup.parse(html ?: return)
            if (currentActiveBot == Bot.AUTO_LABYRINTH) {
                val header = document.selectFirst("div.hdr>div>span>b")?.text()
                if (header != "Лабиринт") {
                    loadGameUrl("doors")
                    return
                }
                val failMessage = document.selectFirst("span.notify")
                if (failMessage != null) {
                    vibrate(100)
                    loadGameUrl("doors")
                    return
                }
                val roomNumber = document.selectFirst("div.m5>b.amount")
                if (roomNumber != null && roomNumber.text() == "10") {
                    vibrate(500)
                    binding.webViewLock.isVisible = false
                    return
                }
                val doorNumber = (1..3).random()
                val door = document.selectFirst("div.m5>a[href*=doorLink$doorNumber]")
                if (door != null) {
                    loadGameUrl("doors/" + door.attr("href"))
                    return
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.auto_labyrinth -> {
            if (currentActiveBot == Bot.AUTO_LABYRINTH) {
                currentActiveBot = null
                item.setTitle(R.string.bot_auto_labyrinth_start)
                setTitle(R.string.app_name)
                binding.webViewLock.isVisible = false
            } else {
                currentActiveBot = Bot.AUTO_LABYRINTH
                item.setTitle(R.string.bot_auto_labyrinth_stop)
                setTitle(R.string.bot_auto_labyrinth_status)
                binding.webViewLock.isVisible = true
                binding.webView.reload()
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
            return
        }
        super.onBackPressed()
    }

    private fun loadGameUrl(path: String) {
        handler.postDelayed({
            binding.webViewLock.isVisible = true
            binding.webView.loadUrl(GAME_URL_PREFIX + path)
        }, (300..500).random().toLong())
    }

    private fun vibrate(millis: Long) {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(millis)
    }

    enum class Bot {
        AUTO_LABYRINTH
    }

    companion object {
        private const val GAME_URL_PREFIX = "http://nebomobi2.spaces-games.com/"
    }
}