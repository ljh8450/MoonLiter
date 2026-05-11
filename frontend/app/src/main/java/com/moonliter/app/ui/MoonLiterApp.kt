package com.moonliter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moonliter.app.ui.theme.LampOrange
import com.moonliter.app.ui.theme.MidnightNavy
import com.moonliter.app.ui.theme.RainBlue
import com.moonliter.app.ui.theme.SoftBrown
import com.moonliter.app.ui.theme.WarmCream

private enum class MoonLiterScreen {
    Home,
    WritePostcard
}

@Composable
fun MoonLiterApp() {
    var screen by remember { mutableStateOf(MoonLiterScreen.Home) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MidnightNavy
    ) {
        when (screen) {
            MoonLiterScreen.Home -> HomeScreen(
                onWriteClick = { screen = MoonLiterScreen.WritePostcard }
            )

            MoonLiterScreen.WritePostcard -> WritePostcardScreen(
                onBackClick = { screen = MoonLiterScreen.Home }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onWriteClick: () -> Unit
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

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onWriteClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LampOrange,
                    contentColor = SoftBrown
                )
            ) {
                Text(text = "엽서 쓰기")
            }
        }
    }
}

@Composable
private fun WritePostcardScreen(
    onBackClick: () -> Unit
) {
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmCream)
            .padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        TextButton(onClick = onBackClick) {
            Text(text = "뒤로", color = SoftBrown)
        }

        Spacer(modifier = Modifier.height(24.dp))

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
            onValueChange = { message = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            label = { Text(text = "엽서 내용") },
            placeholder = { Text(text = "예: 오늘 밤 공기가 조금 시원하네요.") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {},
            enabled = message.isNotBlank(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = LampOrange,
                contentColor = SoftBrown
            )
        ) {
            Text(text = "저장하기")
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
