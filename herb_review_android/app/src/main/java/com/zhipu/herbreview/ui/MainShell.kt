package com.zhipu.herbreview.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import com.zhipu.herbreview.APPEARANCE_FOLLOW_SYSTEM
import com.zhipu.herbreview.model.PharmacistSession
import com.zhipu.herbreview.ui.screens.ErrorInboxScreen
import com.zhipu.herbreview.ui.screens.ProfileScreen
import com.zhipu.herbreview.ui.screens.ReviewHomeScreen
import com.zhipu.herbreview.ui.screens.WorkbenchScreen
import com.zhipu.herbreview.ui.theme.HerbReviewTheme
import com.zhipu.herbreview.ui.theme.WoodNavBackground

private enum class MainTab(val label: String) {
    Workbench("工作台"),
    Review("复核"),
    Errors("报错台"),
    Profile("我的"),
}

@Composable
fun MainShell(
    session: PharmacistSession,
    onLogout: () -> Unit,
    appearanceMode: Int = APPEARANCE_FOLLOW_SYSTEM,
    onCycleAppearanceMode: () -> Unit = {},
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var resumeSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    /** >0: Review tab should load this prescription (from workbench search). */
    var pendingStartPrescriptionId by rememberSaveable { mutableIntStateOf(-1) }
    val tabs = MainTab.entries

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = WoodNavBackground) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondary,
                            selectedTextColor = MaterialTheme.colorScheme.onSecondary,
                            indicatorColor = MaterialTheme.colorScheme.secondary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    MainTab.Workbench -> Icons.Default.Home
                                    MainTab.Review -> Icons.Default.Assignment
                                    MainTab.Errors -> Icons.Default.ReportProblem
                                    MainTab.Profile -> Icons.Default.Person
                                },
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (tabs[tabIndex]) {
            MainTab.Workbench -> WorkbenchScreen(
                modifier = modifier,
                session = session,
                onStartTodayReview = { prescriptionId ->
                    pendingStartPrescriptionId = prescriptionId
                    tabIndex = MainTab.Review.ordinal
                },
                onOpenHistorySession = { sid ->
                    resumeSessionId = sid
                    tabIndex = MainTab.Review.ordinal
                },
                onStartReviewForPrescription = { prescriptionId ->
                    pendingStartPrescriptionId = prescriptionId
                    tabIndex = MainTab.Review.ordinal
                },
            )
            MainTab.Review -> ReviewHomeScreen(
                modifier = modifier,
                session = session,
                resumeSessionId = resumeSessionId,
                onResumeSessionConsumed = { resumeSessionId = null },
                pendingStartPrescriptionId = pendingStartPrescriptionId,
                onPendingStartPrescriptionConsumed = { pendingStartPrescriptionId = -1 },
                onExitReview = { tabIndex = MainTab.Workbench.ordinal },
            )
            MainTab.Errors -> ErrorInboxScreen(modifier, session)
            MainTab.Profile -> ProfileScreen(
                modifier = modifier,
                session = session,
                onLogout = onLogout,
                appearanceMode = appearanceMode,
                onCycleAppearanceMode = onCycleAppearanceMode,
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Main Shell")
@Composable
private fun MainShellPreview() {
    HerbReviewTheme {
        MainShell(
            session = PharmacistSession(
                employeeId = "3109",
                displayName = "药师（3109）",
                isDepartmentDirector = true,
                canSubmitErrorReport = true,
            ),
            onLogout = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Main Shell Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MainShellDarkPreview() {
    HerbReviewTheme(darkTheme = true) {
        MainShell(
            session = PharmacistSession(
                employeeId = "3109",
                displayName = "药师（3109）",
                isDepartmentDirector = true,
                canSubmitErrorReport = true,
            ),
            onLogout = {},
        )
    }
}
