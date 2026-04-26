package com.zhipu.herbreview.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhipu.herbreview.APPEARANCE_DARK
import com.zhipu.herbreview.APPEARANCE_FOLLOW_SYSTEM
import com.zhipu.herbreview.APPEARANCE_LIGHT
import com.zhipu.herbreview.model.PharmacistSession
import com.zhipu.herbreview.ui.theme.HerbReviewTheme

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    session: PharmacistSession,
    onLogout: () -> Unit,
    appearanceMode: Int = APPEARANCE_FOLLOW_SYSTEM,
    onCycleAppearanceMode: () -> Unit = {},
) {
    var showDirector by rememberSaveable { mutableStateOf(false) }
    var showReviewHistory by rememberSaveable { mutableStateOf(false) }

    if (showReviewHistory) {
        ReviewHistoryScreen(
            modifier = modifier,
            session = session,
            onBack = { showReviewHistory = false },
        )
        return
    }

    if (showDirector && session.isDepartmentDirector) {
        DirectorAnalyticsScreen(
            modifier = modifier,
            onBack = { showDirector = false },
        )
        return
    }

    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("我的", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "个人信息与账户安全",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        ProfileHeroCard(session = session)

        Spacer(Modifier.height(14.dp))

        Text(
            text = "快捷入口",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ProfileNavRow(
            title = "复核历史记录",
            subtitle = "本人已归档会话 · 同步服务器与本机摘要",
            icon = { Icon(Icons.Outlined.History, contentDescription = null) },
            onClick = { showReviewHistory = true },
        )
        Spacer(Modifier.height(8.dp))
        if (session.isDepartmentDirector) {
            FilledTonalButton(
                onClick = { showDirector = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Analytics, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("工作分析（科主任）")
            }
            Spacer(Modifier.height(14.dp))
        }

        Text(
            text = "通用",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ProfileAppearanceRow(
            appearanceMode = appearanceMode,
            onCycle = onCycleAppearanceMode,
        )
        Spacer(Modifier.height(8.dp))
        ProfileNavRow(
            title = "消息与提醒",
            subtitle = "后台约每 15 分钟轮询本人待继续会话；数量增加时系统通知",
            icon = { Icon(Icons.Outlined.NotificationsNone, contentDescription = null) },
            onClick = {},
        )
        Spacer(Modifier.height(8.dp))
        ProfileNavRow(
            title = "关于与合规声明",
            subtitle = "辅助决策说明、版本信息（占位）",
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            onClick = {},
        )

        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Outlined.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("退出登录")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProfileHeroCard(session: PharmacistSession) {
    Card(
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    )
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ProfileAvatar(name = session.displayName)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "工号 ${session.employeeId}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (session.isDepartmentDirector) "药剂科主任" else "药师") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(if (session.canSubmitErrorReport) "可提交报错" else "无报错权限") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                ProfileInfoRow(label = "登录身份", value = if (session.isDepartmentDirector) "科主任" else "普通药师")
                ProfileInfoRow(
                    label = "识别报错",
                    value = if (session.canSubmitErrorReport) "已开通" else "未开通",
                )
            }
        }
    }
}

@Composable
private fun ProfileAvatar(name: String) {
    val initials = profileInitials(name)
    Surface(
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ProfileAppearanceRow(
    appearanceMode: Int,
    onCycle: () -> Unit,
) {
    val (modeLabel, iconVector) = when (appearanceMode) {
        APPEARANCE_LIGHT -> "浅色" to Icons.Outlined.LightMode
        APPEARANCE_DARK -> "深色" to Icons.Outlined.DarkMode
        else -> "跟随系统" to Icons.Outlined.BrightnessAuto
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(iconVector, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("外观与主题", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "当前：$modeLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onCycle,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("切换")
            }
        }
    }
}

@Composable
private fun ProfileNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun profileInitials(displayName: String): String {
    val s = displayName.trim().ifBlank { "?" }
    return when {
        s.length >= 2 -> s.take(2)
        else -> s.take(1)
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Profile - Pharmacist")
@Composable
private fun ProfileScreenPreviewPharmacist() {
    HerbReviewTheme {
        ProfileScreen(
            session = PharmacistSession(
                employeeId = "3070",
                displayName = "李华",
                isDepartmentDirector = false,
                canSubmitErrorReport = true,
            ),
            onLogout = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Profile - Director")
@Composable
private fun ProfileScreenPreviewDirector() {
    HerbReviewTheme {
        ProfileScreen(
            session = PharmacistSession(
                employeeId = "3109",
                displayName = "张淑华",
                isDepartmentDirector = true,
                canSubmitErrorReport = true,
            ),
            onLogout = {},
        )
    }
}
