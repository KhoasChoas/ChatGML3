package com.zhipu.herbreview

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zhipu.herbreview.ui.theme.HerbReviewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001,
            )
        }
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            var appearanceMode by rememberSaveable { mutableIntStateOf(APPEARANCE_FOLLOW_SYSTEM) }
            val useDarkTheme = when (appearanceMode) {
                APPEARANCE_LIGHT -> false
                APPEARANCE_DARK -> true
                else -> systemDark
            }
            HerbReviewTheme(darkTheme = useDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HerbReviewAppRoot(
                        appearanceMode = appearanceMode,
                        onCycleAppearanceMode = {
                            appearanceMode = (appearanceMode + 1) % 3
                        },
                    )
                }
            }
        }
    }
}
