package com.example.rescuelink

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.rounded.ContactSupport
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rescuelink.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ═══════════════════════════════════════════════════════════════════════════
//  DESIGN TOKENS
// ═══════════════════════════════════════════════════════════════════════════
object DashboardTokens {
    val BlackCore  = Color(0xFF060608)
    val CardBg     = Color(0xFF0E0E12)
    val CardBg2    = Color(0xFF141418)
    val CardBg3    = Color(0xFF1A1A1F)
    val Rim        = Color(0xFF1E1E26)
    val Rim2       = Color(0xFF2A2A35)
    val WhitePure  = Color(0xFFFFFFFF)
    val White80    = Color(0xCCFFFFFF)
    val White60    = Color(0x99FFFFFF)
    val White35    = Color(0x59FFFFFF)
    val White15    = Color(0x26FFFFFF)

    val RedHot     = AuthDesignTokens.RedHot
    val RedDeep    = AuthDesignTokens.RedDeep
    val RedGlow    = AuthDesignTokens.RedGlow
    val RedDim     = Color(0x1EE8001D)
    val RedMid     = Color(0x38E8001D)

    val Green      = Color(0xFF22C55E)
    val GreenDim   = Color(0x1A22C55E)
    val Orange     = Color(0xFFF59E0B)
    val OrangeDim  = Color(0x1AF59E0B)
    val Blue       = Color(0xFF3B82F6)
    val BlueDim    = Color(0x1A3B82F6)
    val Purple     = Color(0xFFA855F7)
    val PurpleDim  = Color(0x1AA855F7)
}

// ═══════════════════════════════════════════════════════════════════════════
//  DATA MODELS
// ═══════════════════════════════════════════════════════════════════════════
data class Incident(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val status: String = "active",
    val timestamp: Long = 0L
)

data class CommunityPost(
    val id: String = "",
    val author: String = "",
    val initials: String = "",
    val timeAgo: String = "",
    val content: String = "",
    val likes: Int = 0,
    val comments: Int = 0,
    val authorUid: String = "",
    val likedByMe: Boolean = false,
    val timestamp: Long = 0L
)

data class VehicleData(
    val fuel: Int = 64,
    val tyrePsi: Int = 32,
    val gpsActive: Boolean = true,
    val engineTemp: Int = 92,
    val batteryLevel: Int = 87
)

