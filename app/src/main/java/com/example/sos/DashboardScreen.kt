package com.example.sos

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.automirrored.rounded.ContactSupport
import androidx.compose.material.icons.automirrored.rounded.Logout
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
import com.example.sos.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val comments: Int = 0
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
    private var communityListener: ValueEventListener? = null
    private var vehicleListener: ValueEventListener? = null
    private var notifListener: ValueEventListener? = null

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
            _state.value = _state.value.copy(isLoadingIncidents = false)
            return
        }
        val ref = db?.reference?.child("incidents")?.child(uid) ?: run {
            _state.value = _state.value.copy(isLoadingIncidents = false)
            return
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
                _state.value = _state.value.copy(incidents = list, isLoadingIncidents = false)
            }
            override fun onCancelled(error: DatabaseError) {
                _state.value = _state.value.copy(isLoadingIncidents = false)
            }
        }
        ref.addValueEventListener(incidentListener!!)
    }

    // ── Community ─────────────────────────────────────────────────────────
    private fun observeCommunityPosts() {
        val ref = db?.reference?.child("community") ?: run {
            _state.value = _state.value.copy(
                isLoadingCommunity = false,
                communityPosts = sampleCommunityPosts()
            )
            return
        }
        communityListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val list = snap.children.mapNotNull { child ->
                    try {
                        CommunityPost(
                            id       = child.key ?: "",
                            author   = child.child("author").getValue(String::class.java) ?: "User",
                            initials = child.child("initials").getValue(String::class.java) ?: "U",
                            timeAgo  = child.child("timeAgo").getValue(String::class.java) ?: "",
                            content  = child.child("content").getValue(String::class.java) ?: "",
                            likes    = child.child("likes").getValue(Long::class.java)?.toInt() ?: 0,
                            comments = child.child("comments").getValue(Long::class.java)?.toInt() ?: 0
                        )
                    } catch (e: Exception) { null }
                }
                val finalList = if (list.isEmpty()) sampleCommunityPosts() else list
                _state.value = _state.value.copy(communityPosts = finalList, isLoadingCommunity = false)
            }
            override fun onCancelled(error: DatabaseError) {
                _state.value = _state.value.copy(
                    communityPosts = sampleCommunityPosts(), isLoadingCommunity = false
                )
            }
        }
        ref.limitToLast(20).addValueEventListener(communityListener!!)
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

    override fun onCleared() {
        val uid = auth.currentUser?.uid
        incidentListener?.let {
            uid?.let { id -> db?.reference?.child("incidents")?.child(id)?.removeEventListener(it) }
        }
        communityListener?.let { db?.reference?.child("community")?.removeEventListener(it) }
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
    vm: DashboardViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var currentRoute by remember { mutableStateOf("home") }
    val listState = rememberLazyListState()

    // Detect scroll for top bar collapse
    val isScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20 } }

    Box(modifier = Modifier.fillMaxSize().background(DashboardTokens.BlackCore)) {
        // Ambient background glows
        DashboardBackground()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                DashboardTopBar(
                    initials    = state.userInitials,
                    userName    = state.userName,
                    unreadCount = state.unreadCount,
                    isScrolled  = isScrolled,
                    onLogout    = onLogout
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
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) { route ->
                when (route) {
                    "home"      -> HomeScreen(state, listState, vm::toggleSos)
                    "map"       -> MapScreen()
                    "community" -> CommunityScreen(state)
                    "chat"      -> AiChatScreen()
                    "more"      -> MoreScreen(onLogout)
                    else        -> HomeScreen(state, listState, vm::toggleSos)
                }
            }
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
    onSosTap: () -> Unit
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
        item { ChatbotPreviewSection() }
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
@Composable
private fun QuickActionsRow() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quick Actions", fontFamily = OutfitFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionCard("🔧", "Mechanic", DashboardTokens.Orange, DashboardTokens.OrangeDim)
            QuickActionCard("⛽", "Fuel",     DashboardTokens.Green,  DashboardTokens.GreenDim)
            QuickActionCard("🚑", "Hospital", DashboardTokens.RedHot, DashboardTokens.RedDim)
            QuickActionCard("👮", "Police",   DashboardTokens.Blue,   DashboardTokens.BlueDim)
            QuickActionCard("🔋", "Charge",   DashboardTokens.Purple, DashboardTokens.PurpleDim)
        }
    }
}

@Composable
private fun QuickActionCard(emoji: String, label: String, color: Color, dimColor: Color) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .background(DashboardTokens.CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(0.2f), RoundedCornerShape(14.dp))
            .clickable {}
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(dimColor, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 16.sp) }
        Text(label, fontFamily = OutfitFontFamily, fontSize = 9.sp, color = DashboardTokens.White60, textAlign = TextAlign.Center)
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
private fun ChatbotPreviewSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("AI Emergency Assistant", "Open Chat")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DashboardTokens.CardBg, RoundedCornerShape(16.dp))
                .border(1.dp, DashboardTokens.Rim, RoundedCornerShape(16.dp))
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
                    QuickChip("🔧 Mechanic"); QuickChip("⛽ Fuel")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickChip("🚑 Hospital"); QuickChip("👮 Police")
                }
            }
        }
    }
}

@Composable
private fun QuickChip(text: String) {
    Box(
        modifier = Modifier
            .background(DashboardTokens.CardBg3, RoundedCornerShape(10.dp))
            .border(1.dp, DashboardTokens.Rim2, RoundedCornerShape(10.dp))
            .clickable {}
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) { Text(text, fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.White60) }
}

