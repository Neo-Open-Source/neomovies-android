package com.neo.tv.presentation.profile

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil.compose.AsyncImage
import com.neo.neomovies.R
import com.neo.neomovies.auth.NeoIdAuthManager
import com.neo.tv.presentation.common.TvActionButton

@Composable
fun TvProfileScreen(
    onLoginWithNeoId: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val authManager = remember { NeoIdAuthManager(context) }
    var token by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf<String?>(null) }
    var avatar by remember { mutableStateOf<String?>(null) }

    fun loadAuthState() {
        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        token = prefs.getString("token", null)
        displayName = prefs.getString("display_name", null)
        email = prefs.getString("email", null)
        avatar = prefs.getString("avatar", null)
    }

    LaunchedEffect(Unit) {
        loadAuthState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentToken = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                    .getString("token", null)
                if (!currentToken.isNullOrBlank()) {
                    Thread {
                        authManager.fetchAndPersistProfile(currentToken)
                        Handler(Looper.getMainLooper()).post { loadAuthState() }
                    }.start()
                }
                loadAuthState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val nameForInitial = displayName?.takeIf { it.isNotBlank() } ?: email
    val initial = nameForInitial
        ?.trim()
        ?.firstOrNull()
        ?.uppercaseChar()
        ?.toString()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!avatar.isNullOrBlank()) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                modifier = Modifier.size(96.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (!initial.isNullOrBlank()) {
                    Text(
                        initial,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Text(
            text = displayName ?: stringResource(R.string.profile_title),
            style = MaterialTheme.typography.titleLarge,
        )

        if (!email.isNullOrBlank()) {
            Text(
                text = email!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsItem(title = stringResource(R.string.settings_title), onClick = onOpenSettings)
            SettingsItem(title = stringResource(R.string.about_title), onClick = onOpenAbout)
            if (!token.isNullOrBlank()) {
                SettingsItem(title = stringResource(R.string.action_logout), onClick = onLogout)
            }
        }

        if (token.isNullOrBlank()) {
            TvActionButton(
                text = stringResource(R.string.action_login_with_neo_id),
                onClick = onLoginWithNeoId,
                modifier = Modifier.fillMaxWidth(0.5f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        selected = false,
        onClick = onClick,
        headlineContent = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        trailingContent = { Text(text = stringResource(R.string.action_more)) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        ),
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.medium),
    )
}
