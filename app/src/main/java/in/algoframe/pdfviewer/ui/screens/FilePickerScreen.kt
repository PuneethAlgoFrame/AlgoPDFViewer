package `in`.algoframe.pdfviewer.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import `in`.algoframe.pdfviewer.utils.RecentPdfManager
import `in`.algoframe.pdfviewer.R
import `in`.algoframe.pdfviewer.data.viewmodel.AuthViewModel
import `in`.algoframe.pdfviewer.navigation.Routes
import `in`.algoframe.pdfviewer.ui.theme.PurpleGrey40
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FilePickerScreen(
    navController: NavHostController,
    onPdfSelected: (String) -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    // Inside FilePickerScreen composable, near the top
    var showLogoutDialog by remember { mutableStateOf(false) }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    val permissionsState = rememberMultiplePermissionsState(permissions)
    
    val recentPdfManager = remember { RecentPdfManager(context) }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val path = getPathFromUri(context, it)
            path?.let { pdfPath ->
                // Save to recent PDFs
                recentPdfManager.addRecentPdf(pdfPath)
                onPdfSelected(pdfPath)
            }
        }
    }
    
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    // Inside the Column of the Scaffold, after the Row containing the title and icon
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false }, // Dismiss if user clicks outside
            title = { Text(stringResource(R.string.confirm_logout)) },
            text = { Text(stringResource(R.string.are_you_sure_you_want_to_log_out)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false // Close the dialog
                        authViewModel.signOut(context,
                            context.getString(R.string.default_web_client_id))  // Perform the sign out
                        navController.navigate(Routes.REGISTER) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                ) {
                    Text(stringResource(R.string.logout))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false } // Just close the dialog
                ) {
                    Text(stringResource(R.string.cancel), color = Color.Black.copy(0.5f))
                }
            }
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(21.dp)
        ) {

            Row( modifier = Modifier
                .fillMaxWidth())
            {
                Text(
                    color = PurpleGrey40,
                    text = stringResource(R.string.app_name
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Logout Icon
                IconButton(onClick = { showLogoutDialog = true}) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Logout"
                    )
                }
            }

            if (!permissionsState.allPermissionsGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Storage Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please grant storage permissions to access PDF files",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { filePickerLauncher.launch("application/pdf") },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.select_pdf_file))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.recent_pdfs),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            RecentPdfList(
                onPdfSelected = { pdfPath ->
                    // Save to recent PDFs when selected from list
                    recentPdfManager.addRecentPdf(pdfPath)
                    onPdfSelected(pdfPath)
                },
                recentPdfManager = recentPdfManager
            )
        }
    }
}

@Composable
fun RecentPdfList(
    onPdfSelected: (String) -> Unit,
    recentPdfManager: RecentPdfManager
) {
    var pdfFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(Unit) {
        pdfFiles = getPdfFiles(recentPdfManager)
    }

    if (pdfFiles.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.no_pdf_files_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pdfFiles) { file ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            recentPdfManager.addRecentPdf(file.absolutePath)
                            onPdfSelected(file.absolutePath)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = file.parent ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun getPdfFiles(recentPdfManager: RecentPdfManager): List<File> {
    val pdfFiles = mutableListOf<File>()
    
    // First, get recently opened PDFs
    val recentPdfs = recentPdfManager.getRecentPdfs()
    recentPdfs.forEach { recentPdf ->
        val file = File(recentPdf.path)
        if (file.exists() && file.isFile) {
            pdfFiles.add(file)
        }
    }
    
    // Also try to get system PDFs (as a supplement)
    // Limit to avoid showing too many files
    val systemPdfs = recentPdfManager.getSystemPdfFiles().take(10)
    systemPdfs.forEach { file ->
        // Only add if not already in recent list
        if (!pdfFiles.any { it.absolutePath == file.absolutePath }) {
            pdfFiles.add(file)
        }
    }
    
    return pdfFiles
}

private fun getPathFromUri(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    val fileName = it.getString(index)
                    val cacheDir = context.cacheDir
                    val file = File(cacheDir, fileName)
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        result = file.absolutePath
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    } else if (uri.scheme == "file") {
        result = uri.path
    }
    return result
}

