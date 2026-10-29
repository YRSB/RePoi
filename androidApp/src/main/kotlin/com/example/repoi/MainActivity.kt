package com.example.repoi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nobody.poishelf.platform.initPoiShelfActivity
import com.nobody.poishelf.ui.navigation.PoiShelfApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initPoiShelfActivity(this)

        setContent {
            PoiShelfApp()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    PoiShelfApp()
}