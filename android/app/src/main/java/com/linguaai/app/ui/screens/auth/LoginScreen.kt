package com.linguaai.app.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.ui.components.LinguaButton
import com.linguaai.app.ui.components.LinguaMarkTile
import com.linguaai.app.ui.theme.Spacing

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onAuthenticated: (Boolean) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.authEvents.collect { event ->
            when (event) {
                is AuthEvent.Authenticated -> onAuthenticated(event.onboarded)
            }
        }
    }

    LoginContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateToRegister = onNavigateToRegister,
    )
}

@Composable
internal fun LoginContent(
    state: LoginUiState,
    onEvent: (LoginEvent) -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            BrandHeader()

            OutlinedTextField(
                value = state.email,
                onValueChange = { onEvent(LoginEvent.EmailChanged(it)) },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                isError = state.emailError != null,
                supportingText = { state.emailError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = { onEvent(LoginEvent.PasswordChanged(it)) },
                label = { Text("Password") },
                isError = state.passwordError != null,
                supportingText = { state.passwordError?.let { Text(it) } },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )

            state.formError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        ) {
            LinguaButton(
                text = "Log in",
                onClick = { onEvent(LoginEvent.Submit) },
                enabled = state.isSubmitEnabled,
                isLoading = state.isLoading,
            )

            TextButton(
                onClick = onNavigateToRegister,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = Spacing.sm),
            ) {
                Text("New here? Create an account")
            }
        }
    }
}

/** Shared LinguaAI brand lockup used by the auth screens. */
@Composable
internal fun BrandHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        LinguaMarkTile(contentDescription = "LinguaAI")
        Text(
            text = "LinguaAI",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = "A calmer way to learn every day",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
