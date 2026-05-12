package com.moonliter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moonliter.app.data.PostcardApiClient
import com.moonliter.app.data.TodayPostcardResponse
import com.moonliter.app.ui.theme.LampOrange
import com.moonliter.app.ui.theme.MidnightNavy
import com.moonliter.app.ui.theme.RainBlue
import com.moonliter.app.ui.theme.SoftBrown
import com.moonliter.app.ui.theme.WarmCream
import kotlinx.coroutines.launch

private enum class MoonLiterScreen {
    Home,
    WritePostcard,
    TodayPostcard
}

private sealed interface RequestState {
    data object Idle : RequestState
    data object Loading : RequestState
    data class Success(val message: String) : RequestState
    data class Error(val message: String) : RequestState
}

@Composable
fun MoonLiterApp() {
    val apiClient = remember { PostcardApiClient() }
    var screen by remember { mutableStateOf(MoonLiterScreen.Home) }
    var userId by remember { mutableStateOf("1") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MidnightNavy
    ) {
        when (screen) {
            MoonLiterScreen.Home -> HomeScreen(
                userId = userId,
                onUserIdChange = { userId = it },
                onWriteClick = { screen = MoonLiterScreen.WritePostcard },
                onTodayClick = { screen = MoonLiterScreen.TodayPostcard }
            )

            MoonLiterScreen.WritePostcard -> WritePostcardScreen(
                userId = userId.toLongOrNull(),
                apiClient = apiClient,
                onBackClick = { screen = MoonLiterScreen.Home }
            )

            MoonLiterScreen.TodayPostcard -> TodayPostcardScreen(
                userId = userId.toLongOrNull(),
                apiClient = apiClient,
                onBackClick = { screen = MoonLiterScreen.Home }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    userId: String,
    onUserIdChange: (String) -> Unit,
    onWriteClick: () -> Unit,
    onTodayClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MidnightNavy, Color(0xFF2F3C4B), RainBlue)
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "문라이터",
                color = WarmCream,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "오늘의 마음을 누군가에게.",
                color = WarmCream.copy(alpha = 0.92f),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedTextField(
                value = userId,
                onValueChange = { value -> onUserIdChange(value.filter { it.isDigit() }) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "개발용 사용자 ID") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onWriteClick,
                    modifier = Modifier.weight(1f),
                    enabled = userId.toLongOrNull() != null,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LampOrange,
                        contentColor = SoftBrown
                    )
                ) {
                    Text(text = "엽서 쓰기")
                }

                OutlinedButton(
                    onClick = onTodayClick,
                    modifier = Modifier.weight(1f),
                    enabled = userId.toLongOrNull() != null,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "오늘의 엽서")
                }
            }
        }
    }
}

@Composable
private fun WritePostcardScreen(
    userId: Long?,
    apiClient: PostcardApiClient,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<RequestState>(RequestState.Idle) }
    val canSubmit = userId != null && message.isNotBlank() && state !is RequestState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmCream)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        TextButton(onClick = onBackClick) {
            Text(text = "뒤로", color = SoftBrown)
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "엽서 쓰기",
            color = MidnightNavy,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "오늘의 장면과 마음을 짧게 적어보세요.",
            color = SoftBrown,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = message,
            onValueChange = {
                message = it.take(500)
                state = RequestState.Idle
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            label = { Text(text = "엽서 내용") },
            placeholder = { Text(text = "예: 오늘 밤 공기가 조금 시원하네요.") }
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "${message.length}/500",
            color = SoftBrown,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val currentUserId = userId ?: return@Button
                scope.launch {
                    state = RequestState.Loading
                    state = runCatching {
                        apiClient.createPostcard(currentUserId, message)
                    }.fold(
                        onSuccess = {
                            message = ""
                            RequestState.Success("엽서를 우체통에 넣었습니다.")
                        },
                        onFailure = { RequestState.Error(it.message ?: "엽서를 저장하지 못했습니다.") }
                    )
                }
            },
            enabled = canSubmit,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = LampOrange,
                contentColor = SoftBrown
            )
        ) {
            if (state is RequestState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(18.dp)
                        .height(18.dp),
                    strokeWidth = 2.dp,
                    color = SoftBrown
                )
            } else {
                Text(text = "저장하기")
            }
        }

        RequestMessage(state = state)
    }
}

@Composable
private fun TodayPostcardScreen(
    userId: Long?,
    apiClient: PostcardApiClient,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RequestState>(RequestState.Idle) }
    var postcard by remember { mutableStateOf<TodayPostcardResponse?>(null) }
    val isLoading = state is RequestState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightNavy)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        TextButton(onClick = onBackClick) {
            Text(text = "뒤로", color = WarmCream)
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "오늘의 엽서",
            color = WarmCream,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "이름 모를 누군가의 마음이 도착합니다.",
            color = WarmCream.copy(alpha = 0.86f),
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(28.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = WarmCream,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                if (postcard == null) {
                    Text(
                        text = "아직 열린 엽서가 없습니다.",
                        color = MidnightNavy,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "오늘 받을 엽서를 배정받으면 이곳에 문장이 나타납니다.",
                        color = SoftBrown,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = postcard!!.content,
                        color = MidnightNavy,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "도착일 ${postcard!!.deliveredDate}",
                        color = SoftBrown,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val currentUserId = userId ?: return@Button
                    scope.launch {
                        state = RequestState.Loading
                        state = runCatching {
                            apiClient.assignTodayPostcard(currentUserId)
                        }.fold(
                            onSuccess = {
                                postcard = it
                                RequestState.Success("오늘의 엽서를 열었습니다.")
                            },
                            onFailure = { RequestState.Error(it.message ?: "엽서를 배정받지 못했습니다.") }
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = userId != null && !isLoading,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LampOrange,
                    contentColor = SoftBrown
                )
            ) {
                Text(text = "받기")
            }

            OutlinedButton(
                onClick = {
                    val currentUserId = userId ?: return@OutlinedButton
                    scope.launch {
                        state = RequestState.Loading
                        state = runCatching {
                            apiClient.getTodayPostcard(currentUserId)
                        }.fold(
                            onSuccess = {
                                postcard = it
                                RequestState.Success("오늘 도착한 엽서를 다시 열었습니다.")
                            },
                            onFailure = { RequestState.Error(it.message ?: "엽서를 찾지 못했습니다.") }
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = userId != null && !isLoading,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "다시 보기")
            }
        }

        RequestMessage(state = state)
    }
}

@Composable
private fun RequestMessage(state: RequestState) {
    when (state) {
        RequestState.Idle -> Unit
        RequestState.Loading -> {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "잠시만 기다려주세요.",
                color = SoftBrown,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is RequestState.Success -> {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.message,
                color = SoftBrown,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is RequestState.Error -> {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.message,
                color = Color(0xFF9B3D36),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MoonLiterAppPreview() {
    com.moonliter.app.ui.theme.MoonLiterTheme {
        MoonLiterApp()
    }
}
