package `in`.algoframe.pdfviewer.data.viewmodel


import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import `in`.algoframe.pdfviewer.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val auth = Firebase.auth
    private val _authState = MutableStateFlow<AuthState?>(null)
    val authState: StateFlow<AuthState?> = _authState
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    init {
        checkAuth()
    }

    suspend fun signInWithGoogle(idToken: String) {
        // Create a credential with the Google ID token
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        // Sign in to Firebase with the credential
        auth.signInWithCredential(credential).await()
    }

    suspend fun getFirebaseIdToken(): String? {
        val user = Firebase.auth.currentUser ?: return null
        return user.getIdToken(false).await().token
    }

    fun showLoading(){
        _authState.value = AuthState.Loading
    }

    fun hideLoading(){
        _authState.value = null
    }

    private fun checkAuth() {
        // Create the listener callback
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            Log.d("AuthViewModel", "Auth state changed. User: ${user?.email}")

            // Update the state flow based on the user's status
            _authState.value = if (user != null) {
                AuthState.Authenticated
            } else {
                AuthState.Unauthenticated
            }
        }
        // Attach the listener to the FirebaseAuth instance
        auth.addAuthStateListener(authStateListener!!)
    }

    /**
     * Creates and configures the Google Sign-In options and returns an Intent
     * to launch the sign-in flow.
     */
    fun getGoogleSignInIntent(context: Context): Intent {
        showLoading()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(context, gso)

        // Ensure any previously signed-in user is signed out before starting a new flow
        //googleSignInClient.signOut()

        return googleSignInClient.signInIntent
    }

    fun signOut(context: Context, clientID: String) {
        // No need to manually update state here either. The listener will handle it.
        auth.signOut()
        //Required so that if user re-login ask's for choose Gmail account.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientID)
            .requestEmail()
            .build()

        GoogleSignIn.getClient(context, gso).signOut()
    }

    override fun onCleared() {
        super.onCleared()
        authStateListener?.let {
            auth.removeAuthStateListener(it)
            Log.d("AuthViewModel", "AuthStateListener removed.")
        }
    }
}

sealed class AuthState {
    object Loading : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
}