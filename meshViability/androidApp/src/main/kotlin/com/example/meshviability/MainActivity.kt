package com.example.meshviability

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {

    private var permissionAnswer: CompletableDeferred<Boolean>? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionAnswer?.complete(result.values.all { it })
    }

    private val requestPermissions: suspend (Array<String>) -> Boolean = { permissions ->
        val answer = CompletableDeferred<Boolean>()
        permissionAnswer = answer
        permissionLauncher.launch(permissions)
        answer.await()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as MeshViabilityApp
        app.blePermissions.requester = requestPermissions

        setContent {
            App(app.radioLinks)
        }
    }

    override fun onDestroy() {
        val permissions = (application as MeshViabilityApp).blePermissions
        if (permissions.requester === requestPermissions) permissions.requester = null
        permissionAnswer?.complete(false)
        super.onDestroy()
    }
}
