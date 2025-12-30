package `in`.algoframe.pdfviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import `in`.algoframe.pdfviewer.ui.screens.FilePickerScreen
import `in`.algoframe.pdfviewer.ui.screens.PdfViewerScreen
import `in`.algoframe.pdfviewer.ui.theme.PDFReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PDFReaderTheme {
                PDFReaderApp()
            }
        }
    }
}

@Composable
fun PDFReaderApp() {
    var selectedPdfPath by remember { mutableStateOf<String?>(null) }
    
    if (selectedPdfPath == null) {
        FilePickerScreen(
            onPdfSelected = { pdfPath ->
                selectedPdfPath = pdfPath
            }
        )
    } else {
        PdfViewerScreen(
            pdfPath = selectedPdfPath!!,
            onBack = {
                selectedPdfPath = null
            }
        )
    }
}