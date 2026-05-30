package com.dendy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dendy.app.ui.DendyApp
import com.dendy.app.ui.theme.DendyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as DendyApplication).appGraph
        setContent {
            DendyTheme {
                DendyApp(appGraph = graph)
            }
        }
    }
}

