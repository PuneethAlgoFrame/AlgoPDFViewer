package `in`.algoframe.pdfviewer.ui.screens

import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.algoframe.pdfviewer.data.viewmodel.AuthViewModel
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import `in`.algoframe.pdfviewer.R // Make sure to import your R file
import `in`.algoframe.pdfviewer.local.getPref
import `in`.algoframe.pdfviewer.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    navController: NavHostController ,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preference = context.getPref()
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
                        authViewModel.signInWithGoogle(idToken)
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.REGISTER) { inclusive = true }
                        }
                    }
                } else {
                    Log.w("RegisterScreen", "Google Sign-In succeeded but ID token was null.")
                }
            } catch (e: ApiException) {
                // Google Sign In failed
                Log.w("RegisterScreen", "Google sign in failed", e)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            // Launch the Google Sign-In intent
            val signInIntent = getGoogleSignInIntent(context)
            googleSignInLauncher.launch(signInIntent)
        }) {
            Text("Sign in with Google")
        }
    }
}

@Preview
@Composable
fun RegisterScreenPreview() {
    RegisterScreen(navController = NavHostController(LocalContext.current))
}


/**
 * Creates and configures the Google Sign-In options and returns an Intent
 * to launch the sign-in flow.
 */
private fun getGoogleSignInIntent(context: Context): Intent {
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()

    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    // Ensure any previously signed-in user is signed out before starting a new flow
    //googleSignInClient.signOut()

    return googleSignInClient.signInIntent
}
