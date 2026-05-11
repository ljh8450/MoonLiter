package com.moonliter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.moonliter.app.ui.MoonLiterApp
import com.moonliter.app.ui.theme.MoonLiterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MoonLiterTheme {
                MoonLiterApp()
            }
        }
    }
}
