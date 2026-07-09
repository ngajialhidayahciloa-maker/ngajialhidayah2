package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {

  // For WebView File Chooser
  private var uploadMessage: ValueCallback<Array<Uri>>? = null

  private val fileChooserLauncher = registerForActivityResult(
      ActivityResultContracts.StartActivityForResult()
  ) { result ->
      if (result.resultCode == RESULT_OK) {
          val data: Intent? = result.data
          val results = if (data != null) {
              val dataString = data.dataString
              val clipData = data.clipData
              if (clipData != null) {
                  val uris = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                  uris
              } else if (dataString != null) {
                  arrayOf(Uri.parse(dataString))
              } else {
                  null
              }
          } else {
              null
          }
          uploadMessage?.onReceiveValue(results)
      } else {
          uploadMessage?.onReceiveValue(null)
      }
      uploadMessage = null
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AppMainScreen(
          fileChooserLauncher = fileChooserLauncher,
          getUploadCallback = { uploadMessage },
          setUploadCallback = { uploadMessage = it }
        )
      }
    }
  }
}

// Check internet connection
fun isInternetAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
    return when {
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        else -> false
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppMainScreen(
    fileChooserLauncher: ActivityResultLauncher<Intent>,
    getUploadCallback: () -> ValueCallback<Array<Uri>>?,
    setUploadCallback: (ValueCallback<Array<Uri>>?) -> Unit
) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("alhidayah_prefs", Context.MODE_PRIVATE) }
    
    // Default Web App URL
    val defaultUrl = "file:///android_asset/index.html"
    
    var currentUrl by remember { mutableStateOf(sharedPref.getString("web_url", defaultUrl) ?: defaultUrl) }
    var isOnboardingCompleted by remember { mutableStateOf(sharedPref.getBoolean("onboarding_completed", false)) }
    var appState by remember { mutableStateOf(if (isOnboardingCompleted) "webview" else "splash") }
    
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("Al-Hidayah Digital") }
    var loadProgress by remember { mutableStateOf(0) }
    var isOffline by remember { mutableStateOf(!isInternetAvailable(context)) }
    
    // Setup Settings dialog
    var showSettingsDialog by remember { mutableStateOf(false) }
    var urlInputText by remember { mutableStateOf(currentUrl) }

    // Handle back presses to navigate web history instead of closing app
    BackHandler(enabled = appState == "webview" && canGoBack) {
        webViewInstance?.goBack()
    }

    AnimatedContent(
        targetState = appState,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) with fadeOut(animationSpec = tween(400))
        },
        label = "AppStateTransition"
    ) { state ->
        when (state) {
            "splash" -> SplashScreen(onNext = { appState = "onboarding" })
            "onboarding" -> OnboardingScreen(
                onCompleted = {
                    sharedPref.edit().putBoolean("onboarding_completed", true).apply()
                    isOnboardingCompleted = true
                    appState = "webview"
                }
            )
            "webview" -> {
                Scaffold(
                    topBar = {
                        Column {
                            // Top bar styled elegantly
                            TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = IslamicTealPrimary,
                                    titleContentColor = Color.White
                                ),
                                title = {
                                    Column {
                                        Text(
                                            text = pageTitle,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            maxLines = 1
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isOffline) Color.Red else Color.Green)
                                            )
                                            Text(
                                                text = if (isOffline) "Offline - Perlu Internet" else "Online (Sinergi Cloud)",
                                                fontSize = 10.sp,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    IconButton(onClick = {
                                        isOffline = !isInternetAvailable(context)
                                        webViewInstance?.reload()
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", tint = Color.White)
                                    }
                                    IconButton(onClick = {
                                        webViewInstance?.loadUrl(currentUrl)
                                    }) {
                                        Icon(Icons.Default.Home, contentDescription = "Halaman Awal", tint = Color.White)
                                    }
                                    IconButton(onClick = {
                                        urlInputText = currentUrl
                                        showSettingsDialog = true
                                    }) {
                                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan URL", tint = Color.White)
                                    }
                                }
                            )
                            
                            // Dynamic loading progress indicator
                            if (loadProgress < 100) {
                                LinearProgressIndicator(
                                    progress = loadProgress / 100f,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = IslamicGoldAccent,
                                    trackColor = IslamicTealPrimary
                                )
                            } else {
                                HorizontalDivider(color = IslamicTealSecondary.copy(alpha = 0.3f), thickness = 1.dp)
                            }
                        }
                    },
                    bottomBar = {
                        // Navigation controls
                        BottomAppBar(
                            containerColor = IslamicTealPrimary,
                            contentColor = Color.White,
                            tonalElevation = 8.dp,
                            modifier = Modifier.height(64.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    enabled = canGoBack,
                                    onClick = { webViewInstance?.goBack() }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Kembali",
                                        tint = if (canGoBack) Color.White else Color.White.copy(alpha = 0.3f)
                                    )
                                }
                                
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = IslamicGoldAccent, contentColor = Color.Black),
                                    shape = RoundedCornerShape(20.dp),
                                    onClick = {
                                        isOffline = !isInternetAvailable(context)
                                        webViewInstance?.loadUrl(currentUrl)
                                    }
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sinkron Data", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                IconButton(
                                    enabled = canGoForward,
                                    onClick = { webViewInstance?.goForward() }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Maju",
                                        tint = if (canGoForward) Color.White else Color.White.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        val isLocalUrl = currentUrl.startsWith("file://")
                        if (isOffline && !isLocalUrl) {
                            OfflinePlaceholderScreen {
                                isOffline = !isInternetAvailable(context)
                                if (!isOffline) {
                                    webViewInstance?.loadUrl(currentUrl)
                                }
                            }
                        } else {
                            WebViewContainer(
                                url = currentUrl,
                                onWebViewCreated = { webViewInstance = it },
                                onNavigationStateChanged = { back, forward, title, progress ->
                                    canGoBack = back
                                    canGoForward = forward
                                    if (title.isNotEmpty() && !title.startsWith("http")) {
                                        pageTitle = title
                                    }
                                    loadProgress = progress
                                },
                                fileChooserLauncher = fileChooserLauncher,
                                getUploadCallback = getUploadCallback,
                                setUploadCallback = setUploadCallback
                            )
                        }
                    }
                }
            }
        }
    }

    // Settings dialogue to change or reset URL
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Pengaturan URL Aplikasi", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ubah URL server jika Anda memiliki deployment custom atau ingin masuk ke mode pengujian.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = urlInputText,
                        onValueChange = { urlInputText = it },
                        label = { Text("URL Web App") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { urlInputText = defaultUrl },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Default", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (urlInputText.isNotEmpty()) {
                            currentUrl = urlInputText
                            sharedPref.edit().putString("web_url", urlInputText).apply()
                            showSettingsDialog = false
                            webViewInstance?.loadUrl(urlInputText)
                        }
                    }
                ) {
                    Text("Simpan & Muat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun SplashScreen(onNext: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(IslamicTealPrimary, IslamicTealSecondary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Circle Shadow for Logo
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_app_logo_1783497608662),
                    contentDescription = "Al-Hidayah Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            Text(
                text = "AL-HIDAYAH DIGITAL",
                color = IslamicGoldAccent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Buku Penghubung Digital & Monitoring Santri",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            CircularProgressIndicator(
                color = IslamicGoldAccent,
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = IslamicGoldAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.shadow(4.dp, RoundedCornerShape(24.dp))
            ) {
                Text("Lanjutkan", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = "Lanjutkan")
            }
        }
    }
}

