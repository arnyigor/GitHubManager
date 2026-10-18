package dev.arny.githubmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.arny.githubmanager.api.GitHubApi
import dev.arny.githubmanager.security.SecureTokenStore
import dev.arny.githubmanager.security.EncryptedDraftStore
import dev.arny.githubmanager.remote.RemoteGitHubApi
import dev.arny.githubmanager.ui.GitHubManagerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SecureTokenStore(applicationContext)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(GitHubApi(applicationContext), store, EncryptedDraftStore(applicationContext), RemoteGitHubApi()) as T
            }
        }
        setContent {
            val vm: MainViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsState()
            GitHubManagerApp(state, vm)
        }
    }
}