// ═══════════════════════════════════════════════════════════════════════════
//  MAP SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun MapScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text("Map & Navigation", fontFamily = OutfitFontFamily, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Locate nearby assistance", fontFamily = OutfitFontFamily, fontSize = 11.sp, color = DashboardTokens.White60)
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).border(1.dp, DashboardTokens.Rim, RoundedCornerShape(16.dp))) {
            Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF0C0C10))) {
                val step = 36.dp.toPx()
                for (i in 0..30) {
                    drawLine(Color(0x0AFFFFFF), Offset(i * step, 0f), Offset(i * step, size.height))
                    drawLine(Color(0x0AFFFFFF), Offset(0f, i * step), Offset(size.width, i * step))
                }
                drawRect(Color(0xFF151520), Offset(0f, size.height * 0.42f), Size(size.width, 22.dp.toPx()))
                drawRect(Color(0xFF151520), Offset(size.width * 0.52f, 0f), Size(16.dp.toPx(), size.height))
                drawCircle(DashboardTokens.RedHot, 8.dp.toPx(), Offset(size.width * 0.42f, size.height * 0.44f))
                drawCircle(DashboardTokens.Blue,   5.dp.toPx(), Offset(size.width * 0.72f, size.height * 0.24f))
                drawCircle(DashboardTokens.Green,  5.dp.toPx(), Offset(size.width * 0.22f, size.height * 0.62f))
                drawCircle(DashboardTokens.Orange, 5.dp.toPx(), Offset(size.width * 0.82f, size.height * 0.72f))
            }
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f))))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, null, tint = DashboardTokens.RedHot, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Near Ahmedabad Hwy, Gujarat", fontFamily = OutfitFontFamily, fontSize = 11.sp, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text("Expand", fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.RedHot, fontWeight = FontWeight.Bold)
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
//  COMMUNITY SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun CommunityScreen(state: DashboardState) {
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
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("+ Post", fontFamily = OutfitFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        if (state.isLoadingCommunity) {
            item {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DashboardTokens.RedHot, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }
        } else {
            items(state.communityPosts) { post -> CommunityPostCard(post) }
        }
    }
}

@Composable
private fun CommunityPostCard(post: CommunityPost) {
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
                LiveBadge()
            }
            Text(post.content, fontFamily = OutfitFontFamily, fontSize = 12.sp, color = DashboardTokens.WhitePure, lineHeight = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ThumbUp, null, tint = DashboardTokens.RedHot, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("${post.likes}",    fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.White60)
                Spacer(Modifier.width(14.dp))
                Icon(Icons.AutoMirrored.Rounded.Comment, null, tint = DashboardTokens.White35, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("${post.comments}", fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.White60)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  AI CHAT SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun AiChatScreen(chatVm: ChatViewModel = viewModel()) {
    val messages = chatVm.chatMessages
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xD9060608))
                .drawBehind { drawLine(DashboardTokens.Rim, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(DashboardTokens.RedDim, CircleShape).border(1.dp, DashboardTokens.RedMid, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = DashboardTokens.RedHot, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("AI Emergency Assistant", fontFamily = OutfitFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Online · Responds instantly", fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.Green)
            }
            Spacer(Modifier.weight(1f))
            Text("History", fontFamily = OutfitFontFamily, fontSize = 10.sp, color = DashboardTokens.RedHot, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items(messages.reversed()) { msg ->
                val isAi = !msg.isUser
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End) {
                    if (isAi) {
                        Box(modifier = Modifier.size(26.dp).background(DashboardTokens.RedDim, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = DashboardTokens.RedHot, modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(7.dp))
                    }
                    Box(
                        modifier = Modifier.widthIn(max = 240.dp)
                            .background(
                                if (isAi) DashboardTokens.CardBg3 else DashboardTokens.RedHot,
                                RoundedCornerShape(topStart = if (isAi) 2.dp else 12.dp, topEnd = if (isAi) 12.dp else 2.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
                            )
                            .border(1.dp, if (isAi) DashboardTokens.Rim2 else Color.Transparent,
                                RoundedCornerShape(topStart = if (isAi) 2.dp else 12.dp, topEnd = if (isAi) 12.dp else 2.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                            .padding(10.dp)
                    ) { Text(msg.text, fontFamily = OutfitFontFamily, fontSize = 12.sp, color = Color.White, lineHeight = 18.sp) }
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(DashboardTokens.CardBg)
                .drawBehind { drawLine(DashboardTokens.Rim, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Ask anything...", fontSize = 12.sp, color = DashboardTokens.White35) },
                modifier = Modifier.weight(1f).height(46.dp),
                textStyle = TextStyle(fontFamily = OutfitFontFamily, fontSize = 12.sp, color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor   = DashboardTokens.CardBg3,
                    unfocusedContainerColor = DashboardTokens.CardBg3,
                    focusedBorderColor      = DashboardTokens.RedHot,
                    unfocusedBorderColor    = DashboardTokens.Rim2
                ),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(46.dp)
                    .background(Brush.linearGradient(listOf(DashboardTokens.RedHot, DashboardTokens.RedDeep)), RoundedCornerShape(12.dp))
                    .clickable { if (input.isNotBlank()) { chatVm.sendMessage(input); input = "" } },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
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
//  ★  TOP APP BAR  — glassmorphism + scroll-aware collapse
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun DashboardTopBar(
    initials: String, userName: String,
    unreadCount: Int, isScrolled: Boolean,
    onLogout: () -> Unit
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

            // Notification bell with badge
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
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

            // Avatar with gradient ring
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Brush.linearGradient(listOf(DashboardTokens.RedDeep, DashboardTokens.RedHot)),
                        CircleShape
                    )
                    .border(2.dp, DashboardTokens.Rim2, CircleShape)
                    .clickable(onClick = onLogout),
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
