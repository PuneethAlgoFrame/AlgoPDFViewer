package `in`.algoframe.pdfviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import `in`.algoframe.pdfviewer.navigation.AppNavHost
import `in`.algoframe.pdfviewer.ui.theme.PDFReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PDFReaderTheme {
                AppNavHost()
            }
        }
    }
}