import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import `in`.algoframe.pdfviewer.data.viewmodel.AuthState
import `in`.algoframe.pdfviewer.data.viewmodel.AuthViewModel
import `in`.algoframe.pdfviewer.navigation.Routes

@Composable
fun AuthGateScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()

    when (authState) {
        AuthState.Loading -> {
            Log.w("AuthGateScreen", "Loading")
            //SplashUI()
        }

        AuthState.Authenticated -> {
            Log.w("AuthGateScreen", "Authenticated")
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.AUTH_GATE) { inclusive = true }
            }
        }

        AuthState.Unauthenticated -> {
            Log.w("AuthGateScreen", "Unauthenticated")
            navController.navigate(Routes.REGISTER) {
                popUpTo(Routes.AUTH_GATE) { inclusive = true }
            }
        }
    }
}
