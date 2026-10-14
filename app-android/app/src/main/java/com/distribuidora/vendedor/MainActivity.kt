package com.distribuidora.vendedor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.distribuidora.vendedor.ui.AppRoot
import com.distribuidora.vendedor.ui.theme.VendorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VendorTheme {
                AppRoot()
            }
        }
    }
}
