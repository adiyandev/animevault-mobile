package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.Anime
import com.example.ui.DetailState
import com.example.ui.HomeState
import com.example.ui.MainViewModel
import com.example.ui.User
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import java.io.IOException
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AnimeApp()
            }
        }
    }
}

private fun animePosterModel(anime: Anime): Any =
    anime.posterUrl ?: R.drawable.ic_launcher_foreground

fun uploadToCloudinary(
    context: android.content.Context,
    fileUri: android.net.Uri,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit
) {
    val cloudName = "dmljhhe1l"
    val uploadPreset = "animevault"
    val url = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"

    val client = OkHttpClient()

    try {
        val inputStream = context.contentResolver.openInputStream(fileUri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()

        if (bytes == null) {
            onFailure("Could not read image data")
            return
        }

        val mediaType = okhttp3.MediaType.Companion.run { "image/jpeg".toMediaTypeOrNull() }
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", uploadPreset)
            .addFormDataPart(
                "file",
                "profile_image.jpg",
                RequestBody.create(mediaType, bytes)
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onFailure(e.localizedMessage ?: "Network error during upload")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        try {
                            val json = JSONObject(responseBody)
                            val secureUrl = json.optString("secure_url", json.optString("url", ""))
                            if (secureUrl.isNotEmpty()) {
                                onSuccess(secureUrl)
                            } else {
                                onFailure("Upload succeeded but secure_url was empty")
                            }
                        } catch (e: Exception) {
                            onFailure("Failed to parse Cloudinary response: ${e.localizedMessage}")
                        }
                    } else {
                        onFailure("Empty response from Cloudinary")
                    }
                } else {
                    onFailure("Cloudinary upload failed: Code ${response.code} - ${response.message}")
                }
            }
        })
    } catch (e: Exception) {
        onFailure("Failed to read selected file: ${e.localizedMessage}")
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1016)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = "https://github.com/animevaultofficial/animevaultofficial.github.io/blob/main/logo.png?raw=true",
                contentDescription = "AnimeVault Logo",
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "ANIME VAULT",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your Ultimate Anime Companion",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp
            )
        }
    }
}

sealed class Screen(val route: String, val icon: ImageVector, val title: String) {
    object Home : Screen("home", Icons.Default.Home, "Home")
    object Search : Screen("search", Icons.Default.Search, "Search")
    object Dramas : Screen("dramas", Icons.Default.PlayArrow, "Shows")
    object Schedule : Screen("schedule", Icons.Default.DateRange, "Schedule")
    object Profile : Screen("profile", Icons.Default.Person, "Profile")
    object Community : Screen("community", Icons.Default.Share, "Community")
    object Stats : Screen("stats", Icons.Default.Star, "Stats")
    object Collections : Screen("collections", Icons.Default.List, "Collections")
    object Settings : Screen("settings", Icons.Default.Settings, "Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeApp() {
    var showSplash by rememberSaveable { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onTimeout = { showSplash = false })
        return
    }

    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    
    val context = LocalContext.current
    val activityIntent = remember { (context as? android.app.Activity)?.intent }
    val initialNavigateTo = remember { activityIntent?.getStringExtra("navigate_to") }

