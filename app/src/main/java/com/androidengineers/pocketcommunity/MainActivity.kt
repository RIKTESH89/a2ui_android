package com.androidengineers.pocketcommunity

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidengineers.pocketcommunity.data.HttpCommunityRepository
import com.androidengineers.pocketcommunity.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val agentPreferences = getSharedPreferences("agent", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        enableEdgeToEdge(
            statusBarStyle =
                androidx.activity.SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
            navigationBarStyle =
                androidx.activity.SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
        )
        setContent {
            MaterialTheme(
                colorScheme =
                    lightColorScheme(
                        primary = Color(0xFFBD4C2F),
                        onPrimary = Color.White,
                        primaryContainer = Color(0xFFFFDBCD),
                        onPrimaryContainer = Color(0xFF421208),
                        background = Color(0xFFF8F2E9),
                        surface = Color.White,
                        onSurface = Color(0xFF2B211C),
                        surfaceVariant = Color(0xFFF0E4D8),
                        onSurfaceVariant = Color(0xFF6C5B51),
                        secondary = Color(0xFF638A68),
                    ),
                shapes =
                    Shapes(
                        small = RoundedCornerShape(12.dp),
                        medium = RoundedCornerShape(20.dp),
                        large = RoundedCornerShape(28.dp),
                    ),
            ) {
                val catalogs =
                    remember {
                        uiAgentCatalogs { reference ->
                            resolveUiAgentRemoteImage(
                                agentPreferences.getString("endpoint", "").orEmpty(),
                                reference,
                            )
                        }
                    }
                val vm: CommunityViewModel =
                    viewModel(
                        factory =
                            object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                    CommunityViewModel(
                                        HttpCommunityRepository(),
                                        catalogs,
                                        agentPreferences.getString("endpoint", "").orEmpty(),
                                        { endpoint ->
                                            agentPreferences.edit()
                                                .putString("endpoint", endpoint)
                                                .apply()
                                        },
                                    )
                                        as T
                            }
                    )
                CommunityScreen(vm)
            }
        }
    }
}
