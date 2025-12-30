package com.algoframe.pdfreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.algoframe.pdfreader.ui.screens.FilePickerScreen
import com.algoframe.pdfreader.ui.screens.PdfViewerScreen
import com.algoframe.pdfreader.ui.theme.PDFReaderTheme

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