    LaunchedEffect(initialNavigateTo) {
        if (!initialNavigateTo.isNullOrBlank()) {
            navController.navigate(initialNavigateTo)
            activityIntent?.removeExtra("navigate_to")
        }
    }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route, Screen.Search.route, Screen.Dramas.route, Screen.Schedule.route, Screen.Profile.route,
        Screen.Community.route, Screen.Stats.route, Screen.Collections.route, Screen.Settings.route
    )

    val onAnimeClick: (Int) -> Unit = { id ->
        navController.navigate("detail/$id")
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = "https://github.com/animevaultofficial/animevaultofficial.github.io/blob/main/logo.png?raw=true",
                            contentDescription = "AnimeVault Logo",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AnimeVault",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Your Ultimate Media Hub",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                val drawerItems = listOf(
                    Screen.Home,
                    Screen.Search,
                    Screen.Dramas,
                    Screen.Schedule,
                    Screen.Profile,
                    Screen.Community,
                    Screen.Stats,
                    Screen.Collections,
                    Screen.Settings
                )
                
                drawerItems.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title, fontWeight = FontWeight.Bold) },
                        selected = currentRoute == item.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (showBottomBar) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = when (currentRoute) {
                                    Screen.Home.route -> "AnimeVault"
                                    Screen.Search.route -> "Search Library"
                                    Screen.Dramas.route -> "TV Shows & Dramas"
                                    Screen.Schedule.route -> "Airing Schedule"
                                    Screen.Profile.route -> "My Profile"
                                    Screen.Community.route -> "Community Board"
                                    Screen.Stats.route -> "Otaku Statistics"
                                    Screen.Collections.route -> "Collections"
                                    Screen.Settings.route -> "Settings"
                                    else -> "AnimeVault"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch { drawerState.open() }
                            }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 8.dp
                    ) {
                        val items = listOf(Screen.Home, Screen.Search, Screen.Dramas, Screen.Schedule, Screen.Profile)
                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                selected = currentRoute == screen.route,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(viewModel, onAnimeClick = onAnimeClick)
                }
                composable(Screen.Search.route) {
                    SearchScreen(viewModel, onAnimeClick = onAnimeClick, onNavigateToShows = { navController.navigate(Screen.Dramas.route) })
                }
                composable(Screen.Dramas.route) {
                    DramasMoviesPage(viewModel, navController, onAnimeClick = onAnimeClick)
                }
                composable(Screen.Schedule.route) {
                    SchedulePage(viewModel, onAnimeClick = onAnimeClick)
                }
                composable(Screen.Profile.route) {
                    ProfilePage(viewModel, navController, onAnimeClick = onAnimeClick)
                }
                composable(Screen.Community.route) {
                    CommunityScreen(viewModel, onAnimeClick = onAnimeClick)
                }
                composable(Screen.Stats.route) {
                    StatsScreen(viewModel)
                }
                composable(Screen.Collections.route) {
                    CollectionsScreen(viewModel, onAnimeClick = onAnimeClick)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel)
                }
                composable("detail/{id}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id")?.toIntOrNull()
                    if (id != null) {
                        DetailScreen(
                            id = id,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onPlay = { ep -> navController.navigate("player/$id/$ep") },
                            onGenreClick = {
                                navController.navigate(Screen.Search.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
                composable("player/{id}/{ep}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id")?.toIntOrNull()
                    val ep = backStackEntry.arguments?.getString("ep")?.toIntOrNull() ?: 1
                    if (id != null) {
                        PlayerScreen(animeId = id, episode = ep, viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityScreen(viewModel: MainViewModel, onAnimeClick: (Int) -> Unit) {
    var posts by remember { mutableStateOf(listOf(
        Triple("LegendaryOtaku", "Just finished watching Frieren! The animation and story are an absolute masterpiece. 🌸", 152),
        Triple("DemonSlayerFan", "Who is hyped for the upcoming movie? The trailer looks insane!", 84),
        Triple("KawaiiChan", "Can anyone recommend some wholesome slice-of-life comedies?", 42)
    )) }
    var newPostText by remember { mutableStateOf("") }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Share something with the community!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPostText,
                        onValueChange = { newPostText = it },
                        placeholder = { Text("What's on your mind?") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (newPostText.isNotBlank()) {
                                posts = listOf(Triple("You", newPostText, 1)) + posts
                                newPostText = ""
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Post")
                    }
                }
            }
        }
        
        items(posts) { (user, text, likes) ->
            var likeCount by remember { mutableStateOf(likes) }
            var isLiked by remember { mutableStateOf(false) }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(user.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(user, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Posted just now", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            isLiked = !isLiked
                            likeCount += if (isLiked) 1 else -1
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(likeCount.toString(), fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        IconButton(onClick = { /* Comment */ }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MailOutline, contentDescription = "Comment", tint = Color.Gray)
                                Spacer(Modifier.width(4.dp))
                                Text("Comment", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val reminders by viewModel.reminders.collectAsState()

    val user = currentUser ?: User("LegendaryOtaku", "guest@vault.com")
    val totalWatchMinutes = user.totalWatchTime
    val watchHours = totalWatchMinutes / 60
    val watchMins = totalWatchMinutes % 60
    val topGenre = favorites.flatMap { it.genres ?: emptyList() }
        .groupingBy { it.name }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key ?: "N/A"
    val topAnime = favorites.firstOrNull()?.displayTitle ?: continueWatching.firstOrNull()?.first?.displayTitle ?: "N/A"
    val watchedCount = continueWatching.size
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Level & XP Progression Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("LEVEL ${user.level} OTAKU 👑", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Total XP: ${user.xp}/${user.xpNeeded} to next tier", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Spacer(Modifier.height(12.dp))
                    val progress = user.xp.toFloat() / user.xpNeeded.toFloat()
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )
                }
            }
        }
        
        // Watch Time & Reminder stats grid
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📺 Watch Time", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                        Text("${watchHours}h ${watchMins}m", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔔 Reminders", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                        Text("${reminders.size}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Red)
                    }
                }
            }
        }
        
        // Activity Heatmap
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Activity Heatmap", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (row in 0..4) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (col in 0..14) {
                                    val alpha = remember { (0..3).random() * 0.3f + 0.1f }
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Anime Wrapped
        item {
            val favoriteAnime = favorites.firstOrNull() ?: continueWatching.firstOrNull()?.first
            val topCharacter = favoriteAnime?.let { viewModel.getAnimeCharacters(it.malId).firstOrNull() } ?: "N/A"

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anime Wrapped 🌟", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Favorite Genre", color = Color.Gray)
                        Text(topGenre, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Top Anime", color = Color.Gray)
                        Text(topAnime, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Favorite Character", color = Color.Gray)
                        Text(topCharacter, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionsScreen(viewModel: MainViewModel, onAnimeClick: (Int) -> Unit) {
    var collectionName by remember { mutableStateOf("") }
    var collectionDesc by remember { mutableStateOf("") }
    var collections by remember { mutableStateOf(listOf(
        Pair("Legendary Masterpieces", "Anime that redefined the medium and stood the test of time."),
        Pair("Top Dark Fantasy", "Gritty, deep, and beautifully dark animated worlds."),
        Pair("Relaxation Station", "Cozy, wholesome shows for unwinding after a long day.")
    )) }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Create Custom Collection 📁", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = collectionName,
                        onValueChange = { collectionName = it },
                        label = { Text("Collection Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = collectionDesc,
                        onValueChange = { collectionDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (collectionName.isNotBlank() && collectionDesc.isNotBlank()) {
                                collections = collections + Pair(collectionName, collectionDesc)
                                collectionName = ""
                                collectionDesc = ""
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Create")
                    }
                }
            }
        }
        
        items(collections) { (title, desc) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("12 Titles", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    var darkModeEnabled by remember { mutableStateOf(true) }
    var autoPlayEnabled by remember { mutableStateOf(true) }
    var streamQuality by remember { mutableStateOf("1080p") }
    var currentAccent by remember { mutableStateOf("Classic Pink") }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Personalization", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Theme")
                        Switch(checked = darkModeEnabled, onCheckedChange = { darkModeEnabled = it })
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Accent Color")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Classic Pink", "Teal", "Amber").forEach { accent ->
                                val selected = currentAccent == accent
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { currentAccent = accent }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(accent, fontSize = 10.sp, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Playback & Streaming", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Play Episodes")
                        Switch(checked = autoPlayEnabled, onCheckedChange = { autoPlayEnabled = it })
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Default Stream Quality")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("480p", "720p", "1080p").forEach { quality ->
                                val selected = streamQuality == quality
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { streamQuality = quality }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(quality, fontSize = 10.sp, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App Info", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Version", color = Color.Gray)
                        Text("v2.4.1 Stable", fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Environment", color = Color.Gray)
                        Text("Android Runtime (Kotlin / Compose)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AnimeDetailModal(
    anime: Anime,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onDetailClick: () -> Unit
) {
    val favorites by viewModel.favorites.collectAsState()
    val isLiked = favorites.any { it.malId == anime.malId }
    val context = LocalContext.current

    val staffList = viewModel.getAnimeStaff(anime.malId)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.85f)
                    .clickable(enabled = false) {}, // Prevent clicks from dismissing
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Image with close button
                    Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                        AsyncImage(
                            model = animePosterModel(anime),
                            contentDescription = anime.displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                                    )
                                )
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Content Section
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = anime.displayTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    anime.score?.let {
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("⭐ $it", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    anime.episodes?.let {
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("$it Episodes", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    anime.year?.let {
                                        Text(it.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        // Genres Row
                        anime.genres?.let { genres ->
                            if (genres.isNotEmpty()) {
                                item {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        genres.take(3).forEach { genre ->
                                            Box(
                                                modifier = Modifier
                                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(genre.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Detailed Summary Section
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Synopsis",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = anime.synopsis ?: "No synopsis available for this title.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        // Staff Information Section
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Key Staff",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                staffList.forEach { (name, role) ->
                                    val icon = when (role) {
                                        "Director" -> Icons.Default.Person
                                        "Studio", "Animation Studio" -> Icons.Default.Home
                                        else -> Icons.Default.Info
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text(role, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                        
                        // Extra bottom spacing to ensure scrolling clears the actions
                        item {
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // Bottom Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.toggleFavorite(anime)
                                Toast.makeText(
                                    context,
                                    if (isLiked) "Removed from favorites" else "Added to favorites!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }

                        Button(
                            onClick = onDetailClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Full Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. HOME SCREEN (HomePage.jsx parity)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel, onAnimeClick: (Int) -> Unit) {
    val state by viewModel.homeState.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val context = LocalContext.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var sortBy by rememberSaveable { mutableStateOf("Popularity") } // "Popularity", "Rating", "Year", "Title"

    var isRefreshing by remember { mutableStateOf(false) }

    // Background auto-refresh every 5 minutes
    LaunchedEffect(Unit) {
        while (true) {
            delay(5 * 60 * 1000L)
            viewModel.loadTrending()
            viewModel.loadMoviesAndTvShows()
            viewModel.loadSchedule()
        }
    }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = {
        isRefreshing = true
        viewModel.loadTrending()
        viewModel.loadMoviesAndTvShows()
        viewModel.loadSchedule()
    }) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
        // Search Input Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchAnime(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Search by title or character name...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.searchAnime("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )
        }

        // Hero Slide Carousel (Only visible if no search or genre filter is active)
        if (selectedGenre == null && searchQuery.isBlank()) {
            item {
                val trending = (state as? HomeState.Success)?.trending ?: viewModel.localTrendingAnime
                if (trending.isNotEmpty()) {
                    HeroCarousel(trending = trending, onAnimeClick = onAnimeClick)
                }
            }
        }

        // Continue Watching Row (Only visible if no search or genre filter is active and history exists)
        if (selectedGenre == null && searchQuery.isBlank() && continueWatching.isNotEmpty()) {
            item {
                SectionHeader(title = "Continue Watching", icon = Icons.Default.PlayArrow)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(continueWatching) { pair ->
                        val anime = pair.first
                        val ep = pair.second
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clickable { onAnimeClick(anime.malId) }
                        ) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
                                AsyncImage(
                                    model = animePosterModel(anime),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(96.dp),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                            )
                                        )
                                )

                                // Watch & Like action buttons
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                ) {
                                    IconButton(onClick = { onAnimeClick(anime.malId) }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Watch", tint = Color.White)
                                    }
                                    IconButton(onClick = {
                                        viewModel.toggleFavorite(anime)
                                        Toast.makeText(context, if (viewModel.isFavorite(anime.malId)) "Added to favorites" else "Removed from favorites", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.FavoriteBorder, contentDescription = "Like", tint = Color.White)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("EP $ep", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = anime.displayTitle,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        // Recommended for You (personalized anime recommendations based on genres and viewed history)
        if (selectedGenre == null && searchQuery.isBlank() && recommendations.isNotEmpty()) {
            item {
                SectionHeader(title = "Recommended for You", icon = Icons.Default.ThumbUp)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recommendations) { anime ->
                        AnimeScrollCard(anime = anime, onClick = { onAnimeClick(anime.malId) })
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        // Genre Filter Bar (Stays visible so users can toggle genres)
        item {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (selectedGenre == null) "Browse Genres" else "Genre: $selectedGenre",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (selectedGenre != null) {
                        Text(
                            text = "Clear Filter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { selectedGenre = null }
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                val genres = listOf("All", "Action", "Romance", "Sci-Fi", "Fantasy", "Adventure", "Drama", "Comedy", "Mystery", "School")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(genres) { g ->
                        val isSelected = (g == "All" && selectedGenre == null) || (g == selectedGenre)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedGenre = if (g == "All") null else g
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (g == "All") {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = g,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Sorting Bar (Visible only when filtering a genre)
                if (selectedGenre != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Sort by:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                        val sortOptions = listOf(
                            "Popularity" to Icons.Default.Star,
                            "Rating" to Icons.Default.Favorite,
                            "Year" to Icons.Default.DateRange,
                            "Title" to Icons.Default.Info
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(sortOptions) { (option, icon) ->
                                val isSelected = sortBy == option
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { sortBy = option }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = option,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Main Content Categories / Filtered Grid
        when (val s = state) {
            is HomeState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            is HomeState.Error -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            is HomeState.Success -> {
                if (selectedGenre != null || searchQuery.isNotBlank()) {
                    val allAnime = viewModel.getAnimeOnlyList()
                    val filteredList = allAnime.filter { anime ->
                        val matchesGenre = if (selectedGenre == null) {
                            true
                        } else {
                            anime.genres?.any { it.name.equals(selectedGenre, ignoreCase = true) } == true
                        }
                        val matchesSearch = if (searchQuery.isBlank()) {
                            true
                        } else {
                            val titleMatches = anime.title.contains(searchQuery, ignoreCase = true) || 
                                               anime.titleEnglish?.contains(searchQuery, ignoreCase = true) == true
                            val characters = viewModel.getAnimeCharacters(anime.malId)
                            val characterMatches = characters.any { it.contains(searchQuery, ignoreCase = true) }
                            titleMatches || characterMatches
                        }
                        matchesGenre && matchesSearch
                    }
                    val sortedList = when (sortBy) {
                        "Rating" -> filteredList.sortedByDescending { it.score ?: 0.0 }
                        "Year" -> filteredList.sortedByDescending { it.year ?: 0 }
                        "Title" -> filteredList.sortedBy { it.displayTitle }
                        else -> filteredList
                    }

                    if (sortedList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (selectedGenre != null && searchQuery.isNotBlank()) {
                                            "No anime found for \"$searchQuery\" in $selectedGenre"
                                        } else if (selectedGenre != null) {
                                            "No anime found for $selectedGenre"
                                        } else {
                                            "No anime found for \"$searchQuery\""
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        val columns = 3
                        val chunked = sortedList.chunked(columns)
                        items(chunked) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (anime in rowItems) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        AnimeGridCard(
                                            anime = anime,
                                            onClick = { onAnimeClick(anime.malId) }
                                        )
                                    }
                                }
                                if (rowItems.size < columns) {
                                    for (i in 0 until (columns - rowItems.size)) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Trending Now
                    item {
                        SectionHeader(title = "Trending Now", icon = Icons.Default.Star)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.trending) { anime ->
                                AnimeScrollCard(anime = anime, onClick = { onAnimeClick(anime.malId) })
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    // Most Popular
                    item {
                        SectionHeader(title = "Most Popular", icon = Icons.Default.Favorite)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.popular) { anime ->
                                AnimeScrollCard(anime = anime, onClick = { onAnimeClick(anime.malId) })
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    // Seasonal Curated Section (Interactive Seasonal Browser!)
                    item {
                        val activeYear by viewModel.selectedSeasonalYear.collectAsState()
                        val activeSeason by viewModel.selectedSeasonalSeason.collectAsState()
                        val isSeasonalLoading by viewModel.seasonalBrowserLoading.collectAsState()

                        Column(modifier = Modifier.fillMaxWidth()) {
                            SectionHeader(title = "Seasonal Favorites", icon = Icons.Default.DateRange)
                            
                            // Year Selection row
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                val years = listOf(2026, 2025, 2024, 2023)
                                items(years) { y ->
                                    val isSelected = activeYear == y
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                if (activeYear != y) {
                                                    viewModel.changeSeason(y, activeSeason)
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = y.toString(),
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Season Selection row
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                val seasons = listOf("Winter", "Spring", "Summer", "Fall")
                                items(seasons) { sName ->
                                    val isSelected = activeSeason.equals(sName, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                if (!activeSeason.equals(sName, ignoreCase = true)) {
                                                    viewModel.changeSeason(activeYear, sName)
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = sName,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (isSeasonalLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(s.seasonal) { anime ->
                                        AnimeScrollCard(anime = anime, onClick = { onAnimeClick(anime.malId) })
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    // Upcoming Next Season
                    item {
                        SectionHeader(title = "Upcoming Releases", icon = Icons.Default.Info)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.upcoming) { anime ->
                                AnimeScrollCard(anime = anime, onClick = { onAnimeClick(anime.malId) })
                            }
                        }
                    }
                }
            }
        }
    }
    }
    // Turn off the refresh indicator shortly after refresh was triggered
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(800)
            isRefreshing = false
        }
    }
}

@Composable
fun HeroCarousel(trending: List<Anime>, onAnimeClick: (Int) -> Unit) {
    val slideshowAnimeList = remember {
        listOf(
            Anime(
                malId = 1535,
                title = "Death Note",
                titleEnglish = "Death Note",
                images = null,
                score = 8.62,
                synopsis = "A high school student discovers a supernatural notebook that grants him the ability to kill anyone by writing their name in it.",
                episodes = 37,
                status = "Finished Airing",
                year = 2006
            ),
            Anime(
                malId = 41389,
                title = "Tonikawa: Over the Moon For You",
                titleEnglish = "Tonikawa: Over the Moon For You",
                images = null,
                score = 7.91,
                synopsis = "Tsukasa and Nasa Yuzaki fall in love at first sight and decide to get married, beginning their sweet daily lives together.",
                episodes = 12,
                status = "Finished Airing",
                year = 2020
            ),
            Anime(
                malId = 21,
                title = "One Piece",
                titleEnglish = "One Piece",
                images = null,
                score = 8.72,
                synopsis = "Monkey D. Luffy and his pirate crew search for the ultimate treasure, the One Piece, to become the next Pirate King.",
                episodes = 1100,
                status = "Currently Airing",
                year = 1999
            ),
            Anime(
                malId = 19,
                title = "Monster",
                titleEnglish = "Monster",
                images = null,
                score = 8.88,
                synopsis = "A brilliant brain surgeon's life is pathologically changed when he decides to save a young boy's life instead of a prominent politician.",
                episodes = 74,
                status = "Finished Airing",
                year = 2004
            ),
            Anime(
                malId = 52299,
                title = "Solo Leveling",
                titleEnglish = "Solo Leveling",
                images = null,
                score = 8.36,
                synopsis = "In a world where hunters must battle deadly monsters to protect mankind, Sung Jinwoo, a weak hunter, receives a unique leveling system.",
                episodes = 12,
                status = "Finished Airing",
                year = 2024
            )
        )
    }

    var activeIndex by rememberSaveable(slideshowAnimeList) { mutableStateOf(0) }
    
    // Auto sliding effect
    LaunchedEffect(slideshowAnimeList.size) {
        while (true) {
            delay(5000)
            activeIndex = (activeIndex + 1) % slideshowAnimeList.size
        }
    }

    val currentAnime = slideshowAnimeList[activeIndex]

    val imageModel = when (currentAnime.malId) {
        1535 -> "https://m.media-amazon.com/images/M/MV5BOTdjOGZlNWUtYTQ0NC00YjIwLTgzMGQtYjA1Y2YxZWJjMDA4XkEyXkFqcGc@._V1_QL75_UX291_.jpg"
        41389 -> "https://m.media-amazon.com/images/S/pv-target-images/8bed8b49da4505916ef814544e0f452d3a0002e0abbe568a9b37a79205c55bea._BR-6_AC_SX720_FMjpg_.jpg"
        21 -> "https://wallpaperaccess.com/full/8750973.jpg"
        19 -> "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQEHwRLuPMW4mpzEQqP-8vYauka8N4_F5PckqRXQHrjAw&s=10"
        52299 -> "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSNYbn-V557RrlGjrqQ50wyzUqyNRh4TL_pHuffuiU1X8hr4HWY4TLZiWjP&s=10"
        else -> R.drawable.ic_launcher_foreground
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable { onAnimeClick(currentAnime.malId) }
    ) {
        // Hero Background Image with Real Photo
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Black vignette overlays
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        // Contents Box
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "TRENDING #${activeIndex + 1}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                currentAnime.score?.let {
                    Text("⭐ $it", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = currentAnime.displayTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = currentAnime.synopsis ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAnimeClick(currentAnime.malId) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Watch Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Dot indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            slideshowAnimeList.forEachIndexed { idx, _ ->
                Box(
                    modifier = Modifier
                        .size(if (idx == activeIndex) 14.dp else 6.dp, 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (idx == activeIndex) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun AnimeScrollCard(anime: Anime, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(118.dp)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
            AsyncImage(
                model = animePosterModel(anime),
                contentDescription = anime.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(166.dp),
                contentScale = ContentScale.Crop
            )
            anime.score?.let {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("⭐ $it", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = anime.displayTitle,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        anime.year?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AnimeCard(anime: Anime, onClick: () -> Unit) {
    AnimeScrollCard(anime = anime, onClick = onClick)
}

@Composable
fun AnimeGridCard(anime: Anime, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = animePosterModel(anime),
                contentDescription = anime.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            anime.score?.let {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("⭐ $it", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = anime.displayTitle,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        anime.year?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==========================================
// 2. SEARCH SCREEN (SearchPage.jsx parity)
// ==========================================
@Composable
fun SearchScreen(viewModel: MainViewModel, onAnimeClick: (Int) -> Unit, onNavigateToShows: () -> Unit) {
    val results by viewModel.searchResults.collectAsState()
    val state by viewModel.homeState.collectAsState()
    val movies by viewModel.moviesList.collectAsState()
    val tvShows by viewModel.tvShowsList.collectAsState()

    val query by viewModel.searchQuery.collectAsState()
    val filterGenre by viewModel.searchGenre.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }

    val listToShow = remember(query, filterGenre, results) {
        if (query.isBlank() && filterGenre.isBlank()) {
            emptyList()
        } else {
            val list = results.toMutableList()
            val localMatches = viewModel.getCombinedAnimeList().filter { anime ->
                val matchesGenre = if (filterGenre.isBlank()) {
                    true
                } else {
                    anime.genres?.any { it.name.equals(filterGenre, ignoreCase = true) } == true
                }
                val matchesSearch = if (query.isBlank()) {
                    true
                } else {
                    val titleMatches = anime.title.contains(query, ignoreCase = true) || 
                                       anime.titleEnglish?.contains(query, ignoreCase = true) == true
                    val characters = viewModel.getAnimeCharacters(anime.malId)
                    val characterMatches = characters.any { it.contains(query, ignoreCase = true) }
                    titleMatches || characterMatches
                }
                matchesGenre && matchesSearch
            }
            (localMatches + list).distinctBy { it.malId }
        }
    }

    val filteredList = remember(listToShow, selectedCategory, movies, tvShows) {
        when (selectedCategory) {
            "Movies" -> listToShow.filter { anime -> movies.any { it.malId == anime.malId } }
            "TV" -> listToShow.filter { anime -> tvShows.any { it.malId == anime.malId } }
            "Anime" -> listToShow.filter { anime ->
                !movies.any { it.malId == anime.malId } && !tvShows.any { it.malId == anime.malId }
            }
            else -> listToShow
        }
    }

    val defaultList = remember(selectedCategory, movies, tvShows, viewModel.localTrendingAnime) {
        val baseList = when (selectedCategory) {
            "Movies" -> movies
            "TV" -> tvShows
            "Anime" -> viewModel.localTrendingAnime
            else -> viewModel.localTrendingAnime + movies + tvShows
        }
        baseList.distinctBy { it.malId }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    viewModel.searchQuery.value = it
                    viewModel.searchAnime(it)
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search by title or character...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { 
                            viewModel.searchQuery.value = ""
                            viewModel.searchAnime("") 
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            IconButton(
                onClick = { showFilters = !showFilters },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (showFilters) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    Icons.Default.List,
                    contentDescription = "Filters",
                    tint = if (showFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            }

            Button(
                onClick = onNavigateToShows,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Browse Shows", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }

        // Category Tabs filter Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val categories = listOf(
                "All" to "🌐 All",
                "Movies" to "🎬 Movies",
                "TV" to "📺 TV Shows",
                "Anime" to "✨ Anime"
            )
            items(categories) { (categoryKey, label) ->
                val active = selectedCategory == categoryKey
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { selectedCategory = categoryKey }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        label,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Expandable Filters Accordion
        AnimatedVisibility(visible = showFilters) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Genres",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val genres = listOf("Action", "Fantasy", "Adventure", "Drama", "Romance", "Comedy", "Mystery", "School")
                    items(genres) { g ->
                        val selected = filterGenre == g
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    val newGenre = if (selected) "" else g
                                    viewModel.setSearchGenre(newGenre)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                g,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // Results view
        if (state is HomeState.Loading && listToShow.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val isSearching = query.isNotBlank() || filterGenre.isNotBlank()
            if (isSearching) {
                if (filteredList.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No results in $selectedCategory category for search query", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(118.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredList) { anime ->
                            AnimeCard(anime = anime, onClick = { onAnimeClick(anime.malId) })
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(118.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                        val headerText = when (selectedCategory) {
                            "Movies" -> "Trending Movies"
                            "TV" -> "Trending Shows & Dramas"
                            "Anime" -> "Trending Anime"
                            else -> "Trending Searches"
                        }
                        Text(headerText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(defaultList) { anime ->
                        AnimeCard(anime = anime, onClick = { onAnimeClick(anime.malId) })
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. SHOWS & MOVIES (DramasMoviesPage.jsx parity)
// ==========================================
@Composable
fun DramasMoviesPage(viewModel: MainViewModel, navController: NavHostController, onAnimeClick: (Int) -> Unit) {
    val tvShows by viewModel.tvShowsList.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Access Row for new Pages (Community, Stats, Collections, Settings)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("Community", Icons.Default.Share, "community"),
                Triple("Stats", Icons.Default.Star, "stats"),
                Triple("Collections", Icons.Default.List, "collections"),
                Triple("Settings", Icons.Default.Settings, "settings")
            ).forEach { (label, icon, route) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable { navController.navigate(route) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Featured Hero Card of the Month!
        val featured = tvShows.firstOrNull()
        featured?.let { anime ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clickable { onAnimeClick(anime.malId) }
            ) {
                AsyncImage(
                    model = animePosterModel(anime),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("FEATURED", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(anime.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                    Text(anime.synopsis ?: "", style = MaterialTheme.typography.bodySmall, color = Color.LightGray, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Listing Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(118.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(tvShows) { anime ->
                AnimeCard(anime = anime, onClick = { onAnimeClick(anime.malId) })
            }
        }
    }
}

// ==========================================
// 4. SCHEDULE SCREEN (SchedulePage.jsx parity)
// ==========================================
@Composable
fun SchedulePage(viewModel: MainViewModel, onAnimeClick: (Int) -> Unit) {
    val reminders by viewModel.reminders.collectAsState()
    var selectedDay by remember { mutableStateOf(newDayIndex()) }
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val context = LocalContext.current

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val airingSchedule by viewModel.airingSchedule.collectAsState()
    val scheduleByDay by viewModel.scheduleByDay.collectAsState()
    val currentItems = scheduleByDay[selectedDay] ?: emptyList()
    val nextUp = airingSchedule.find { it.id == 1 } // Hero upcoming spotlight

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Spotlight Airing Hero Banner
        nextUp?.let { item ->
            item {
                SectionHeader(title = "Spotlight Airing", icon = Icons.Default.Notifications)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.surface)
                            )
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickable { onAnimeClick(item.animeId) }
                        .padding(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp, 96.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("NEXT UP", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(4.dp))
                            Text("Episode ${item.episode} · ${item.airingTime}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Daily Strip Day Selector
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(days) { idx, d ->
                    val active = selectedDay == idx
                    val count = (scheduleByDay[idx] ?: emptyList()).size
                    Column(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable { selectedDay = idx }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(d, color = if (active) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(count.toString(), color = if (active) Color.White else MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Airing Cards list
        if (currentItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No episodes scheduled today.", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            items(currentItems) { item ->
                val reminded = reminders.contains(item.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAnimeClick(item.animeId) },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp, 72.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column {
                            Text(item.airingTime + " · Ep " + item.episode, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("⭐ ${item.rating} · ${item.countdown}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.toggleReminder(item.id)
                            if (reminded) {
                                cancelAiringNotification(context, item)
                                Toast.makeText(context, "Reminder cancelled", Toast.LENGTH_SHORT).show()
                            } else {
                                scheduleAiringNotification(context, item)
                                Toast.makeText(context, "Alert scheduled! Test alarm in 3s...", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (reminded) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                            contentDescription = "Remind me",
                            tint = if (reminded) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

private fun newDayIndex(): Int {
    val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
    return day - 1 // Calendar is 1-indexed (Sunday = 1, Monday = 2, etc.)
}

@SuppressLint("ScheduleExactAlarm")
fun scheduleAiringNotification(context: android.content.Context, item: com.example.ui.AiringItem) {
    try {
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, com.example.ui.AiringAlarmReceiver::class.java).apply {
            putExtra("animeId", item.animeId)
            putExtra("animeTitle", item.title)
            putExtra("episode", item.episode)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            item.id,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = java.util.Calendar.getInstance().apply {
            val nowDay = get(java.util.Calendar.DAY_OF_WEEK)
            val targetDay = item.dayOfWeek + 1

            var daysDiff = targetDay - nowDay
            if (daysDiff < 0) {
                daysDiff += 7
            }

            val timeParts = item.airingTime.split(" ")
            val hms = timeParts[0].split(":")
            val hour = hms[0].toIntOrNull() ?: 12
            val minute = if (hms.size > 1) hms[1].toIntOrNull() ?: 0 else 0
            val isPm = timeParts.getOrNull(1)?.equals("PM", ignoreCase = true) ?: false

            set(java.util.Calendar.HOUR_OF_DAY, if (isPm) { if (hour == 12) 12 else hour + 12 } else { if (hour == 12) 0 else hour })
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)

            add(java.util.Calendar.DAY_OF_YEAR, daysDiff)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 7)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }

        // --- INSTANT TEST TRIGGER ---
        val testIntent = android.content.Intent(context, com.example.ui.AiringAlarmReceiver::class.java).apply {
            putExtra("animeId", item.animeId)
            putExtra("animeTitle", item.title + " (Upcoming Alert Test)")
            putExtra("episode", item.episode)
        }
        val testPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            item.id + 10000,
            testIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.set(
            android.app.AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 3000, // 3 seconds later
            testPendingIntent
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun cancelAiringNotification(context: android.content.Context, item: com.example.ui.AiringItem) {
    try {
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, com.example.ui.AiringAlarmReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            item.id,
            intent,
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        
        // Also cancel any test trigger
        val testPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            item.id + 10000,
            intent,
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        if (testPendingIntent != null) {
            alarmManager.cancel(testPendingIntent)
            testPendingIntent.cancel()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ==========================================
// 5. PROFILE SCREEN (ProfilePage.jsx parity)
// ==========================================
@Composable
fun ProfilePage(viewModel: MainViewModel, navController: NavHostController, onAnimeClick: (Int) -> Unit) {
    val user by viewModel.currentUser.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val airingSchedule by viewModel.airingSchedule.collectAsState()

    if (user == null) {
        AuthScreen(viewModel = viewModel)
        return
    }

    val currentUser = user!!
    var activeTab by rememberSaveable { mutableStateOf("favorites") }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var usernameState by rememberSaveable { mutableStateOf(currentUser.username) }
    var avatarUrlState by rememberSaveable { mutableStateOf(currentUser.avatarUrl) }
    var bannerUrlState by rememberSaveable { mutableStateOf(currentUser.bannerUrl) }
    var saveStatus by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current
    var isUploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf("") }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            uploadError = ""
            uploadToCloudinary(
                context = context,
                fileUri = uri,
                onSuccess = { uploadedUrl ->
                    avatarUrlState = uploadedUrl
                    isUploading = false
                },
                onFailure = { error ->
                    uploadError = error
                    isUploading = false
                }
            )
        }
    }

    LaunchedEffect(currentUser) {
        usernameState = currentUser.username
        avatarUrlState = currentUser.avatarUrl
        bannerUrlState = currentUser.bannerUrl
    }

    val presetAvatars = listOf(
        "https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=300&q=80",
        "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=300&q=80",
        "https://images.unsplash.com/photo-1549880338-65ddcdfd017b?auto=format&fit=crop&w=300&q=80",
        "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=300&q=80"
    )

    val presetBanners = listOf(
        "https://images.unsplash.com/photo-1614728263952-c834c7302501?auto=format&fit=crop&w=1200&q=80",
        "https://images.unsplash.com/photo-1522383225653-ed111181a951?auto=format&fit=crop&w=1200&q=80",
        "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?auto=format&fit=crop&w=1200&q=80",
        "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=1200&q=80"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (bannerUrlState.isNotBlank()) {
                    AsyncImage(
                        model = bannerUrlState,
                        contentDescription = "Profile Banner",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                )
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                                startY = 120f
                            )
                        )
                )

                IconButton(
                    onClick = { isEditing = !isEditing },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (isEditing) "Close edit" else "Edit profile",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-60).dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                    ) {
                        AsyncImage(
                            model = avatarUrlState.ifBlank { currentUser.avatarUrl },
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.padding(bottom = 6.dp)) {
                        Text(currentUser.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(currentUser.email, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("VAULT CITIZEN", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Level ${currentUser.level}", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatCard(modifier = Modifier.weight(1f), title = continueWatching.size.toString(), subtitle = "In Progress", icon = Icons.Default.PlayArrow)
                Spacer(Modifier.width(10.dp))
                StatCard(modifier = Modifier.weight(1f), title = favorites.size.toString(), subtitle = "Favorites", icon = Icons.Default.Favorite)
                Spacer(Modifier.width(10.dp))
                StatCard(modifier = Modifier.weight(1f), title = "${currentUser.totalWatchTime / 60}h", subtitle = "Watch Time", icon = Icons.Default.DateRange)
            }
            Spacer(Modifier.height(16.dp))
        }

        if (isEditing) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text("Profile Theme Customizer", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = usernameState,
                        onValueChange = { usernameState = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = avatarUrlState,
                        onValueChange = { avatarUrlState = it },
                        label = { Text("Avatar URL") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { pickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isUploading
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isUploading) "Uploading custom avatar..." else "Upload Custom Avatar")
                        }
                    }
                    if (isUploading) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (uploadError.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(uploadError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = bannerUrlState,
                        onValueChange = { bannerUrlState = it },
                        label = { Text("Banner URL") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(16.dp))

                    Text("Quick Preset Avatars", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        presetAvatars.forEach { url ->
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { avatarUrlState = url }
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Quick Preset Banners", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        presetBanners.forEach { url ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { bannerUrlState = url }
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.updateProfile(usernameState, avatarUrlState, bannerUrlState)
                            saveStatus = "Profile updated successfully!"
                            isEditing = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                    if (saveStatus.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(saveStatus, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileTabButton(label = "Continue", selected = activeTab == "continue") { activeTab = "continue" }
                ProfileTabButton(label = "Favorites", selected = activeTab == "favorites") { activeTab = "favorites" }
                ProfileTabButton(label = "Reminders", selected = activeTab == "reminders") { activeTab = "reminders" }
            }
            Spacer(Modifier.height(16.dp))
        }

        when (activeTab) {
            "continue" -> {
                if (continueWatching.isEmpty()) {
                    item {
                        EmptyTabState(msg = "No active streams yet. Watch something to see it here.")
                    }
                } else {
                    items(continueWatching) { pair ->
                        val anime = pair.first
                        val ep = pair.second
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                                .clickable { onAnimeClick(anime.malId) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = animePosterModel(anime),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp, 96.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(anime.displayTitle, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text("Episode $ep", color = Color.Gray, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            "favorites" -> {
                if (favorites.isEmpty()) {
                    item {
                        EmptyTabState(msg = "No favorites yet. Tap the heart on details screen!")
                    }
                } else {
                    item {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(110.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.heightIn(max = 600.dp)
                        ) {
                            items(favorites) { anime ->
                                Column(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .clickable { onAnimeClick(anime.malId) }
                                ) {
                                    AsyncImage(
                                        model = animePosterModel(anime),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(3f / 4f)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(anime.displayTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            "reminders" -> {
                val remindedSchedules = airingSchedule.filter { reminders.contains(it.id) }
                if (remindedSchedules.isEmpty()) {
                    item {
                        EmptyTabState(msg = "No reminders are active right now.")
                    }
                } else {
                    items(remindedSchedules) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                                .clickable { onAnimeClick(item.animeId) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(52.dp, 72.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Episode ${item.episode} • ${item.airingTime}", color = Color.Gray, fontSize = 12.sp)
                            }
                            IconButton(onClick = { viewModel.toggleReminder(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove reminder", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(6.dp))
                Text("Version v0.2-Mobile", fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text("Your vault profile is updated and sync-ready.", color = Color.Gray, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout Account", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ProfileTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun StatCard(modifier: Modifier, title: String, subtitle: String, icon: ImageVector) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmptyTabState(msg: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(msg, color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

// Authentication Forms
@Composable
fun AuthScreen(viewModel: MainViewModel) {
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val authError by viewModel.authError.collectAsState()

    LaunchedEffect(isSignUp) {
        viewModel.clearAuthError()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = "https://github.com/animevaultofficial/animevaultofficial.github.io/blob/main/logo.png?raw=true",
                contentDescription = "AnimeVault Logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isSignUp) "Create Vault Account" else "Welcome to AnimeVault",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isSignUp) "Sign up to start tracking your anime collections." else "Login to sync statistics and track reminders.",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))

            // Show error if there's any
            authError?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Username input (required for sign up, or sign-in identifier)
            OutlinedTextField(
                value = username,
                onValueChange = { 
                    username = it
                    viewModel.clearAuthError()
                },
                label = { Text(if (isSignUp) "Choose Username" else "Username or Email") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            
            if (isSignUp) {
                Spacer(Modifier.height(8.dp))
                // Email field is only shown on Sign Up
                OutlinedTextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        viewModel.clearAuthError()
                    },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { 
                    password = it
                    viewModel.clearAuthError()
                },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    val description = if (passwordVisible) "Hide password" else "Show password"

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(20.dp))

            // Submit Button
            Button(
                onClick = {
                    if (isSignUp) {
                        viewModel.register(email, username, password)
                    } else {
                        viewModel.loginWithPassword(username, password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isSignUp) "Sign Up Now" else "Login Now",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // Toggle Mode Link
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isSignUp) "Already have an account? " else "Don't have an account? ",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = if (isSignUp) "Sign In" else "Sign Up",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { 
                            isSignUp = !isSignUp 
                            // Reset fields when switching modes
                            username = ""
                            email = ""
                            password = ""
                        }
                        .padding(4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))

            Text("OR BYPASS WITH", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))

            // Guest Mode / Legacy Login Bypass
            Button(
                onClick = { 
                    viewModel.login("guest@vault.com", "LegendaryOtaku")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Guest Mode (Auto Bypass)", color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// ==========================================
// 6. DETAIL SCREEN (AnimeDetailsPage.jsx parity)
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(id: Int, viewModel: MainViewModel, onBack: () -> Unit, onPlay: (Int) -> Unit, onGenreClick: () -> Unit) {
    val state by viewModel.detailState.collectAsState()
    val isLiked = viewModel.isFavorite(id)
    val context = LocalContext.current

    LaunchedEffect(id) {
        viewModel.loadAnimeDetails(id)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is DetailState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is DetailState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is DetailState.Success -> {
                val anime = s.anime
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                            AsyncImage(
                                model = animePosterModel(anime),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                        )
                                    )
                            )
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(Alignment.TopStart)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        }
                    }

                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = anime.displayTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        viewModel.toggleFavorite(anime)
                                        Toast.makeText(
                                            context,
                                            if (isLiked) "Removed from favorites" else "Added to favorites!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Like",
                                        tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                anime.score?.let {
                                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                        Text("⭐ $it", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                                anime.status?.let {
                                    Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text(it, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    }
                                }
                                anime.episodes?.let {
                                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("$it Eps", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                            
                            // Clickable tags / genres
                            anime.genres?.let { genres ->
                                if (genres.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        for (genre in genres) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                    .clickable {
                                                        viewModel.setSearchGenre(genre.name)
                                                        onGenreClick()
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = genre.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    viewModel.watchEpisode(anime, 1)
                                    onPlay(1)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Watch Now", fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Episodes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val epCount = anime.episodes ?: 12
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (i in 1..epCount) {
                                    Button(
                                        onClick = {
                                            viewModel.watchEpisode(anime, i)
                                            onPlay(i)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.size(50.dp)
                                    ) {
                                        Text(i.toString(), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Synopsis",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = anime.synopsis ?: "No synopsis available.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. STREAMING PLAYER SCREEN (PlayerScreen.jsx parity)
// ==========================================
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(animeId: Int, episode: Int = 1, viewModel: MainViewModel, onBack: () -> Unit) {
    val detailState by viewModel.detailState.collectAsState()
    
    // Get the media object to check its actual type
    val mediaItem = remember(animeId) {
        viewModel.findAnimeInLoadedLists(animeId)
    }
    
    // Determine media type: first from mediaType field, then from ID range as fallback
    val isMovie = remember(mediaItem, animeId) { 
        mediaItem?.mediaType == "movie" || animeId in 10000..19999
    }
    val isTvShow = remember(mediaItem, animeId) { 
        mediaItem?.mediaType == "tv" || animeId in 20000..29999
    }
    val isMovieOrShow = isMovie || isTvShow

    var lang by remember { mutableStateOf("sub") } // "sub" or "dub"
    var blockedCount by remember { mutableStateOf(0) }
    var watchProgress by remember { mutableStateOf(0) } // Track watch progress
    
    val season = 1 // Default to season 1 for TV shows
    
    val url = remember(animeId, episode, lang, isMovie, isTvShow, season) {
        if (isMovieOrShow) {
            val tmdbId = viewModel.getTmdbId(animeId)
            val vId = viewModel.getVideasyId(animeId)
            val idToUse = tmdbId ?: vId

            // If we don't have a TMDB/Videasy id and the animeId looks like a MAL anime id (<10000),
            // avoid using it for Videasy (it will 404). Fallback to anime stream provider.
            if (idToUse.isNullOrBlank() && animeId < 10000) {
                return@remember "https://animeplay.cfd/stream/mal/$animeId/$episode/$lang"
            }

            val resolvedId = idToUse ?: animeId.toString()
            val baseUrl = if (isMovie) "https://player.videasy.net/movie/$resolvedId"
                         else "https://player.videasy.net/tv/$resolvedId/$season/$episode"

            // Add Videasy features as query parameters
            baseUrl + "?color=3B82F6&nextEpisode=true&episodeSelector=true&autoplayNextEpisode=true&overlay=true"
        } else {
            "https://animeplay.cfd/stream/mal/$animeId/$episode/$lang"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            val requestUrl = request?.url?.toString() ?: return null
                            if (isAdOrTracker(requestUrl)) {
                                view?.post {
                                    blockedCount++
                                }
                                return android.webkit.WebResourceResponse(
                                    "text/plain", 
                                    "UTF-8", 
                                    java.io.ByteArrayInputStream(ByteArray(0))
                                )
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                    webChromeClient = WebChromeClient()
                    
                    // Add JavaScript interface to receive progress tracking events from Videasy player
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun receiveProgress(progress: String) {
                            try {
                                val data = android.util.JsonReader(java.io.StringReader(progress))
                                data.beginObject()
                                while (data.hasNext()) {
                                    when (data.nextName()) {
                                        "progress" -> watchProgress = data.nextInt()
                                        else -> data.skipValue()
                                    }
                                }
                                data.endObject()
                                android.util.Log.d("VideoasyPlayer", "Watch progress: $watchProgress seconds")
                            } catch (e: Exception) {
                                android.util.Log.e("VideoasyPlayer", "Progress tracking error: ${e.message}")
                            }
                        }
                    }, "VideoasyProgress")
                    
                    // Inject script to listen for player messages and block ads
                    val progressScript = """
                        // Block ad scripts before they load
                        window.addEventListener("beforeunload", function() {
                            var scripts = document.querySelectorAll("script");
                            scripts.forEach(function(s) {
                                var src = s.src || "";
                                if (src.includes("ads.") || src.includes("google") || src.includes("analytics") || 
                                    src.includes("advertising") || src.includes("doubleclick")) {
                                    s.remove();
                                }
                            });
                        });
                        
                        // Remove ad elements and iframes
                        var removeAds = function() {
                            var adSelectors = [
                                "[id*='ad-']", "[id*='ads']", "[class*='ad-']", "[class*='ads']",
                                "[id*='advertisement']", "[class*='advertisement']",
                                "[id*='sponsor']", "[class*='sponsor']",
                                "[id*='banner']", "[class*='banner']",
                                "iframe[src*='ads']", "iframe[src*='google']", "iframe[src*='doubleclick']"
                            ];
                            adSelectors.forEach(function(sel) {
                                try {
                                    document.querySelectorAll(sel).forEach(function(el) {
                                        el.style.display = "none";
                                        el.remove();
                                    });
                                } catch (e) {}
                            });
                        };
                        
                        removeAds();
                        setInterval(removeAds, 2000);
                        
                        // Track player progress
                        window.addEventListener("message", function (event) {
                            try {
                                if (typeof event.data === "string") {
                                    var data = JSON.parse(event.data);
                                    if (data.progress) {
                                        window.VideoasyProgress.receiveProgress(event.data);
                                    }
                                }
                            } catch (e) {
                                console.error("Error processing message:", e);
                            }
                        });
                    """.trimIndent()
                    
                    setWebViewClient(object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(progressScript) {}
                        }
                    })
                }
            },
            update = { webView ->
                if (webView.tag != url) {
                    webView.tag = url
                    android.util.Log.d("PlayerScreen", "Loading player url: $url (animeId=$animeId episode=$episode)")
                    webView.loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Overlay Floating TopBar Controller
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
                
                // Ad block active badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Green.copy(alpha = 0.2f))
                        .border(1.dp, Color.Green.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "🛡️ Blocked: $blockedCount ads",
                        color = Color.Green,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Sub/Dub switch (for anime only)
                if (!isMovieOrShow) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(2.dp)
                    ) {
                        listOf("sub" to "SUB", "dub" to "DUB").forEach { (value, label) ->
                            val selected = lang == value
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { lang = value }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (selected) Color.White else Color.LightGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun isAdOrTracker(url: String): Boolean {
    val blockedHosts = listOf(
        // Google
        "google-analytics.com", "analytics.google.com", "googletagmanager.com",
        "googletagservices.com", "doubleclick.net", "adservice.google",
        "pagead2.googlesyndication.com", "stats.g.doubleclick.net",
        "fonts.googleapis.com", "fonts.gstatic.com", "googleapis.com", "gstatic.com",
        // Ad networks
        "cdn.adx1.com", "intelligenceadx.com", "adsco.re", "mc.yandex.com",
        "mc.yandex.ru", "bvtpk.com", "my.rtmark.net", "b7510.com",
        "gt.unbrownunflat.com", "im.malocacomals.com",
        "nf.sixmossin.com", "realizationnewestfangs.com", "acscdn.com",
        "lt.taloseempest.com", "preferencenail.com", "protrafficinspector.com", 
        "s10.histats.com", "weirdopt.com", "static.cloudflareinsights.com",
        "kettledroopingcontinuation.com", "wayfarerorthodox.com",
        "woxaglasuy.net", "adeptspiritual.com", "calculating-laugh.com",
        "usrpubtrk.com", "adexchangeclear.com", "cloudnestra.com",
        "neonhorizonworkshops.com", "profitableratecpm.com", "histats.com",
        // Common ad patterns
        "vidsrc", "dropfile.cc", "dropfiles", "clickid", "ad_params",
        "ads.", "analytics.", "tracker.", "pixel.", "beacon.",
        "pagead", "adclick", "adframe", "adserver", "adbot",
        "amazon-adsystem.com", "betrad.com", "bidswitch.net",
        "criteo.com", "c1.taboola.com", "partner.googleadservices.com"
    )
    val uri = try { android.net.Uri.parse(url) } catch (e: Exception) { null } ?: return false
    val host = uri.host?.lowercase() ?: return false
    val path = uri.path?.lowercase() ?: ""
    return blockedHosts.any { host.contains(it) || path.contains(it) }
}
