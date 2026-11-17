package com.snowdos.app.feature.account

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snowdos.app.R
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.components.SnowGlassCard
import com.snowdos.app.core.designsystem.components.snowPressFeedback
import com.snowdos.app.domain.model.AuthState
import com.snowdos.app.domain.model.SyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = SnowDosThemeTokens.current

    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.md)
                .padding(
                    bottom = if (onBack == null) {
                        tokens.spacing.floatingNavBottomClearance
                    } else {
                        tokens.spacing.xl
                    }
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(tokens.spacing.md))

            Text(
                text = stringResource(R.string.account_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(tokens.spacing.sm))

            val stubMessage = when (val auth = uiState.authState) {
                is AuthState.UnavailableStub -> stringResource(R.string.account_stub_message)
                AuthState.Unauthenticated -> stringResource(R.string.account_unauthenticated)
                is AuthState.Authenticated -> stringResource(R.string.account_authenticated, auth.displayName)
            }
            Text(
                text = stubMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(tokens.spacing.lg))

            SnowGlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(tokens.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
                ) {
                    Text(
                        text = stringResource(R.string.account_sync_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    val syncLabel = when (uiState.syncStatus) {
                        SyncStatus.LocalOnly -> stringResource(R.string.account_sync_local_only)
                        is SyncStatus.Idle -> stringResource(R.string.account_sync_idle)
                        SyncStatus.Syncing -> stringResource(R.string.account_sync_syncing)
                        is SyncStatus.Error -> stringResource(R.string.account_sync_error)
                    }
                    Text(
                        text = syncLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (onBack != null) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.account_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        val backInteraction = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = onBack,
                            interactionSource = backInteraction,
                            modifier = Modifier.snowPressFeedback(backInteraction, pressedScale = 0.92f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            content(Modifier.padding(innerPadding))
        }
    } else {
        content(modifier)
    }
}
