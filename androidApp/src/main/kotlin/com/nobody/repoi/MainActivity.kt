package com.nobody.repoi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nobody.repoi.di.imagePickerModule
import com.nobody.repoi.di.initKoin
import com.nobody.repoi.platform.ImagePickerLauncher
import com.nobody.repoi.util.AppLogger
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules

class MainActivity : ComponentActivity() {

    private val imagePicker = ImagePickerLauncher()

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        imagePicker.onResult(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AppLogger.installCrashHandler()

        imagePicker.launcher = pickImageLauncher
        initKoin {
            androidContext(applicationContext)
        }
        loadKoinModules(imagePickerModule(imagePicker))

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
