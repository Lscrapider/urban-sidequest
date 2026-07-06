package com.urbansidequest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.urbansidequest.app.ui.theme.UrbanSidequestTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UrbanSidequestTheme {
                UrbanSidequestApp()
            }
        }
    }
}
