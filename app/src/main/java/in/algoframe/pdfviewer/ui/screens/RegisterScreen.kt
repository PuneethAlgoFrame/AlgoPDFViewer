package `in`.algoframe.pdfviewer.ui.screens

import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.algoframe.pdfviewer.data.viewmodel.AuthViewModel
import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import `in`.algoframe.pdfviewer.R // Make sure to import your R file
import `in`.algoframe.pdfviewer.data.viewmodel.AuthState
import `in`.algoframe.pdfviewer.navigation.Routes
import `in`.algoframe.pdfviewer.ui.theme.PurpleGrey40
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    navController: NavHostController ,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authState by authViewModel.authState.collectAsState()

    when (authState) {
        AuthState.Loading -> {
            Log.w("AuthGateScreen", "Loading")
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
        else -> { }
    }

    // Create the ActivityResultLauncher to handle the Google Sign-In result
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken //Short-lived (≈ 1 hour)
                if (idToken != null) {
                    Log.w("RegisterScreen", "Google Sign-In succeeded idToken = $idToken")
                    coroutineScope.launch {
                        // If we reach here, Firebase auth was successful
                        //preference.saveBoolean(StorageHelper.LOGGED_IN, true)
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.REGISTER) { inclusive = true }
                        }
                        authViewModel.signInWithGoogle(idToken)
                        authViewModel.hideLoading()
                    }
                } else {
                    Log.w("RegisterScreen", "Google Sign-In succeeded but ID token was null.")
                }
            } catch (e: ApiException) {
                // Google Sign In failed
                Log.w("RegisterScreen", "Google sign in failed", e)
            }
        }else{
            authViewModel.hideLoading()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        Column(modifier = Modifier.padding(21.dp)){
            Text(stringResource(R.string.app_name),
                color = PurpleGrey40, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(R.string.description),
                color = PurpleGrey40,  fontSize = 18.sp)
            Spacer(modifier = Modifier.height(40.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        //Text(stringResource(R.string.register_create_account), color = PurpleGrey40, fontSize = 14.sp, fontWeight = FontWeight.Bold)

        Button(
            modifier = Modifier.border(
                width = 1.dp,
                color = Color.LightGray, // Light grey border
                shape = RoundedCornerShape(8.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White, // White background
                contentColor = Color.Black // Black text
            ),
            onClick = {
            // Launch the Google Sign-In intent
            val signInIntent = authViewModel.getGoogleSignInIntent(context)
            googleSignInLauncher.launch(signInIntent)
        }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp) // Padding inside the button
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_google_logo), // Your Google icon
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(24.dp) // Standard icon size
                )
                Spacer(modifier = Modifier.width(12.dp)) // Space between icon and text
                Text(
                    text = stringResource(R.string.sign_in_with_google),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.primary,
                text = stringResource(R.string.login_agreement) + " ",
            )
            Text(
                fontSize = 12.sp,
                text = stringResource(R.string.termsConditions),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW,
                        "https://www.algoframe.in/terms-conditions/".toUri())
                    context.startActivity(intent)
                },
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Preview
@Composable
fun RegisterScreenPreview() {
    RegisterScreen(navController = NavHostController(LocalContext.current))
}