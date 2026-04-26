# 中草药智能复核 · Android 客户端

技术选型：**Kotlin**、**Jetpack Compose**、**Material 3**。与 `herb_review_system/database/schema.sql` 及后续后端 API 对接。

## 环境要求

- [Android Studio](https://developer.android.com/studio) Hedgehog (2023.1.1) 或更高（自带 JDK 17）
- Android SDK：compileSdk 34，minSdk 26

## 打开工程

1. Android Studio → **Open**，选择本目录 `herb_review_android`。
2. 首次同步后，若缺少 `local.properties`，IDE 会提示配置 SDK；或手动创建：

   ```properties
   sdk.dir=C\:\\Users\\<你>\\AppData\\Local\\Android\\Sdk
   ```

3. 运行 **app** → 设备或模拟器。

### 连接本机 FastAPI（可选）

在 `local.properties` 中增加 `herbApi.*` 项（示例见仓库内 `local.properties.example`）。配置 `herbApi.baseUrl` 后，**复核**页会拉取处方并创建会话，**科主任 · 工作分析**会拉取 `director_work_overview` 与时间线；未配置时仍使用内置 Kotlin 演示数据。模拟器访问宿主机请用 `http://10.0.2.2:8000`。配置 API 后，**复核**与**科主任 · 工作分析**页顶部会出现「自检」卡片：对勾为成功、红色为失败说明，便于确认接口是否接通。

若 `POST /auth/login` 返回 401，可临时配置 `herbApi.devPharmacistId=<药师ID>`，客户端会自动走 `X-Dev-Pharmacist-Id` 头（要求后端 `DEV_ALLOW_HEADER_AUTH=1`，仅本地开发使用）。

## 命令行构建（可选）

已包含 `gradlew.bat` 与 `gradle-wrapper.jar`。需 **JDK 17**（或 11+）作为 `JAVA_HOME`，并配置好 `local.properties` 中的 `sdk.dir`：

```bat
gradlew.bat assembleDebug
```

若报错提示 Android Gradle Plugin 与 Java 版本不兼容，请将 `JAVA_HOME` 指向 Android Studio 自带的 JBR（例如 `…\Android Studio\jbr`）。

## 当前原型范围

- 登录页（原型：不校验密码；提供「演示：科主任账号」）
- 底部导航：工作台、复核、报错台、我的
- 科主任在「我的」中进入「工作分析」占位页

后续可接入：Room（复制/同步 SQLite）、相机与相册、Retrofit + ChatGLM3 流式接口等。
