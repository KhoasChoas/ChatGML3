package com.zhipu.herbreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.zhipu.herbreview.model.PharmacistSession
import com.zhipu.herbreview.ui.LoginScreen
import com.zhipu.herbreview.ui.MainShell

@Composable
fun HerbReviewAppRoot(
    appearanceMode: Int = APPEARANCE_FOLLOW_SYSTEM,
    onCycleAppearanceMode: () -> Unit = {},
) {
    var employeeId by rememberSaveable { mutableStateOf<String?>(null) }
    var displayName by rememberSaveable { mutableStateOf<String?>(null) }
    var isDepartmentDirector by rememberSaveable { mutableStateOf(false) }
    var canSubmitErrorReport by rememberSaveable { mutableStateOf(true) }

    val session = if (employeeId != null && displayName != null) {
        PharmacistSession(
            employeeId = employeeId!!,
            displayName = displayName!!,
            isDepartmentDirector = isDepartmentDirector,
            canSubmitErrorReport = canSubmitErrorReport,
        )
    } else {
        null
    }

    if (session == null) {
        LoginScreen(
            onLoggedIn = { s ->
                employeeId = s.employeeId
                displayName = s.displayName
                isDepartmentDirector = s.isDepartmentDirector
                canSubmitErrorReport = s.canSubmitErrorReport
            },
        )
    } else {
        MainShell(
            session = session,
            onLogout = {
                employeeId = null
                displayName = null
                isDepartmentDirector = false
                canSubmitErrorReport = true
            },
            appearanceMode = appearanceMode,
            onCycleAppearanceMode = onCycleAppearanceMode,
        )
    }
}
