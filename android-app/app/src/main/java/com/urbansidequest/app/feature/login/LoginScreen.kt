package com.urbansidequest.app.feature.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbansidequest.app.data.auth.AuthRepository
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal
import com.urbansidequest.app.ui.theme.WarningAmber
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LoginRoute(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit
) {
    val loginViewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(authRepository)
    )
    val uiState by loginViewModel.uiState.collectAsState()

    LaunchedEffect(loginViewModel) {
        loginViewModel.events.collectLatest { event ->
            when (event) {
                LoginEvent.LoginSuccess -> onLoginSuccess()
            }
        }
    }

    LoginScreen(
        uiState = uiState,
        onPhoneChange = loginViewModel::onPhoneChange,
        onCodeChange = loginViewModel::onCodeChange,
        onRequestCode = loginViewModel::requestVerificationCode,
        onLogin = loginViewModel::login,
        onWeChatLogin = loginViewModel::loginWithWeChat
    )
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onRequestCode: () -> Unit,
    onLogin: () -> Unit,
    onWeChatLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        LoginHeader()

        Spacer(modifier = Modifier.weight(1f))

        LoginForm(
            uiState = uiState,
            onPhoneChange = onPhoneChange,
            onCodeChange = onCodeChange,
            onRequestCode = onRequestCode,
            onLogin = onLogin,
            onWeChatLogin = onWeChatLogin
        )

        Spacer(modifier = Modifier.weight(1f))

        LoginFooter()
    }
}

@Composable
private fun LoginHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        RouteMapGraphic(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp)
                .size(182.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "城市副本",
                color = DeepTeal,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "把今天的城市走成一条可执行路线",
                color = AppTextMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.width(210.dp)
            )
        }
    }
}

@Composable
private fun RouteMapGraphic(modifier: Modifier = Modifier) {
    val strokeWidth = with(LocalDensity.current) { 4.dp.toPx() }
    Canvas(modifier = modifier) {
        val dotStep = size.width / 12f
        var x = 0f
        while (x <= size.width) {
            var y = 0f
            while (y <= size.height) {
                drawCircle(
                    color = AppBorder.copy(alpha = 0.35f),
                    radius = 1.2f,
                    center = Offset(x, y)
                )
                y += dotStep
            }
            x += dotStep
        }

        val path = Path().apply {
            moveTo(size.width * 0.32f, size.height * 0.82f)
            quadraticTo(
                size.width * 0.45f,
                size.height * 0.60f,
                size.width * 0.60f,
                size.height * 0.66f
            )
            quadraticTo(
                size.width * 0.82f,
                size.height * 0.76f,
                size.width * 0.78f,
                size.height * 0.40f
            )
            quadraticTo(
                size.width * 0.80f,
                size.height * 0.22f,
                size.width * 0.92f,
                size.height * 0.10f
            )
        }
        drawPath(
            path = path,
            color = DeepTeal,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        drawRouteNode(Offset(size.width * 0.32f, size.height * 0.82f), WarningAmber)
        drawRouteNode(Offset(size.width * 0.60f, size.height * 0.66f), AppSurface)
        drawRouteNode(Offset(size.width * 0.78f, size.height * 0.40f), AppSurface)
        drawRouteNode(Offset(size.width * 0.92f, size.height * 0.10f), DeepTeal)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRouteNode(
    center: Offset,
    fillColor: androidx.compose.ui.graphics.Color
) {
    drawCircle(color = AppSurface, radius = 12.dp.toPx(), center = center)
    drawCircle(color = fillColor, radius = 7.dp.toPx(), center = center)
    drawCircle(
        color = DeepTeal,
        radius = 7.dp.toPx(),
        center = center,
        style = Stroke(width = 3.dp.toPx())
    )
}

@Composable
private fun LoginForm(
    uiState: LoginUiState,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onRequestCode: () -> Unit,
    onLogin: () -> Unit,
    onWeChatLogin: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        LoginFieldLabel(text = "手机号")
        OutlinedTextField(
            value = uiState.phone,
            onValueChange = onPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            placeholder = { Text(text = "输入手机号") },
            leadingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "+86", color = AppTextMuted, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(AppBorder)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = loginTextFieldColors()
        )

        LoginFieldLabel(text = "验证码")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.code,
                onValueChange = onCodeChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                placeholder = { Text(text = "输入验证码") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = loginTextFieldColors()
            )
            OutlinedButton(
                onClick = onRequestCode,
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Text(text = "获取验证码", color = DeepTeal, fontWeight = FontWeight.SemiBold)
            }
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onLogin,
            enabled = uiState.canLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DeepTeal,
                contentColor = AppSurface
            )
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AppSurface
                )
            } else {
                Text(text = "登录 / 注册", fontWeight = FontWeight.SemiBold)
            }
        }

        OutlinedButton(
            onClick = onWeChatLogin,
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(DeepTeal)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "微信登录", color = DeepTeal, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoginFieldLabel(text: String) {
    Text(
        text = text,
        color = AppTextMuted,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun loginTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DeepTeal,
    unfocusedBorderColor = AppBorder,
    focusedContainerColor = AppSurface,
    unfocusedContainerColor = AppSurface,
    cursorColor = DeepTeal,
    focusedTextColor = AppText,
    unfocusedTextColor = AppText,
    focusedPlaceholderColor = AppTextMuted,
    unfocusedPlaceholderColor = AppTextMuted
)

@Composable
private fun LoginFooter() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppSurfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✓", color = DeepTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "保存路线、打卡记录和你的私人城市地图",
                color = AppTextMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        TextButton(onClick = { }) {
            Text(
                text = "登录即表示同意《用户协议》和《隐私政策》",
                color = AppTextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
