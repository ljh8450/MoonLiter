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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun MoonLiterApp() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MidnightNavy
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
                    text = "새벽우체국",
                    color = WarmCream,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "오늘의 마음을, 누군가에게.",
                    color = WarmCream.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {},
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
}

@Preview(showBackground = true)
@Composable
private fun MoonLiterAppPreview() {
    com.moonliter.app.ui.theme.MoonLiterTheme {
        MoonLiterApp()
    }
}
