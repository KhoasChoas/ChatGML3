package com.zhipu.herbreview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zhipu.herbreview.model.PharmacistSession
import com.zhipu.herbreview.ui.theme.HerbReviewTheme

/**
 * 原型登录：后续对接本地 Room / 服务端校验（与 Excel 工号、密码一致）。
 * 「演示：科主任」用于在没有导入数据时预览科主任入口。
 */
@Composable
fun LoginScreen(
    onLoggedIn: (PharmacistSession) -> Unit,
) {
    var employeeId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("中草药智能复核", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Android 客户端 · ChatGLM3 能力由后续后端接入",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = employeeId,
            onValueChange = { employeeId = it },
            label = { Text("工号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val id = employeeId.trim().ifBlank { "demo" }
                onLoggedIn(
                    PharmacistSession(
                        employeeId = id,
                        displayName = "药师（$id）",
                        isDepartmentDirector = false,
                        canSubmitErrorReport = true,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("登录（原型：不校验密码）")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                onLoggedIn(
                    PharmacistSession(
                        employeeId = "director-demo",
                        displayName = "药剂科主任（演示）",
                        isDepartmentDirector = true,
                        canSubmitErrorReport = true,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("演示：科主任账号")
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Login")
@Composable
private fun LoginScreenPreview() {
    HerbReviewTheme {
        LoginScreen(onLoggedIn = {})
    }
}
