package com.nobody.repoi.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobody.repoi.ui.common.plainText
import com.nobody.repoi.ui.theme.FormFactorPreviews
import com.nobody.repoi.ui.theme.PoiShelfTheme
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onLoginSuccess()
        }
    }

    LoginContent(
        uiState = uiState,
        onAccountChange = viewModel::onAccountChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
        onModeChange = viewModel::onModeChange,
        onTokenChange = viewModel::onTokenChange,
        onToggleTokenVisibility = viewModel::toggleTokenVisibility,
        onClearToken = viewModel::clearTokenInput,
        onPasteToken = {
            scope.launch {
                val clip = runCatching { clipboard.getClipEntry() }.getOrNull()
                val text = clip?.plainText().orEmpty()
                if (text.isNotBlank()) viewModel.onTokenChange(text)
            }
        },
        onLoginClick = { viewModel.login { onLoginSuccess() } },
        onTokenLoginClick = { viewModel.loginWithToken { onLoginSuccess() } },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginContent(
    uiState: LoginUiState,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onModeChange: (LoginMode) -> Unit,
    onTokenChange: (String) -> Unit,
    onToggleTokenVisibility: () -> Unit,
    onClearToken: () -> Unit,
    onPasteToken: () -> Unit,
    onLoginClick: () -> Unit,
    onTokenLoginClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "登录 Hpoi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // 说明：此处 BoxWithConstraints 只在顶层执行一次约束读取（大屏 440dp 居中），
        // 不在滚动列表内重复测量，无子组合爆炸风险，故保留；改用窗口断点反而丢失表单容器实测宽度。
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val formModifier = if (maxWidth > 440.dp) Modifier.width(440.dp) else Modifier.fillMaxWidth()
            Column(
                modifier = formModifier
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // 品牌视觉区：56dp Flower 异形容器 + 标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialShapes.Flower.toShape()
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "登录 Hpoi",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 双模连结切换：账号密码 / Token 登录
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modes = listOf(LoginMode.ACCOUNT to "账号密码", LoginMode.TOKEN to "Token 登录")
                    modes.forEachIndexed { index, (mode, label) ->
                        val checked = uiState.loginMode == mode
                        ToggleButton(
                            checked = checked,
                            onCheckedChange = { if (!checked) onModeChange(mode) },
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    role = Role.RadioButton
                                    selected = checked
                                },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            colors = ToggleButtonDefaults.toggleButtonColors()
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }

                Crossfade(
                    targetState = uiState.loginMode,
                    label = "LoginModeCrossfade",
                    modifier = Modifier.fillMaxWidth()
                ) { mode ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (mode == LoginMode.ACCOUNT) {
                            AccountLoginForm(
                                uiState = uiState,
                                onAccountChange = onAccountChange,
                                onPasswordChange = onPasswordChange,
                                onTogglePasswordVisibility = onTogglePasswordVisibility,
                                onLoginClick = {
                                    focusManager.clearFocus()
                                    onLoginClick()
                                }
                            )
                        } else {
                            TokenLoginForm(
                                uiState = uiState,
                                onTokenChange = onTokenChange,
                                onToggleTokenVisibility = onToggleTokenVisibility,
                                onClearToken = onClearToken,
                                onPasteToken = onPasteToken,
                                onTokenLoginClick = {
                                    focusManager.clearFocus()
                                    onTokenLoginClick()
                                }
                            )
                        }
                    }
                }

                // 错误提示条
                AnimatedVisibility(
                    visible = uiState.errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    uiState.errorMessage?.let { errorMsg ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountLoginForm(
    uiState: LoginUiState,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = uiState.account,
            onValueChange = onAccountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("账号 / 邮箱") },
            placeholder = { Text("请输入 Hpoi 账号或绑定邮箱") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            placeholder = { Text("请输入账号密码") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        imageVector = if (uiState.passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (uiState.passwordVisible) "隐藏密码" else "显示密码"
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onLoginClick()
                }
            )
        )

        val canSubmit = uiState.account.isNotBlank() && uiState.password.isNotBlank() && !uiState.isLoading

        FilledTonalButton(
            onClick = onLoginClick,
            enabled = canSubmit,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = "登录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TokenLoginForm(
    uiState: LoginUiState,
    onTokenChange: (String) -> Unit,
    onToggleTokenVisibility: () -> Unit,
    onClearToken: () -> Unit,
    onPasteToken: () -> Unit,
    onTokenLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = uiState.token,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("登录令牌") },
            placeholder = { Text("粘贴从其他设备导出的 utoken") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.token.isNotEmpty()) {
                        IconButton(onClick = onClearToken) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "清空令牌"
                            )
                        }
                    }
                    IconButton(onClick = onPasteToken) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = "从剪贴板粘贴"
                        )
                    }
                    IconButton(onClick = onToggleTokenVisibility) {
                        Icon(
                            imageVector = if (uiState.tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (uiState.tokenVisible) "隐藏令牌" else "显示令牌"
                        )
                    }
                }
            },
            singleLine = true,
            visualTransformation = if (uiState.tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            supportingText = { Text("已输入 ${uiState.token.length} 字符") },
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onTokenLoginClick()
                }
            )
        )

        val canSubmit = uiState.token.isNotBlank() && !uiState.isLoading

        FilledTonalButton(
            onClick = onTokenLoginClick,
            enabled = canSubmit,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = "验证并登录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@FormFactorPreviews
@Composable
private fun LoginContentPreview() {
    PoiShelfTheme {
        LoginContent(
            uiState = LoginUiState(account = "hpoi_user@example.com"),
            onAccountChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onModeChange = {},
            onTokenChange = {},
            onToggleTokenVisibility = {},
            onClearToken = {},
            onPasteToken = {},
            onLoginClick = {},
            onTokenLoginClick = {},
            onBack = {}
        )
    }
}

@FormFactorPreviews
@Composable
private fun TokenLoginContentPreview() {
    PoiShelfTheme {
        LoginContent(
            uiState = LoginUiState(loginMode = LoginMode.TOKEN, token = "candidate-token"),
            onAccountChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onModeChange = {},
            onTokenChange = {},
            onToggleTokenVisibility = {},
            onClearToken = {},
            onPasteToken = {},
            onLoginClick = {},
            onTokenLoginClick = {},
            onBack = {}
        )
    }
}