data class NotificationItem(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val timestamp: Long = 0L,
    val read: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════════════
//  VIEWMODEL — Firebase Realtime Database
// ═══════════════════════════════════════════════════════════════════════════
data class DashboardState(
    val userInitials: String = "·",
    val userName: String = "",
    val userEmail: String = "",
    val incidents: List<Incident> = emptyList(),
    val communityPosts: List<CommunityPost> = emptyList(),
    val vehicleData: VehicleData = VehicleData(),
    val notifications: List<NotificationItem> = emptyList(),
    val unreadCount: Int = 0,
    val isLoadingIncidents: Boolean = true,
    val isLoadingCommunity: Boolean = true,
    val isLoadingVehicle: Boolean = true,
    val sosActive: Boolean = false
)

class DashboardViewModel : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private val db   = try { FirebaseDatabase.getInstance() } catch (e: Exception) { null }

    private var incidentListener: ValueEventListener? = null
    private var communityListener: ListenerRegistration? = null
    private var vehicleListener: ValueEventListener? = null
    private var notifListener: ValueEventListener? = null

    private val firestore = try { Firebase.firestore } catch (e: Exception) { null }

    init {
        loadUserInfo()
        observeIncidents()
        observeCommunityPosts()
        observeVehicleData()
        observeNotifications()
    }

    // ── User ──────────────────────────────────────────────────────────────
    private fun loadUserInfo() {
        val user = auth.currentUser
        val initials = when {
            user?.displayName?.isNotBlank() == true ->
                user.displayName!!.trim().split(" ")
                    .filter { it.isNotEmpty() }.take(2)
                    .joinToString("") { it.first().uppercase() }
            user?.email?.isNotBlank() == true -> user.email!!.first().uppercase()
            else -> "·"
        }
        _state.value = _state.value.copy(
            userInitials = initials,
            userName     = user?.displayName ?: "",
            userEmail    = user?.email ?: ""
        )
    }

    // ── Incidents ─────────────────────────────────────────────────────────
    private fun observeIncidents() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = _state.value.copy(incidents = sampleIncidents(), isLoadingIncidents = false)
            return
        }
        val ref = db?.reference?.child("incidents")?.child(uid) ?: run {
            _state.value = _state.value.copy(incidents = sampleIncidents(), isLoadingIncidents = false)
            return
        }

        // Prevent infinite buffering if DB connection hangs
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (_state.value.isLoadingIncidents) {
                _state.value = _state.value.copy(
                    incidents = sampleIncidents(),
                    isLoadingIncidents = false
                )
            }
        }

        incidentListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val list = snap.children.mapNotNull { child ->
                    try {
                        Incident(
                            id        = child.key ?: "",
                            title     = child.child("title").getValue(String::class.java) ?: "",
                            subtitle  = child.child("subtitle").getValue(String::class.java) ?: "",
                            status    = child.child("status").getValue(String::class.java) ?: "active",
                            timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        )
                    } catch (e: Exception) { null }
                }.sortedByDescending { it.timestamp }
                
                val finalList = if (list.isEmpty()) sampleIncidents() else list
                _state.value = _state.value.copy(incidents = finalList, isLoadingIncidents = false)
            }
            override fun onCancelled(error: DatabaseError) {
                _state.value = _state.value.copy(incidents = sampleIncidents(), isLoadingIncidents = false)
            }
        }
        ref.addValueEventListener(incidentListener!!)
    }

    // ── Community (Firestore real-time) ───────────────────────────────────
    private fun observeCommunityPosts() {
        val fs = firestore ?: run {
            _state.value = _state.value.copy(
                isLoadingCommunity = false,
                communityPosts = sampleCommunityPosts()
            )
            return
        }
        val myUid = auth.currentUser?.uid ?: ""
        communityListener = fs.collection("community_posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    _state.value = _state.value.copy(
                        communityPosts = sampleCommunityPosts(), isLoadingCommunity = false
                    )
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        val ts = doc.getLong("timestamp") ?: 0L
                        @Suppress("UNCHECKED_CAST")
                        val likedBy = doc.get("likedBy") as? List<String> ?: emptyList()
                        CommunityPost(
                            id        = doc.id,
                            author    = doc.getString("author") ?: "User",
                            initials  = doc.getString("initials") ?: "U",
                            content   = doc.getString("content") ?: "",
                            likes     = doc.getLong("likes")?.toInt() ?: 0,
                            comments  = doc.getLong("comments")?.toInt() ?: 0,
                            authorUid = doc.getString("authorUid") ?: "",
                            likedByMe = myUid.isNotEmpty() && likedBy.contains(myUid),
                            timestamp = ts,
                            timeAgo   = formatTimestamp(ts)
                        )
                    } catch (e: Exception) { null }
                }
                val finalList = if (list.isEmpty()) sampleCommunityPosts() else list
                _state.value = _state.value.copy(communityPosts = finalList, isLoadingCommunity = false)
            }
    }

    /** Submit a new community post to Firestore */
    fun submitPost(content: String, onDone: (Boolean) -> Unit) {
        val fs = firestore ?: run { onDone(false); return }
        val user = auth.currentUser ?: run { onDone(false); return }
        val displayName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@") ?: "User"
        val initials = displayName.trim().split(" ")
            .filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "U" }
        val post = hashMapOf(
            "author"    to displayName,
            "initials"  to initials,
            "authorUid" to user.uid,
            "content"   to content.trim(),
            "timestamp" to System.currentTimeMillis(),
            "likes"     to 0L,
            "comments"  to 0L,
            "likedBy"   to emptyList<String>()
        )
        viewModelScope.launch {
            try {
                fs.collection("community_posts").add(post).await()
                onDone(true)
            } catch (e: Exception) {
                onDone(false)
            }
        }
    }

    /** Toggle like on a post — uses Firestore array union/remove */
    fun toggleLike(post: CommunityPost) {
        val fs = firestore ?: return
        val uid = auth.currentUser?.uid ?: return
        val docRef = fs.collection("community_posts").document(post.id)
        viewModelScope.launch {
            try {
                val snap = docRef.get().await()
                @Suppress("UNCHECKED_CAST")
                val likedBy = (snap.get("likedBy") as? List<String> ?: emptyList()).toMutableList()
                val currentLikes = snap.getLong("likes")?.toInt() ?: 0
                if (likedBy.contains(uid)) {
                    likedBy.remove(uid)
                    docRef.update("likedBy", likedBy, "likes", (currentLikes - 1).coerceAtLeast(0))
                } else {
                    likedBy.add(uid)
                    docRef.update("likedBy", likedBy, "likes", currentLikes + 1)
                }
            } catch (_: Exception) {}
        }
    }

    // ── Vehicle telemetry (real-time) ─────────────────────────────────────
    private fun observeVehicleData() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = _state.value.copy(isLoadingVehicle = false)
            return
        }
        val ref = db?.reference?.child("vehicles")?.child(uid) ?: run {
            _state.value = _state.value.copy(isLoadingVehicle = false)
            return
        }
        vehicleListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val v = VehicleData(
                    fuel        = snap.child("fuel").getValue(Long::class.java)?.toInt() ?: 64,
                    tyrePsi     = snap.child("tyrePsi").getValue(Long::class.java)?.toInt() ?: 32,
                    gpsActive   = snap.child("gpsActive").getValue(Boolean::class.java) ?: true,
                    engineTemp  = snap.child("engineTemp").getValue(Long::class.java)?.toInt() ?: 92,
                    batteryLevel= snap.child("batteryLevel").getValue(Long::class.java)?.toInt() ?: 87
                )
                _state.value = _state.value.copy(vehicleData = v, isLoadingVehicle = false)
            }
            override fun onCancelled(error: DatabaseError) {
                _state.value = _state.value.copy(isLoadingVehicle = false)
            }
        }
        ref.addValueEventListener(vehicleListener!!)
    }

    // ── Notifications (real-time) ─────────────────────────────────────────
    private fun observeNotifications() {
        val uid = auth.currentUser?.uid ?: return
        val ref = db?.reference?.child("notifications")?.child(uid) ?: return
        notifListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val list = snap.children.mapNotNull { child ->
                    try {
                        NotificationItem(
                            id        = child.key ?: "",
                            title     = child.child("title").getValue(String::class.java) ?: "",
                            body      = child.child("body").getValue(String::class.java) ?: "",
                            timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L,
                            read      = child.child("read").getValue(Boolean::class.java) ?: false
                        )
                    } catch (e: Exception) { null }
                }.sortedByDescending { it.timestamp }
                val unread = list.count { !it.read }
                _state.value = _state.value.copy(notifications = list, unreadCount = unread)
            }
            override fun onCancelled(error: DatabaseError) { /* no-op */ }
        }
        ref.limitToLast(50).addValueEventListener(notifListener!!)
    }

    fun toggleSos() {
        val newSos = !_state.value.sosActive
        _state.value = _state.value.copy(sosActive = newSos)
        // Optionally write SOS state to Firebase
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db?.reference?.child("sos")?.child(uid)?.setValue(
                    mapOf("active" to newSos, "timestamp" to System.currentTimeMillis())
                )
            } catch (_: Exception) {}
        }
    }

    private fun sampleCommunityPosts() = listOf(
        CommunityPost("1", "Amit Sharma",  "AS", "2h ago",   "Heavy traffic near Gandhinagar exit. Road work underway, avoid service lane.", 24, 5),
        CommunityPost("2", "Priya Patel",  "PP", "4h ago",   "Flat tyre repair stall near NH-48 toll booth. Very helpful mechanic.", 18, 3),
        CommunityPost("3", "Raj Kumar",    "RK", "Yesterday","Police checking near Ahmedabad bypass. Keep documents ready.", 31, 8)
    )

    private fun sampleIncidents(): List<Incident> = listOf(
        Incident("1", "Engine Overheating", "Tow requested near Downtown", "resolved", System.currentTimeMillis() - 86400000L),
        Incident("2", "Flat Tire", "Service completed by RescueLink", "resolved", System.currentTimeMillis() - 172800000L)
    )

    override fun onCleared() {
        val uid = auth.currentUser?.uid
        incidentListener?.let {
            uid?.let { id -> db?.reference?.child("incidents")?.child(id)?.removeEventListener(it) }
        }
        communityListener?.remove()   // Firestore ListenerRegistration
        vehicleListener?.let {
            uid?.let { id -> db?.reference?.child("vehicles")?.child(id)?.removeEventListener(it) }
        }
        notifListener?.let {
            uid?.let { id -> db?.reference?.child("notifications")?.child(id)?.removeEventListener(it) }
        }
        super.onCleared()
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    vm: DashboardViewModel = viewModel(),
    chatVm: ChatViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var currentRoute by remember { mutableStateOf("home") }
    val listState = rememberLazyListState()
    var showNotifications by remember { mutableStateOf(false) }

    // Detect scroll for top bar collapse
    val isScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20 } }

    Box(modifier = Modifier.fillMaxSize().background(DashboardTokens.BlackCore)) {
        // Ambient background glows
        DashboardBackground()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                DashboardTopBar(
                    initials           = state.userInitials,
                    userName           = state.userName,
                    unreadCount        = state.unreadCount,
                    isScrolled         = isScrolled,
                    onNotificationClick = { showNotifications = true },
                    onProfileClick     = { currentRoute = "more" }
                )
            },
            bottomBar = {
                DashboardBottomNav(currentRoute) { currentRoute = it }
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = currentRoute,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
                label = "route",
                modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()
            ) { route ->
                when (route) {
                    "home"      -> HomeScreen(state, listState, vm::toggleSos) { msg ->
                        if (msg.isNotBlank()) {
                            chatVm.sendMessage(msg)
                        }
                        currentRoute = "chat"
                    }
                    "map"       -> MapScreen()
                    "community" -> CommunityScreen(state, vm)
                    "chat"      -> AiChatScreen(chatVm)
                    "more"      -> MoreScreen(onLogout)
                    else        -> HomeScreen(state, listState, vm::toggleSos) {}
                }
            }
        }

        // Notification panel overlay
        if (showNotifications) {
            NotificationPanel(
                notifications = state.notifications,
                onDismiss     = { showNotifications = false }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  HOME SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun HomeScreen(
    state: DashboardState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSosTap: () -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { VehicleStatusRow(state.vehicleData, state.isLoadingVehicle) }
        item { QuickActionsRow() }
        item { ServiceHistorySection(state.incidents, state.isLoadingIncidents) }
        item { ChatbotPreviewSection(onNavigateToChat) }
        item { Spacer(Modifier.height(6.dp)) }
    }
}


@Composable
private fun StatusPill(emoji: String, label: String, color: Color, dimColor: Color) {
    Row(
        modifier = Modifier
            .background(dimColor, RoundedCornerShape(100.dp))
            .border(1.dp, color.copy(0.25f), RoundedCornerShape(100.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(emoji, fontSize = 10.sp)
        Text(label, fontFamily = OutfitFontFamily, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  VEHICLE STATUS ROW — real-time Firebase data
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VehicleStatusRow(vehicle: VehicleData, isLoading: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Vehicle Status", fontFamily = OutfitFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CircularProgressIndicator(color = DashboardTokens.RedHot, modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                    Text("Live", fontFamily = OutfitFontFamily, fontSize = 9.sp, color = DashboardTokens.RedHot)
                }
            } else {
                LiveBadge()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VehicleMetricCard(
                icon    = Icons.Rounded.LocalGasStation,
                label   = "Fuel",
                value   = "${vehicle.fuel}%",
                color   = when {
                    vehicle.fuel > 50 -> DashboardTokens.Green
                    vehicle.fuel > 20 -> DashboardTokens.Orange
                    else              -> DashboardTokens.RedHot
                },
                dimColor = when {
                    vehicle.fuel > 50 -> DashboardTokens.GreenDim
                    vehicle.fuel > 20 -> DashboardTokens.OrangeDim
                    else              -> DashboardTokens.RedDim
                },
                progress = vehicle.fuel / 100f,
                modifier = Modifier.weight(1f)
            )
            VehicleMetricCard(
                icon    = Icons.Rounded.Speed,
                label   = "Tyre",
                value   = "${vehicle.tyrePsi} PSI",
                color   = DashboardTokens.Orange,
                dimColor = DashboardTokens.OrangeDim,
                progress = vehicle.tyrePsi / 45f,
                modifier = Modifier.weight(1f)
            )
            VehicleMetricCard(
                icon    = Icons.Rounded.LocationOn,
                label   = "GPS",
                value   = if (vehicle.gpsActive) "Active" else "Off",
                color   = if (vehicle.gpsActive) DashboardTokens.Blue else DashboardTokens.White35,
                dimColor = DashboardTokens.BlueDim,
                progress = if (vehicle.gpsActive) 1f else 0f,
                modifier = Modifier.weight(1f)
            )
        }

        // Engine temp + battery row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WideMetricCard(
                icon  = Icons.Rounded.Thermostat,
                label = "Engine Temp",
                value = "${vehicle.engineTemp}°C",
                color = when {
                    vehicle.engineTemp > 105 -> DashboardTokens.RedHot
                    vehicle.engineTemp > 90  -> DashboardTokens.Orange
                    else                     -> DashboardTokens.Green
                },
                modifier = Modifier.weight(1f)
            )
            WideMetricCard(
                icon  = Icons.Rounded.BatteryChargingFull,
                label = "Battery",
                value = "${vehicle.batteryLevel}%",
                color = when {
                    vehicle.batteryLevel > 60 -> DashboardTokens.Green
                    vehicle.batteryLevel > 30 -> DashboardTokens.Orange
                    else                      -> DashboardTokens.RedHot
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VehicleMetricCard(
    icon: ImageVector, label: String, value: String,
    color: Color, dimColor: Color, progress: Float, modifier: Modifier
) {
    Column(
        modifier = modifier
            .background(DashboardTokens.CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, DashboardTokens.Rim, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(dimColor, CircleShape).border(1.dp, color.copy(0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)) }

        Text(value, fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        Text(label, fontFamily = OutfitFontFamily, fontSize = 9.sp, color = DashboardTokens.White35)

        // Progress bar
        Box(
            modifier = Modifier.fillMaxWidth().height(3.dp)
                .background(DashboardTokens.Rim2, RoundedCornerShape(100.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(color.copy(0.6f), color)), RoundedCornerShape(100.dp))
            )
        }
    }
}

@Composable
private fun WideMetricCard(icon: ImageVector, label: String, value: String, color: Color, modifier: Modifier) {
    Row(
        modifier = modifier
            .background(DashboardTokens.CardBg, RoundedCornerShape(12.dp))
            .border(1.dp, DashboardTokens.Rim, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Column {
            Text(value, fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, fontFamily = OutfitFontFamily, fontSize = 9.sp, color = DashboardTokens.White35)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  QUICK ACTIONS ROW
// ─────────────────────────────────────────────────────────────────────────────

data class QuickActionService(
    val name: String,
    val phone: String,
    val address: String,
    val distance: String,
    val rating: String
)

data class QuickActionInfo(
    val emoji: String,
    val label: String,
    val color: Color,
    val dimColor: Color,
    val searchQuery: String,
    val services: List<QuickActionService>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionsRow() {
    val context = LocalContext.current
    var selectedAction by remember { mutableStateOf<QuickActionInfo?>(null) }

    val actions = listOf(
        QuickActionInfo(
            emoji = "🔧", label = "Mechanic",
            color = DashboardTokens.Orange, dimColor = DashboardTokens.OrangeDim,
            searchQuery = "car mechanic near me",
            services = listOf(
                QuickActionService("Sharma Auto Repair",     "+91-9876543210", "NH-48, Km 12, Gujarat",     "0.8 km",  "⭐ 4.7"),
                QuickActionService("Patel Motors",           "+91-9123456780", "Ring Road, Ahmedabad",      "1.4 km",  "⭐ 4.5"),
                QuickActionService("24x7 Road Assist",       "+91-1800112233", "Gandhinagar Hwy, Gujarat",  "2.1 km",  "⭐ 4.8")
            )
        ),
        QuickActionInfo(
            emoji = "⛽", label = "Fuel",
            color = DashboardTokens.Green, dimColor = DashboardTokens.GreenDim,
            searchQuery = "fuel station near me",
            services = listOf(
                QuickActionService("HP Petrol Pump",         "+91-9988776655", "Expressway, Exit 14",        "1.2 km",  "⭐ 4.3"),
                QuickActionService("Indian Oil — Vasan",     "+91-9001122334", "State Hwy 41, Gujarat",     "1.9 km",  "⭐ 4.6"),
                QuickActionService("Bharat Petroleum",       "+91-9445566778", "NH-48 Service Lane",         "3.0 km",  "⭐ 4.4")
            )
        ),
        QuickActionInfo(
            emoji = "🚑", label = "Hospital",
            color = DashboardTokens.RedHot, dimColor = DashboardTokens.RedDim,
            searchQuery = "hospital near me",
            services = listOf(
                QuickActionService("Civil Hospital",         "108",            "Asarwa, Ahmedabad",          "2.8 km",  "⭐ 4.2"),
                QuickActionService("Sterling Hospitals",     "+91-7926570094", "Gurukul Rd, Ahmedabad",     "3.5 km",  "⭐ 4.8"),
                QuickActionService("Apollo Hospitals",       "+91-7923969090", "Bhat GIDC, Gandhinagar",    "5.2 km",  "⭐ 4.9")
            )
        ),
        QuickActionInfo(
            emoji = "👮", label = "Police",
            color = DashboardTokens.Blue, dimColor = DashboardTokens.BlueDim,
            searchQuery = "police station near me",
            services = listOf(
                QuickActionService("Highway Patrol — NH48",  "100",            "NH-48, Km 18 Booth",         "0.9 km",  "⭐ 4.1"),
                QuickActionService("Kheda Police Station",   "+91-2692-220100","Kheda, Gujarat",             "4.1 km",  "⭐ 4.0"),
                QuickActionService("Traffic Control Room",   "1095",           "Ahmedabad Traffic HQ",      "—",       "⭐ 4.3")
            )
        ),
        QuickActionInfo(
            emoji = "🔋", label = "Charge",
            color = DashboardTokens.Purple, dimColor = DashboardTokens.PurpleDim,
            searchQuery = "EV charging station near me",
            services = listOf(
                QuickActionService("Tata Power EV Hub",      "+91-1800209090", "Sarkhej-Gandhinagar Hwy",   "1.6 km",  "⭐ 4.7"),
                QuickActionService("EESL Charging Station",  "+91-1800123456", "Near Toll Plaza, NH-48",    "2.3 km",  "⭐ 4.5"),
                QuickActionService("Ather Grid Point",       "+91-8069019999", "Thaltej, Ahmedabad",        "3.8 km",  "⭐ 4.6")
            )
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quick Actions", fontFamily = OutfitFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions.forEach { action ->
                QuickActionCard(
                    emoji    = action.emoji,
                    label    = action.label,
                    color    = action.color,
                    dimColor = action.dimColor,
                    onClick  = { selectedAction = action }
                )
            }
        }
    }

    // Bottom Sheet
    selectedAction?.let { action ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest  = { selectedAction = null },
            sheetState        = sheetState,
            containerColor    = DashboardTokens.CardBg,
            tonalElevation    = 0.dp,
            scrimColor        = Color.Black.copy(alpha = 0.65f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(48.dp).background(action.dimColor, CircleShape)
                            .border(1.dp, action.color.copy(0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(action.emoji, fontSize = 22.sp) }
                    Column {
                        Text("${action.label} Services", fontFamily = OutfitFontFamily, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("Nearest options around you", fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.White60)
                    }
                }

                HorizontalDivider(color = DashboardTokens.Rim, thickness = 0.5.dp)

                // Service cards
                action.services.forEach { service ->
                    ServiceDetailCard(
                        service = service,
                        color   = action.color,
                        dimColor = action.dimColor,
                        onCall  = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${service.phone}"))
                            context.startActivity(intent)
                        },
                        onMap   = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(service.address)}"))
                            context.startActivity(intent)
                        }
                    )
                }

                // Search on Maps
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(action.color.copy(0.15f), action.dimColor)),
                            RoundedCornerShape(14.dp)
                        )
                        .border(1.dp, action.color.copy(0.3f), RoundedCornerShape(14.dp))
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(action.searchQuery)}"))
                            context.startActivity(intent)
                        }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Map, null, tint = action.color, modifier = Modifier.size(16.dp))
                        Text("Find more on Google Maps", fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = action.color)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceDetailCard(
    service: QuickActionService,
    color: Color,
    dimColor: Color,
    onCall: () -> Unit,
    onMap: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(DashboardTokens.CardBg2, RoundedCornerShape(14.dp))
            .border(1.dp, DashboardTokens.Rim, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(service.name, fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(service.address, fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.White60, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.background(dimColor, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(service.distance, fontFamily = OutfitFontFamily, fontSize = 9.sp, color = color, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.background(DashboardTokens.CardBg3, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(service.rating, fontFamily = OutfitFontFamily, fontSize = 9.sp, color = DashboardTokens.White60)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Call button
                Box(
                    modifier = Modifier.weight(1f)
                        .background(color.copy(0.15f), RoundedCornerShape(10.dp))
                        .border(1.dp, color.copy(0.3f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onCall)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Call, null, tint = color, modifier = Modifier.size(13.dp))
                        Text("Call", fontFamily = OutfitFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
                    }
                }
                // Directions button
                Box(
                    modifier = Modifier.weight(1f)
                        .background(DashboardTokens.CardBg3, RoundedCornerShape(10.dp))
                        .border(1.dp, DashboardTokens.Rim2, RoundedCornerShape(10.dp))
                        .clickable(onClick = onMap)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Directions, null, tint = DashboardTokens.White60, modifier = Modifier.size(13.dp))
                        Text("Directions", fontFamily = OutfitFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DashboardTokens.White60)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(emoji: String, label: String, color: Color, dimColor: Color, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .width(68.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .background(DashboardTokens.CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(0.3f), RoundedCornerShape(14.dp))
            .clickable {
                scope.launch {
                    scale.animateTo(0.90f, tween(80))
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                onClick()
            }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(dimColor, CircleShape)
                .border(1.dp, color.copy(0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 16.sp) }
        Text(label, fontFamily = OutfitFontFamily, fontSize = 9.sp, color = color.copy(0.85f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SERVICE HISTORY
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ServiceHistorySection(incidents: List<Incident>, isLoading: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Service History", "View All")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DashboardTokens.CardBg, RoundedCornerShape(16.dp))
                .border(1.dp, DashboardTokens.Rim, RoundedCornerShape(16.dp))
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DashboardTokens.RedHot, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                    }
                }
                incidents.isEmpty() -> {
                    EmptyState("No service history yet", "Your incidents will appear here in real-time")
                }
                else -> {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        incidents.forEachIndexed { idx, incident ->
                            HistoryRow(incident)
                            if (idx < incidents.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DashboardTokens.Rim, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(incident: Incident) {
    val dotColor = when (incident.status) {
        "resolved" -> DashboardTokens.Green
        "pending"  -> DashboardTokens.Orange
        else       -> DashboardTokens.RedHot
    }
    val isLive = incident.status == "active"
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(incident.title, fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(incident.subtitle, fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.White60)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(if (isLive) DashboardTokens.RedDim else DashboardTokens.Rim, RoundedCornerShape(100.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                if (isLive) "LIVE" else formatTimestamp(incident.timestamp),
                fontFamily = OutfitFontFamily, fontSize = 8.sp,
                color = if (isLive) DashboardTokens.RedHot else DashboardTokens.White35,
                fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

private fun formatTimestamp(ts: Long): String {
    if (ts == 0L) return ""
    val diff  = System.currentTimeMillis() - ts
    val hours = diff / 3_600_000
    val days  = diff / 86_400_000
    return when {
        hours < 1  -> "Just now"
        hours < 24 -> "${hours}h ago"
        days  < 30 -> "${days}d ago"
        else       -> "${days / 30}mo ago"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CHATBOT PREVIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ChatbotPreviewSection(onQuickAction: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("AI Emergency Assistant", "Open Chat")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DashboardTokens.CardBg, RoundedCornerShape(16.dp))
                .border(1.dp, DashboardTokens.Rim, RoundedCornerShape(16.dp))
                .clickable { onQuickAction("") }
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.size(32.dp).background(DashboardTokens.RedDim, CircleShape).border(1.dp, DashboardTokens.RedMid, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = DashboardTokens.RedHot, modifier = Modifier.size(15.dp)) }
                    Box(
                        modifier = Modifier.background(DashboardTokens.CardBg3, RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp)).padding(10.dp)
                    ) {
                        Text("I'm your AI road assistant. Need help with a breakdown, nearby services, or emergency guidance?", fontFamily = OutfitFontFamily, fontSize = 12.sp, color = Color.White, lineHeight = 17.sp)
                    }
                }
                Text("Quick actions:", fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.White35)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickChip("🔧 Mechanic") { onQuickAction("I need a mechanic near me.") }
                    QuickChip("⛽ Fuel") { onQuickAction("Where is the nearest gas station?") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickChip("🚑 Hospital") { onQuickAction("Where is the nearest hospital?") }
                    QuickChip("👮 Police") { onQuickAction("How do I contact the local police?") }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(DashboardTokens.CardBg3, RoundedCornerShape(10.dp))
            .border(1.dp, DashboardTokens.Rim2, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) { Text(text, fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.White60) }
}

// ═══════════════════════════════════════════════════════════════════════════
//  MAP SCREEN  (Leaflet.js via WebView — no API key required)
// ═══════════════════════════════════════════════════════════════════════════

/** Returns a self-contained HTML page that renders a Leaflet.js map with live marker updates. */
private fun buildLeafletHtml(lat: Double, lng: Double): String = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>RescueLink Map</title>
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body, #map { width: 100%; height: 100%; background: #0d0d0d; }
    .leaflet-container { background: #1a1a1a !important; }
    .leaflet-tile { filter: brightness(0.85) saturate(0.9) hue-rotate(5deg); }
    @keyframes pulse {
      0%   { box-shadow: 0 0 0 0 rgba(232,0,29,0.7), 0 0 8px #E8001D; }
      70%  { box-shadow: 0 0 0 12px rgba(232,0,29,0), 0 0 8px #E8001D; }
      100% { box-shadow: 0 0 0 0 rgba(232,0,29,0), 0 0 8px #E8001D; }
    }
    .live-dot {
      width: 18px; height: 18px; border-radius: 50%;
      background: #E8001D; border: 3px solid #fff;
      animation: pulse 1.8s infinite;
    }
  </style>
</head>
<body>
  <div id="map"></div>
  <script>
    var map = L.map('map', { zoomControl: true, attributionControl: true })
              .setView([$lat, $lng], 15);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      maxZoom: 19
    }).addTo(map);

    /* Live user location marker (pulsing dot) */
    var userIcon = L.divIcon({
      html: '<div class="live-dot"></div>',
      iconSize: [18, 18], iconAnchor: [9, 9], className: ''
    });
    var userMarker = L.marker([$lat, $lng], { icon: userIcon })
      .addTo(map)
      .bindPopup('<b>📍 Your Live Location</b><br><span style="color:#E8001D;font-size:11px;">● LIVE GPS</span>', { maxWidth: 200 })
      .openPopup();

    /* Nearby sample markers (offset from initial coords) */
    var mechIcon = L.divIcon({
      html: '<div style="width:24px;height:24px;border-radius:6px;background:#1a2a1a;border:2px solid #22C55E;display:flex;align-items:center;justify-content:center;font-size:13px;">🔧</div>',
      iconSize: [24, 24], iconAnchor: [12, 12], className: ''
    });
    var fuelIcon = L.divIcon({
      html: '<div style="width:24px;height:24px;border-radius:6px;background:#1a1a2a;border:2px solid #3B82F6;display:flex;align-items:center;justify-content:center;font-size:13px;">⛽</div>',
      iconSize: [24, 24], iconAnchor: [12, 12], className: ''
    });

    L.marker([$lat + 0.004, $lng - 0.005], { icon: mechIcon }).addTo(map)
     .bindPopup('<b>🔧 Patel Auto Workshop</b><br>★★★★★ Open Now<br>~0.8 km away');
    L.marker([$lat - 0.003, $lng + 0.006], { icon: mechIcon }).addTo(map)
     .bindPopup('<b>🔧 Sharma Motors</b><br>★★★★☆ Open Now<br>~1.4 km away');
    L.marker([$lat + 0.006, $lng + 0.004], { icon: fuelIcon }).addTo(map)
     .bindPopup('<b>⛽ HP Petrol Pump</b><br>24 hrs • Open Now<br>~1.2 km away');

    /**
     * Called from Android via evaluateJavascript().
     * Moves the live marker AND re-centres the map.
     */
    function setLocation(newLat, newLng) {
      userMarker.setLatLng([newLat, newLng]);
      map.setView([newLat, newLng], 15, { animate: true, duration: 0.5 });
    }
  </script>
</body>
</html>
""".trimIndent()

@Composable
private fun MapScreen() {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Default coords: Ahmedabad, Gujarat (shown until GPS resolves)
    var userLat by remember { mutableDoubleStateOf(23.0225) }
    var userLng by remember { mutableDoubleStateOf(72.5714) }
    var isLiveTracking by remember { mutableStateOf(false) }

    // WebView reference so we can call JS bridge after location updates
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }
    )

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            launcher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Start live location updates whenever permission is granted; stop on dispose
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose {}

        val fusedClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 3_000L   // update every 3 s
        ).setMinUpdateIntervalMillis(1_500L)           // but no faster than 1.5 s
         .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                userLat = location.latitude
                userLng  = location.longitude
                isLiveTracking = true
                // Push new coords into Leaflet via JS bridge
                webViewRef?.evaluateJavascript(
                    "setLocation(${location.latitude}, ${location.longitude})", null
                )
            }
        }

        try {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                android.os.Looper.getMainLooper()
            )
        } catch (_: SecurityException) { /* permission revoked mid-session */ }

        onDispose {
            fusedClient.removeLocationUpdates(locationCallback)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row with live badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Map & Navigation", fontFamily = OutfitFontFamily, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Locate nearby assistance", fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.White60)
            }
            if (isLiveTracking) {
                val pulse = rememberInfiniteTransition(label = "livePulse")
                val alpha by pulse.animateFloat(
                    initialValue = 1f, targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "livePulseAlpha"
                )
                Box(
                    modifier = Modifier
                        .background(DashboardTokens.RedDim, RoundedCornerShape(100.dp))
                        .border(1.dp, DashboardTokens.RedMid, RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).background(DashboardTokens.RedHot.copy(alpha = alpha), CircleShape))
                        Text("LIVE", fontFamily = OutfitFontFamily, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = DashboardTokens.RedHot)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, DashboardTokens.Rim, RoundedCornerShape(16.dp))
        ) {
            // Leaflet.js WebView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        webViewClient = WebViewClient()
                        settings.apply {
                            javaScriptEnabled   = true
                            domStorageEnabled   = true
                            cacheMode           = WebSettings.LOAD_NO_CACHE
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        setBackgroundColor(android.graphics.Color.parseColor("#0d0d0d"))
                        loadDataWithBaseURL(
                            "https://rescuelink.app",
                            buildLeafletHtml(userLat, userLng),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                update = { wv -> webViewRef = wv }
            )

            // Status overlay at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f))))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, null, tint = DashboardTokens.RedHot, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        when {
                            isLiveTracking        -> "Live GPS — %.5f, %.5f".format(userLat, userLng)
                            hasLocationPermission -> "Acquiring GPS signal…"
                            else                  -> "Map — OpenStreetMap"
                        },
                        fontFamily = OutfitFontFamily, fontSize = 11.sp, color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    Text("Leaflet.js", fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.RedHot, fontWeight = FontWeight.Bold)
                }
            }
        }

        SectionHeader("Nearby Assistance", "See All")
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ServiceCard("Mechanics", Icons.Rounded.Build,           DashboardTokens.Orange, "3 nearby")
            ServiceCard("Fuel",      Icons.Rounded.LocalGasStation, DashboardTokens.Green,  "1.2 km")
            ServiceCard("Hospital",  Icons.Rounded.MedicalServices, DashboardTokens.RedHot, "2.8 km")
            ServiceCard("Police",    Icons.Rounded.Security,        DashboardTokens.Blue,   "0.9 km")
        }
    }
}

@Composable
private fun ServiceCard(name: String, icon: ImageVector, color: Color, distance: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(56.dp)
                .background(DashboardTokens.CardBg2, RoundedCornerShape(14.dp))
                .border(1.dp, color.copy(0.25f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(5.dp))
        Text(name,     fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.White60)
        Text(distance, fontFamily = OutfitFontFamily, fontSize = 9.sp,  color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  COMMUNITY SCREEN  (Firestore-backed)
// ═══════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityScreen(state: DashboardState, vm: DashboardViewModel) {
    var showPostSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Community", fontFamily = OutfitFontFamily, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Live updates from travellers", fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.White60)
                }
                Box(
                    modifier = Modifier
                        .background(Brush.linearGradient(listOf(DashboardTokens.RedHot, DashboardTokens.RedDeep)), RoundedCornerShape(12.dp))
                        .clickable { showPostSheet = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Text("Post", fontFamily = OutfitFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
        if (state.isLoadingCommunity) {
            item {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DashboardTokens.RedHot, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }
        } else if (state.communityPosts.isEmpty()) {
            item {
                EmptyState("No posts yet", "Be the first to share an update with the community!")
            }
        } else {
            items(state.communityPosts, key = { it.id }) { post ->
                CommunityPostCard(post = post, onLike = { vm.toggleLike(post) })
            }
        }
    }

    // ── New-post bottom sheet ──────────────────────────────────────────────
    if (showPostSheet) {
        val context = LocalContext.current
        CreatePostSheet(
            onDismiss = { showPostSheet = false },
            onSubmit   = { content ->
                vm.submitPost(content) { success ->
                    showPostSheet = false
                    if (success) {
                        android.widget.Toast.makeText(context, "Post shared successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Failed to share post. Check Firebase setup.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePostSheet(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    val maxChars = 280

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = DashboardTokens.CardBg,
        tonalElevation   = 0.dp,
        scrimColor       = Color.Black.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(38.dp)
                        .background(DashboardTokens.RedDim, CircleShape)
                        .border(1.dp, DashboardTokens.RedMid, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Edit, null, tint = DashboardTokens.RedHot, modifier = Modifier.size(18.dp)) }
                Column {
                    Text("New Community Post", fontFamily = OutfitFontFamily, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Share a road update or tip", fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.White60)
                }
            }

            HorizontalDivider(color = DashboardTokens.Rim, thickness = 0.5.dp)

            // Text field
            OutlinedTextField(
                value          = text,
                onValueChange  = { if (it.length <= maxChars) text = it },
                placeholder    = {
                    Text(
                        "What's happening on the road? Traffic, breakdowns, hazards…",
                        fontFamily = OutfitFontFamily, fontSize = 13.sp, color = DashboardTokens.White35,
                        lineHeight = 18.sp
                    )
                },
                modifier       = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                textStyle      = TextStyle(fontFamily = OutfitFontFamily, fontSize = 13.sp, color = Color.White, lineHeight = 19.sp),
                colors         = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor   = DashboardTokens.CardBg3,
                    unfocusedContainerColor = DashboardTokens.CardBg3,
                    focusedBorderColor      = DashboardTokens.RedHot,
                    unfocusedBorderColor    = DashboardTokens.Rim2
                ),
                shape    = RoundedCornerShape(14.dp),
                maxLines = 8
            )

            // Char counter + submit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "${text.length} / $maxChars",
                    fontFamily = OutfitFontFamily, fontSize = 10.sp,
                    color = if (text.length > maxChars * 0.9) DashboardTokens.Orange else DashboardTokens.White35
                )
                Box(
                    modifier = Modifier
                        .background(
                            if (text.isNotBlank() && !isPosting)
                                Brush.linearGradient(listOf(DashboardTokens.RedHot, DashboardTokens.RedDeep))
                            else
                                Brush.linearGradient(listOf(DashboardTokens.Rim2, DashboardTokens.Rim)),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = text.isNotBlank() && !isPosting) {
                            isPosting = true
                            onSubmit(text)
                        }
                        .padding(horizontal = 22.dp, vertical = 11.dp)
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Share Post", fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityPostCard(post: CommunityPost, onLike: () -> Unit) {
    val likeColor by animateColorAsState(
        if (post.likedByMe) DashboardTokens.RedHot else DashboardTokens.White35,
        tween(200), label = "likeColor"
    )
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(DashboardTokens.CardBg, RoundedCornerShape(16.dp))
            .border(1.dp, DashboardTokens.Rim, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp)
                        .background(DashboardTokens.RedDim, CircleShape)
                        .border(1.dp, DashboardTokens.RedMid, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(post.initials, fontFamily = OutfitFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DashboardTokens.RedHot) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.author,  fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(post.timeAgo, fontFamily = OutfitFontFamily, fontSize = 9.sp,  color = DashboardTokens.White35)
                }
            }
            Text(post.content, fontFamily = OutfitFontFamily, fontSize = 12.sp, color = DashboardTokens.WhitePure, lineHeight = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Like button — tappable, colour-animated
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onLike)
                        .background(if (post.likedByMe) DashboardTokens.RedDim else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (post.likedByMe) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        null, tint = likeColor, modifier = Modifier.size(14.dp)
                    )
                    Text("${post.likes}", fontFamily = OutfitFontFamily, fontSize = 10.sp, color = likeColor, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Rounded.Comment, null, tint = DashboardTokens.White35, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("${post.comments}", fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.White60)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  AI CHAT SCREEN  — redesigned with formatted output & premium UI
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Renders a single run of text, applying bold styling to **..** segments
 * and coloring emoji-prefixed lines with a subtle accent.
 */
@Composable
private fun FormattedMessageText(raw: String, isUser: Boolean) {
    val baseColor    = Color.White
    val dimColor     = if (isUser) Color.White.copy(0.85f) else DashboardTokens.White80
    val accentColor  = if (isUser) Color.White else DashboardTokens.RedHot

    // Split into lines, render each
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        raw.lines().forEach { line ->
            val isBullet  = line.trimStart().startsWith("•")
            val trimmed   = line.trimStart()

            // Build annotated string for bold segments
            val annotated = buildAnnotatedStringWithBold(
                text        = line,
                boldColor   = accentColor,
                normalColor = if (isBullet) dimColor else baseColor
            )

            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text       = annotated,
                    fontSize   = 13.sp,
                    lineHeight = 20.sp,
                    fontFamily = OutfitFontFamily,
                    modifier   = if (isBullet) Modifier.padding(start = 4.dp) else Modifier
                )
            }
        }
    }
}

/**
 * Builds an AnnotatedString that bolds any text wrapped in ** ** markers.
 */
fun buildAnnotatedStringWithBold(
    text: String,
    boldColor: Color,
    normalColor: Color
): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    val pattern = Regex("""\*\*(.+?)\*\*""")
    var lastIndex = 0
    pattern.findAll(text).forEach { match ->
        // Append normal text before this match
        if (match.range.first > lastIndex) {
            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = normalColor))
            builder.append(text.substring(lastIndex, match.range.first))
            builder.pop()
        }
        // Append bold text
        builder.pushStyle(
            androidx.compose.ui.text.SpanStyle(
                color      = boldColor,
                fontWeight = FontWeight.Bold
            )
        )
        builder.append(match.groupValues[1])
        builder.pop()
        lastIndex = match.range.last + 1
    }
    // Append any remaining normal text
    if (lastIndex < text.length) {
        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = normalColor))
        builder.append(text.substring(lastIndex))
        builder.pop()
    }
    return builder.toAnnotatedString()
}

/** Animated three-dot typing indicator for when the AI is generating. */
@Composable
private fun TypingIndicator() {
    val inf = rememberInfiniteTransition(label = "typing")
    val d1 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(0)),   "d1")
    val d2 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(150)), "d2")
    val d3 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(300)), "d3")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // Avatar
        Box(
            modifier = Modifier.size(28.dp)
                .background(
                    Brush.linearGradient(listOf(DashboardTokens.RedDeep, DashboardTokens.RedHot)),
                    CircleShape
                )
                .border(1.dp, DashboardTokens.RedMid, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = Color.White, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(DashboardTokens.CardBg3, RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .border(1.dp, DashboardTokens.Rim2, RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(d1, d2, d3).forEach { alpha ->
                    Box(Modifier.size(6.dp).graphicsLayer { this.alpha = alpha }.background(DashboardTokens.RedHot, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun AiChatScreen(chatVm: ChatViewModel = viewModel()) {
    val messages  = chatVm.chatMessages
    val isTyping  = chatVm.isTyping.value
    var input     by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showSuggestions by remember { mutableStateOf(true) }

    // Auto-scroll to newest message
    LaunchedEffect(messages.size, isTyping) {
        listState.animateScrollToItem(0)
    }

    val inf = rememberInfiniteTransition(label = "headerPulse")
    val pulseAlpha by inf.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), "pulse")

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Premium glassmorphism header ──────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0A0007), DashboardTokens.BlackCore)
                    )
                )
                .drawBehind {
                    drawLine(DashboardTokens.RedMid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Animated TARS avatar
                Box(contentAlignment = Alignment.Center) {
                    // Outer glow ring
                    Box(
                        modifier = Modifier.size(52.dp).graphicsLayer { alpha = pulseAlpha * 0.4f }
                            .background(Brush.radialGradient(listOf(DashboardTokens.RedHot, Color.Transparent)), CircleShape)
                    )
                    Box(
                        modifier = Modifier.size(44.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF2A0010), DashboardTokens.RedDeep)),
                                CircleShape
                            )
                            .border(1.5.dp, DashboardTokens.RedHot.copy(0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "TARS",
                        fontFamily    = BebasNeueFontFamily,
                        fontSize      = 22.sp,
                        letterSpacing = 2.sp,
                        style         = TextStyle(brush = Brush.linearGradient(listOf(Color.White, DashboardTokens.RedGlow)))
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(6.dp).graphicsLayer { alpha = pulseAlpha }.background(DashboardTokens.Green, CircleShape))
                        Text(
                            if (isTyping) "Generating response…" else "AI Emergency Assistant · Online",
                            fontFamily = OutfitFontFamily, fontSize = 10.sp,
                            color = if (isTyping) DashboardTokens.Orange else DashboardTokens.Green
                        )
                    }
                }
                // Clear chat pill
                Box(
                    modifier = Modifier
                        .background(DashboardTokens.CardBg3, RoundedCornerShape(100.dp))
                        .border(1.dp, DashboardTokens.Rim2, RoundedCornerShape(100.dp))
                        .clickable {
                            chatVm.chatMessages.clear()
                            chatVm.chatMessages.add(
                                Message("Hi! I'm **TARS**, your AI emergency road assistant. How can I help you today?\n\n**I can help with:**\n• Emergency SOS guidance\n• Road hazard information\n• Vehicle troubleshooting\n• Route assistance & navigation tips", false)
                            )
                            showSuggestions = true
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("New Chat", fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.White60, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Message list ──────────────────────────────────────────────────
        LazyColumn(
            state             = listState,
            modifier          = Modifier.weight(1f).padding(horizontal = 14.dp),
            reverseLayout     = true,
            contentPadding    = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Typing indicator (shown at top since reverseLayout = true)
            if (isTyping) {
                item { TypingIndicator() }
            }

            items(messages.reversed()) { msg ->
                val isAi = !msg.isUser
                val enterAnim = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }

                AnimatedVisibility(visible = true, enter = enterAnim) {
                    if (isAi) {
                        // ── AI bubble ──
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier.size(28.dp)
                                    .background(
                                        Brush.linearGradient(listOf(DashboardTokens.RedDeep, DashboardTokens.RedHot)),
                                        CircleShape
                                    )
                                    .border(1.dp, DashboardTokens.RedMid, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF17121A), DashboardTokens.CardBg3),
                                                start = Offset.Zero, end = Offset(0f, Float.POSITIVE_INFINITY)
                                            ),
                                            RoundedCornerShape(topStart = 2.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                                        )
                                        .border(
                                            1.dp,
                                            Brush.linearGradient(listOf(DashboardTokens.RedMid.copy(0.4f), DashboardTokens.Rim2)),
                                            RoundedCornerShape(topStart = 2.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    FormattedMessageText(raw = msg.text, isUser = false)
                                }
                                Spacer(Modifier.height(3.dp))
                                Text("TARS · just now", fontFamily = OutfitFontFamily, fontSize = 9.sp, color = DashboardTokens.White35, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    } else {
                        // ── User bubble ──
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Top) {
                            Column(horizontalAlignment = Alignment.End) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 260.dp)
                                        .background(
                                            Brush.linearGradient(listOf(DashboardTokens.RedHot, DashboardTokens.RedDeep)),
                                            RoundedCornerShape(topStart = 16.dp, topEnd = 2.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    FormattedMessageText(raw = msg.text, isUser = true)
                                }
                                Spacer(Modifier.height(3.dp))
                                Text("You · just now", fontFamily = OutfitFontFamily, fontSize = 9.sp, color = DashboardTokens.White35, modifier = Modifier.padding(end = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Quick suggestion chips ────────────────────────────────────────
        AnimatedVisibility(
            visible = showSuggestions && messages.size <= 1,
            enter   = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit    = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DashboardTokens.BlackCore)
                    .drawBehind { drawLine(DashboardTokens.Rim, Offset(0f, 0f), Offset(size.width, 0f), 0.5.dp.toPx()) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Quick Actions", fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.White35, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chatVm.suggestions.forEach { suggestion ->
                        Box(
                            modifier = Modifier
                                .background(DashboardTokens.CardBg3, RoundedCornerShape(100.dp))
                                .border(1.dp, DashboardTokens.Rim2, RoundedCornerShape(100.dp))
                                .clickable {
                                    showSuggestions = false
                                    chatVm.sendMessage(suggestion)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(suggestion, fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.White80, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // ── Premium input bar ─────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(DashboardTokens.BlackCore, Color(0xFF0A0007)))
                )
                .drawBehind { drawLine(DashboardTokens.RedMid.copy(0.4f), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value         = input,
                    onValueChange = { input = it },
                    placeholder   = {
                        Text(
                            "Ask TARS anything…",
                            fontFamily = OutfitFontFamily,
                            fontSize   = 13.sp,
                            color      = DashboardTokens.White35
                        )
                    },
                    modifier      = Modifier.weight(1f).heightIn(min = 50.dp),
                    textStyle     = TextStyle(fontFamily = OutfitFontFamily, fontSize = 13.sp, color = Color.White, lineHeight = 20.sp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor   = DashboardTokens.CardBg3,
                        unfocusedContainerColor = Color(0xFF0F0F14),
                        focusedBorderColor      = DashboardTokens.RedHot,
                        unfocusedBorderColor    = DashboardTokens.Rim2
                    ),
                    shape    = RoundedCornerShape(16.dp),
                    maxLines = 4,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = {
                            if (input.isNotBlank() && !isTyping) {
                                showSuggestions = false
                                chatVm.sendMessage(input)
                                input = ""
                            }
                        }
                    )
                )
                Spacer(Modifier.width(10.dp))
                // Send button — animated when active
                val sendScale by animateFloatAsState(
                    targetValue = if (input.isNotBlank() && !isTyping) 1f else 0.85f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "sendScale"
                )
                Box(
                    modifier = Modifier.size(50.dp)
                        .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                        .background(
                            if (input.isNotBlank() && !isTyping)
                                Brush.linearGradient(listOf(DashboardTokens.RedHot, DashboardTokens.RedDeep))
                            else
                                Brush.linearGradient(listOf(DashboardTokens.CardBg3, DashboardTokens.CardBg2)),
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            if (input.isNotBlank() && !isTyping) Color.Transparent else DashboardTokens.Rim2,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = input.isNotBlank() && !isTyping) {
                            showSuggestions = false
                            chatVm.sendMessage(input)
                            input = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isTyping) {
                        CircularProgressIndicator(
                            color       = DashboardTokens.RedHot,
                            modifier    = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            null,
                            tint     = if (input.isNotBlank()) Color.White else DashboardTokens.White35,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  MORE SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun MoreScreen(onLogout: () -> Unit) {
    val auth  = FirebaseAuth.getInstance()
    val email = auth.currentUser?.email ?: "—"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Settings", fontFamily = OutfitFontFamily, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text("Manage your preferences", fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.White60)
        }
        item {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF1C0008), DashboardTokens.CardBg)), RoundedCornerShape(16.dp))
                    .border(1.dp, DashboardTokens.Rim, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(50.dp)
                            .background(Brush.linearGradient(listOf(DashboardTokens.RedDeep, DashboardTokens.RedHot)), CircleShape)
                            .border(2.dp, DashboardTokens.Rim2, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Account", fontFamily = OutfitFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(email, fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.White60, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Box(modifier = Modifier.background(DashboardTokens.RedDim, RoundedCornerShape(100.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("Premium Member", fontFamily = OutfitFontFamily, fontSize = 9.sp, color = DashboardTokens.RedHot, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item { MoreGroupLabel("Account") }
        item { MoreRow(Icons.Rounded.Person,        "Edit Profile",          "Update your details") }
        item { MoreRow(Icons.Rounded.Notifications, "Notifications",         "Manage alerts & sounds") }
        item { MoreRow(Icons.Rounded.Security,      "Privacy & Security",    "Two-factor, data settings") }
        item { MoreGroupLabel("Vehicle") }
        item { MoreRow(Icons.Rounded.LocalShipping, "My Vehicle",            "Manage vehicle details") }
        item { MoreRow(Icons.Rounded.Build,         "Service Records",       "View maintenance logs") }
        item { MoreRow(Icons.Rounded.LocationOn,    "Emergency Contacts",    "SOS contact list") }
        item { MoreGroupLabel("App") }
        item { MoreRow(Icons.Rounded.Info,          "About RescueLink",      "Version 1.0.0") }
        item { MoreRow(Icons.AutoMirrored.Rounded.ContactSupport,"Help & Support", "FAQs and contact us") }
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(DashboardTokens.RedDim, RoundedCornerShape(14.dp))
                    .border(1.dp, DashboardTokens.RedMid, RoundedCornerShape(14.dp))
                    .clickable(onClick = onLogout)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = DashboardTokens.RedHot, modifier = Modifier.size(17.dp))
                    Text("Sign Out", fontFamily = OutfitFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DashboardTokens.RedHot)
                }
            }
        }
    }
}

@Composable
private fun MoreGroupLabel(title: String) {
    Text(
        title, fontFamily = OutfitFontFamily, fontSize = 10.sp,
        fontWeight = FontWeight.Bold, color = DashboardTokens.White35,
        letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, sub: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(DashboardTokens.CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, DashboardTokens.Rim, RoundedCornerShape(14.dp))
            .clickable {}
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).background(DashboardTokens.CardBg2, RoundedCornerShape(10.dp)).border(1.dp, DashboardTokens.Rim2, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = DashboardTokens.White60, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(sub,   fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.White35)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = DashboardTokens.White35, modifier = Modifier.size(17.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun DashboardBackground() {
    val inf = rememberInfiniteTransition(label = "bg")
    val a1 by inf.animateFloat(0.04f, 0.12f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "a1")
    val a2 by inf.animateFloat(0.03f, 0.09f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "a2")
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.offset((-80).dp, (-160).dp).size(420.dp).graphicsLayer { alpha = a1 }
            .background(Brush.radialGradient(listOf(DashboardTokens.RedHot, Color.Transparent))))
        Box(Modifier.align(Alignment.BottomEnd).offset(80.dp, 80.dp).size(300.dp).graphicsLayer { alpha = a2 }
            .background(Brush.radialGradient(listOf(DashboardTokens.RedDeep, Color.Transparent))))
        Box(Modifier.fillMaxSize().drawBehind {
            val c    = Color(0x05E8001D)
            val cell = 44.dp.toPx()
            var y    = 0f; while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y), 1f); y += cell }
            var x    = 0f; while (x < size.width)  { drawLine(c, Offset(x, 0f), Offset(x, size.height), 1f); x += cell }
        })
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  ★  NOTIFICATION PANEL
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun NotificationPanel(
    notifications: List<NotificationItem>,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Dim scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onDismiss)
        )
        // Panel slides from top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .align(Alignment.TopCenter)
            .background(
                Brush.verticalGradient(listOf(DashboardTokens.CardBg, DashboardTokens.CardBg2)),
                RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(DashboardTokens.Rim2, DashboardTokens.Rim)),
                RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            )
            .clickable(enabled = false) {} // prevent scrim close when tapping panel
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 56.dp) // below the top bar
        ) {
            // Panel header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Notifications, null, tint = DashboardTokens.RedHot, modifier = Modifier.size(18.dp))
                    Text("Notifications", fontFamily = OutfitFontFamily, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Box(
                    modifier = Modifier
                        .background(DashboardTokens.RedDim, RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("Close", fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.RedHot, fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(color = DashboardTokens.Rim, thickness = 0.5.dp)

            if (notifications.isEmpty()) {
                // Sample notifications when no Firebase data
                val samples = listOf(
                    Triple("SOS Alert Nearby",         "A distress signal was reported 2.4 km from you.",   false),
                    Triple("Vehicle Check Complete",   "Your vehicle diagnostics finished successfully.",     true),
                    Triple("Road Closure — NH48",      "Traffic diversion active near Km 18. Plan ahead.",   false),
                    Triple("Service Reminder",         "Your next oil change is due in 300 km.",             true),
                    Triple("Community Alert",          "Police checking reported near Ahmedabad bypass.",    false)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(samples) { (title, body, read) ->
                        NotificationRow(title = title, body = body, read = read)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications) { notif ->
                        NotificationRow(title = notif.title, body = notif.body, read = notif.read)
                    }
                }
            }
        }
        } // end panel Box
    } // end outer Box
} // end NotificationPanel



@Composable
private fun NotificationRow(title: String, body: String, read: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(
                if (!read) DashboardTokens.RedDim else DashboardTokens.CardBg3,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (!read) DashboardTokens.RedMid else DashboardTokens.Rim,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(32.dp)
                    .background(if (!read) DashboardTokens.RedDim else DashboardTokens.CardBg2, CircleShape)
                    .border(1.dp, if (!read) DashboardTokens.RedMid else DashboardTokens.Rim2, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (!read) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                    null,
                    tint = if (!read) DashboardTokens.RedHot else DashboardTokens.White35,
                    modifier = Modifier.size(15.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = OutfitFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(2.dp))
                Text(body, fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.White60, lineHeight = 15.sp)
            }
            if (!read) {
                Box(Modifier.size(7.dp).background(DashboardTokens.RedHot, CircleShape))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  ★  TOP APP BAR  — glassmorphism + scroll-aware collapse
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun DashboardTopBar(
    initials: String, userName: String,
    unreadCount: Int, isScrolled: Boolean,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "topbar")
    val dotAlpha by inf.animateFloat(1f, 0f, infiniteRepeatable(tween(1200), RepeatMode.Restart), "dot")

    // Animate background opacity based on scroll
    val bgAlpha by animateFloatAsState(if (isScrolled) 0.96f else 0.80f, tween(300), label = "bgAlpha")
    val elevation by animateDpAsState(if (isScrolled) 12.dp else 0.dp, tween(300), label = "elev")

    Column(
        modifier = Modifier.fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(0.dp))
            .background(DashboardTokens.BlackCore.copy(alpha = bgAlpha))
            .drawBehind {
                if (isScrolled) drawLine(DashboardTokens.Rim, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp).height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand
            Column(Modifier.weight(1f)) {
                Text(
                    "RescueLink",
                    fontFamily    = BebasNeueFontFamily,
                    fontSize      = 20.sp,
                    letterSpacing = 1.5.sp,
                    style         = TextStyle(brush = Brush.linearGradient(listOf(Color.White, DashboardTokens.RedGlow)))
                )
                AnimatedVisibility(visible = !isScrolled, enter = fadeIn(tween(200)) + expandVertically(), exit = fadeOut(tween(150)) + shrinkVertically()) {
                    Text(
                        "EMERGENCY ROAD ASSIST",
                        fontFamily    = OutfitFontFamily,
                        fontSize      = 7.5.sp,
                        fontWeight    = FontWeight.Medium,
                        color         = DashboardTokens.White35,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            // Live badge
            Row(
                modifier = Modifier
                    .background(DashboardTokens.RedDim, RoundedCornerShape(100.dp))
                    .border(1.dp, DashboardTokens.RedMid, RoundedCornerShape(100.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(5.dp).graphicsLayer { alpha = dotAlpha }.background(DashboardTokens.RedHot, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text("Live", fontFamily = OutfitFontFamily, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = DashboardTokens.RedHot)
            }
            Spacer(Modifier.width(8.dp))

            // Notification bell with badge — tappable
            Box(
                modifier = Modifier.size(36.dp).clickable(onClick = onNotificationClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(DashboardTokens.CardBg2, RoundedCornerShape(10.dp))
                        .border(1.dp, DashboardTokens.Rim2, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Notifications, null, tint = DashboardTokens.White60, modifier = Modifier.size(17.dp)) }
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).offset((-2).dp, 2.dp)
                            .size(14.dp)
                            .background(DashboardTokens.RedHot, CircleShape)
                            .border(1.5.dp, DashboardTokens.BlackCore, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (unreadCount > 9) "9+" else "$unreadCount",
                            fontFamily = OutfitFontFamily, fontSize = 6.sp,
                            fontWeight = FontWeight.Bold, color = Color.White
                        )
                    }
                } else {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset((-3).dp, 3.dp).size(6.dp)
                            .background(DashboardTokens.RedHot, CircleShape)
                            .border(1.dp, DashboardTokens.BlackCore, CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(7.dp))

            // Avatar — navigates to Profile/Settings
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Brush.linearGradient(listOf(DashboardTokens.RedDeep, DashboardTokens.RedHot)),
                        CircleShape
                    )
                    .border(2.dp, DashboardTokens.Rim2, CircleShape)
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials,
                    fontFamily = OutfitFontFamily, fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold, color = Color.White
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  ★  BOTTOM NAV BAR — floating pill design
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun DashboardBottomNav(currentRoute: String, onRoute: (String) -> Unit) {
    val items = listOf(
        Triple("home",      Icons.Rounded.Home,              "Home"),
        Triple("map",       Icons.Rounded.Map,               "Map"),
        Triple("community", Icons.Rounded.People,            "Community"),
        Triple("chat",      Icons.AutoMirrored.Rounded.Chat, "AI Chat"),
        Triple("more",      Icons.Rounded.Menu,              "More")
    )

    Box(
        modifier = Modifier.fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
    ) {
        // Gradient fade at bottom
        Box(
            modifier = Modifier.fillMaxWidth().height(80.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, DashboardTokens.BlackCore.copy(0.96f))))
        )

        // Pill container
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(DashboardTokens.CardBg3, DashboardTokens.CardBg2)),
                    RoundedCornerShape(100.dp)
                )
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(DashboardTokens.Rim2, DashboardTokens.Rim, DashboardTokens.Rim2)),
                    RoundedCornerShape(100.dp)
                )
                .shadow(16.dp, RoundedCornerShape(100.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { (route, icon, label) ->
                    val selected = currentRoute == route
                    val iconColor by animateColorAsState(
                        if (selected) DashboardTokens.RedHot else DashboardTokens.White35,
                        tween(250), label = "iconColor"
                    )
                    val bgAlpha by animateFloatAsState(
                        if (selected) 1f else 0f,
                        tween(250), label = "bgAlpha"
                    )
                    val scale by animateFloatAsState(
                        if (selected) 1f else 0.9f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale"
                    )

                    Box(
                        modifier = Modifier.weight(1f)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (selected) DashboardTokens.RedDim else Color.Transparent)
                            .clickable { onRoute(route) }
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(icon, label, tint = iconColor, modifier = Modifier.size(20.dp))
                            AnimatedVisibility(visible = selected, enter = fadeIn(tween(200)) + expandVertically(), exit = fadeOut(tween(150)) + shrinkVertically()) {
                                Text(label, fontFamily = OutfitFontFamily, fontSize = 8.sp, color = DashboardTokens.RedHot, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  SHARED HELPERS
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun SectionHeader(title: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title,  fontFamily = OutfitFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold,   color = Color.White)
        Text(action, fontFamily = OutfitFontFamily, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = DashboardTokens.RedHot)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.Rounded.History, null, tint = DashboardTokens.White35, modifier = Modifier.size(30.dp))
        Text(title,    fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DashboardTokens.White60)
        Text(subtitle, fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.White35, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LiveBadge() {
    val inf = rememberInfiniteTransition(label = "lbadge")
    val dot by inf.animateFloat(1f, 0.2f, infiniteRepeatable(tween(900), RepeatMode.Reverse), "dot")
    Row(
        modifier = Modifier
            .background(DashboardTokens.RedDim, RoundedCornerShape(100.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(Modifier.size(4.dp).graphicsLayer { alpha = dot }.background(DashboardTokens.RedHot, CircleShape))
        Text("LIVE", fontFamily = OutfitFontFamily, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = DashboardTokens.RedHot, letterSpacing = 0.5.sp)
    }
}