@Composable
fun OnboardingScreen(onCompleted: () -> Unit) {
    val scrollState = rememberScrollState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8F7))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Banner Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .shadow(4.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_onboarding_banner_1783497625389),
                    contentDescription = "Onboarding Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )
                Text(
                    text = "Aplikasi Pendamping Ibadah & Quran",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Fitur Unggulan Al-Hidayah",
                    color = IslamicTealPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Feature list
                FeatureCard(
                    icon = Icons.Default.MenuBook,
                    title = "Monitoring Ngaji Santri",
                    desc = "Pantau progres jilid Iqro, halaman, dan surah Al-Qur'an santri secara real-time langsung oleh Ustadz/Ustadzah."
                )
                
                FeatureCard(
                    icon = Icons.Default.Star,
                    title = "Evaluasi Ibadah Harian",
                    desc = "Pantau ibadah shalat fardhu harian, tahajud, zikir, dhuha, witir, serta bakti sosial santri kepada orang tua."
                )
                
                FeatureCard(
                    icon = Icons.Default.Edit,
                    title = "Tanda Tangan Digital",
                    desc = "Sistem tanda tangan digital yang memudahkan orang tua memberikan paraf verifikasi laporan mingguan santri."
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onCompleted,
                    colors = ButtonDefaults.buttonColors(containerColor = IslamicTealPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(4.dp, RoundedCornerShape(24.dp))
                ) {
                    Text("Masuk ke Aplikasi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Login, contentDescription = "Login")
                }
                
                Text(
                    text = "© 2026 Al-Hidayah Digital • Sinergi Guru & Orang Tua",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun FeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(IslamicTealLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = IslamicTealPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = IslamicTealPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    color = Color.DarkGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun OfflinePlaceholderScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9F9)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFDE8E8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = "Offline",
                    tint = Color.Red,
                    modifier = Modifier.size(48.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Koneksi Terputus",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = IslamicTealPrimary
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "Al-Hidayah Digital memerlukan koneksi internet aktif untuk sinkronisasi data real-time dengan server cloud kami. Silakan periksa jaringan Wi-Fi atau Paket Data Anda.",
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = Color.Gray,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = IslamicTealPrimary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Coba Lagi")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hubungkan Ulang", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContainer(
    url: String,
    onWebViewCreated: (WebView) -> Unit,
    onNavigationStateChanged: (back: Boolean, forward: Boolean, title: String, progress: Int) -> Unit,
    fileChooserLauncher: ActivityResultLauncher<Intent>,
    getUploadCallback: () -> ValueCallback<Array<Uri>>?,
    setUploadCallback: (ValueCallback<Array<Uri>>?) -> Unit
) {
    val context = LocalContext.current
    
    // Check and request camera permission dynamically if the web app needs it
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // WebView Settings Optimization
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mediaPlaybackRequiresUserGesture = false
                    
                    // Hardware Acceleration
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }

                // Scroll and focus configurations to optimize scrolling in emulator & touch devices
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                isFocusable = true
                isFocusableInTouchMode = true
                
                @android.annotation.SuppressLint("ClickableViewAccessibility")
                setOnTouchListener { v, _ ->
                    v.requestFocus()
                    false
                }
                
                // Maintain session cookies
                CookieManager.getInstance().setAcceptCookie(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val requestUrl = request?.url?.toString() ?: return false
                        
                        // Handle standard external links (like WhatsApp parent chat) in a system intent
                        if (requestUrl.startsWith("whatsapp://") || requestUrl.startsWith("https://api.whatsapp.com") || requestUrl.startsWith("https://wa.me")) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
                                context.startActivity(intent)
                                return true
                            } catch (e: Exception) {
                                return false
                            }
                        }
                        
                        // Maintain internal navigation inside WebView
                        view?.loadUrl(requestUrl)
                        return true
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onNavigationStateChanged(
                            view?.canGoBack() ?: false,
                            view?.canGoForward() ?: false,
                            view?.title ?: "",
                            0
                        )
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onNavigationStateChanged(
                            view?.canGoBack() ?: false,
                            view?.canGoForward() ?: false,
                            view?.title ?: "",
                            100
                        )
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onNavigationStateChanged(
                            view?.canGoBack() ?: false,
                            view?.canGoForward() ?: false,
                            view?.title ?: "",
                            newProgress
                        )
                    }

                    // Handle camera / file dialog inputs from HTML5 web elements
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        getUploadCallback()?.onReceiveValue(null)
                        setUploadCallback(filePathCallback)

                        // Request camera permission dynamically if not granted yet
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }

                        val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                        
                        try {
                            fileChooserLauncher.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            setUploadCallback(null)
                            return false
                        }
                        return true
                    }

                    // Dynamically grant camera/mic web permission requests from React applets
                    override fun onPermissionRequest(request: PermissionRequest) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            request.grant(request.resources)
                        }
                    }
                }

                loadUrl(url)
                onWebViewCreated(this)
            }
        },
        update = { webView ->
            // Update URL only if it has changed from current loading page
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
