package com.linguaai.app.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.ui.components.LinguaButton
import com.linguaai.app.ui.theme.Spacing

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegistered: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.authEvents.collect { event ->
            when (event) {
                AuthEvent.Authenticated -> onRegistered()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
    ) {
        BrandHeader()

        OutlinedTextField(
            value = state.email,
            onValueChange = { viewModel.onEvent(RegisterEvent.EmailChanged(it)) },
            label = { Text("Email") },
            isError = state.emailError != null,
            supportingText = { state.emailError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.lg),
        )

        OutlinedTextField(
            value = state.username,
            onValueChange = { viewModel.onEvent(RegisterEvent.UsernameChanged(it)) },
            label = { Text("Username") },
            isError = state.usernameError != null,
            supportingText = { state.usernameError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = { viewModel.onEvent(RegisterEvent.PasswordChanged(it)) },
            label = { Text("Password") },
            isError = state.passwordError != null,
            supportingText = {
                state.passwordError?.let { Text(it) }
                    ?: Text("At least 8 characters")
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
        )

        state.formError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }

        LinguaButton(
            text = "Create account",
            onClick = { viewModel.onEvent(RegisterEvent.Submit) },
            enabled = state.isSubmitEnabled,
            isLoading = state.isLoading,
            modifier = Modifier.padding(top = Spacing.lg),
        )

        TextButton(
            onClick = onNavigateToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
        ) {
            Text("Already have an account? Log in")
        }
    }
}
