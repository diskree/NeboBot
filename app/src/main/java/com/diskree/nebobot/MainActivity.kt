package com.diskree.nebobot

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.diskree.nebobot.databinding.ActivityMainBinding
import org.jsoup.Jsoup

class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val handler: Handler by lazy { Handler(mainLooper) }
    private var menu: Menu? = null
    private var currentActiveBot: Bot? = null
    private var notificationCounter = 0

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
                val confirmLink = document.selectFirst("a[href*=confirmLink]")
                if (confirmLink != null) {
                    loadGameUrl(confirmLink.attr("href"))
                    return
                }
                val header = document.selectFirst("div.hdr>div>span>b")?.text()
                if (header != "Лабиринт") {
                    loadGameUrl("doors")
                    return
                }
                val victoryDoor = document.selectFirst("img.doorSel[src$=door_iron_big.png]")
                if (victoryDoor != null) {
                    val coinsCount = document.selectFirst("div.m5.cntr>span.amount>img[src$=mn_iron.png]+span")?.text()
                    val xpCount = document.selectFirst("div.m5.cntr>span.amount>img[src$=star.png]+span")?.text()
                    val snowflakesCount = document.selectFirst("div.m5.cntr>span.amount>img[src$=snowflake.png]+span")?.text()
                    NotificationManagerCompat.from(this@MainActivity).notify(
                            notificationCounter++,
                            NotificationCompat.Builder(this@MainActivity, currentActiveBot!!.name)
                                    .setSmallIcon(R.drawable.ic_done)
                                    .setContentText("\uD83E\uDE99 + $coinsCount, ⭐️+ $xpCount, ❄️ + $snowflakesCount")
                                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                    .build()
                    )
                }
                val buyKeyLink = document.selectFirst("a.tdu[href*=buyKeyLink]")
                if (buyKeyLink != null) {
                    loadGameUrl("doors/" + buyKeyLink.attr("href"))
                    return
                }
                val lossDoor = document.selectFirst("img.doorSel[src$=door_wall.png]")
                if (lossDoor != null || victoryDoor != null) {
                    loadGameUrl("doors")
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
        this.menu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.auto_labyrinth -> {
            if (currentActiveBot == Bot.AUTO_LABYRINTH) {
                currentActiveBot = null
                item.setTitle(R.string.bot_auto_labyrinth_start)
                setTitle(R.string.app_name)
                binding.webViewLock.isVisible = false
                binding.webView.stopLoading()
            } else {
                startBot()
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
            if (currentActiveBot != null) {
                binding.webViewLock.isVisible = true
            }
            binding.webView.loadUrl(GAME_URL_PREFIX + path)
        }, (300..500).random().toLong())
    }

    private fun startBot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this@MainActivity, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(POST_NOTIFICATIONS), POST_NOTIFICATION_PERMISSION_REQUEST_ID)
            return
        }

        currentActiveBot = Bot.AUTO_LABYRINTH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val descriptionText = getString(R.string.bot_auto_labyrinth_status)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(currentActiveBot!!.name, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        menu?.findItem(R.id.auto_labyrinth)?.setTitle(R.string.bot_auto_labyrinth_stop)
        setTitle(R.string.bot_auto_labyrinth_status)
        binding.webViewLock.isVisible = true
        binding.webView.reload()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val isGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (requestCode == POST_NOTIFICATION_PERMISSION_REQUEST_ID && isGranted) {
            startBot()
        }
    }

    enum class Bot {
        AUTO_LABYRINTH
    }

    companion object {
        private const val GAME_URL_PREFIX = "http://nebomobi2.spaces-games.com/"
        private const val POST_NOTIFICATION_PERMISSION_REQUEST_ID = 10
    }
}