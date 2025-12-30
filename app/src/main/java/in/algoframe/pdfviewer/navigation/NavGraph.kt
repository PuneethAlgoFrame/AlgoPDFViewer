package `in`.algoframe.pdfviewer.navigation

import AuthGateScreen
import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import `in`.algoframe.pdfviewer.ui.screens.FilePickerScreen
import `in`.algoframe.pdfviewer.ui.screens.PdfViewerScreen
import `in`.algoframe.pdfviewer.ui.screens.RegisterScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    val startDestination = Routes.AUTH_GATE

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(250)
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth  },
                animationSpec = tween(250, )
            )
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth  },
                animationSpec = tween(250,)
            )
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(250,)
            )
        }
    ) {
        composable(Routes.AUTH_GATE) {
            AuthGateScreen(navController)
        }

        composable(Routes.REGISTER) { RegisterScreen(navController) }

        composable(Routes.PREVIEW + "/{pdfPath}",
            arguments = listOf(navArgument("pdfPath") { type = NavType.StringType }))
        {  b->
            val pdfPath = b.arguments?.getString("pdfPath")
            val decodedPath = URLDecoder.decode(pdfPath, StandardCharsets.UTF_8.toString())
            PdfViewerScreen(
                pdfPath = decodedPath,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.HOME) {
            FilePickerScreen(
                onPdfSelected = { pdfPath ->
                    val encodedPdfPath = URLEncoder.encode(pdfPath, StandardCharsets.UTF_8.toString())
                    navController.navigate(Routes.PREVIEW + "/$encodedPdfPath")
                }
            )
        }
    }
}
