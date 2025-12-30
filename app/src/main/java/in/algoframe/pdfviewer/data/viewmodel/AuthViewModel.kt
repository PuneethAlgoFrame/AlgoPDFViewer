package `in`.algoframe.pdfviewer.data.viewmodel


import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val auth = Firebase.auth
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState
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

    fun signOut() {
        // No need to manually update state here either. The listener will handle it.
        auth.signOut()
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