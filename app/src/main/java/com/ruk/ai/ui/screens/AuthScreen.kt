package com.ruk.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ruk.ai.data.repository.AuthRepository
import com.ruk.ai.data.repository.Result
import com.ruk.ai.di.ServiceLocator
import com.ruk.ai.ui.components.ErrorBanner
import com.ruk.ai.ui.components.GradientButton
import com.ruk.ai.ui.components.RukLogo
import com.ruk.ai.ui.theme.RukColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val registerMode: Boolean = false
)

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun toggleMode() {
        _state.value = _state.value.copy(
            registerMode = !_state.value.registerMode,
            error = null
        )
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(error = message, busy = false)
    }

    fun submit(email: String, password: String, name: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            setError("Enter your email and password.")
            return
        }
        if (_state.value.registerMode && password.length < 8) {
            setError("Password must be at least 8 characters.")
            return
        }
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = if (_state.value.registerMode) {
                repo.register(email, password, name)
            } else {
                repo.login(email, password)
            }
            when (result) {
                is Result.Success -> {
                    _state.value = _state.value.copy(busy = false)
                    onSuccess()
                }
                is Result.Failure ->
                    _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }

    fun google(idToken: String, onSuccess: () -> Unit) {
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            when (val result = repo.google(idToken)) {
                is Result.Success -> {
                    _state.value = _state.value.copy(busy = false)
                    onSuccess()
                }
                is Result.Failure ->
                    _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }
}

@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    val vm: AuthViewModel = viewModel { AuthViewModel(ServiceLocator.authRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(RukColors.NavyDeep, RukColors.Black, RukColors.Black)
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            RukLogo(size = 96)
            Spacer(Modifier.height(18.dp))
            Text(
                "RUK AI",
                style = MaterialTheme.typography.headlineLarge,
                color = RukColors.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                "All-in-One AI Assistant",
                style = MaterialTheme.typography.bodyMedium,
                color = RukColors.Grey
            )
            Spacer(Modifier.height(36.dp))

            Text(
                if (state.registerMode) "Create your account" else "Welcome back",
                style = MaterialTheme.typography.titleLarge,
                color = RukColors.White
            )
            Spacer(Modifier.height(18.dp))

            state.error?.let {
                ErrorBanner(it)
                Spacer(Modifier.height(12.dp))
            }

            if (state.registerMode) {
                RukField(name, { name = it }, "Name", Icons.Filled.Person)
                Spacer(Modifier.height(12.dp))
            }
            RukField(email, { email = it }, "Email", Icons.Filled.Email, keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            RukField(password, { password = it }, "Password", Icons.Filled.Lock, isPassword = true)
            Spacer(Modifier.height(22.dp))

            GradientButton(
                text = if (state.registerMode) "Create account" else "Sign in",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                onClick = { vm.submit(email, password, name, onAuthenticated) }
            )

            if (state.busy) {
                Spacer(Modifier.height(14.dp))
                CircularProgressIndicator(
                    color = RukColors.PurpleLight,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                if (state.registerMode) "Already have an account? Sign in"
                else "New to RUK AI? Create an account",
                style = MaterialTheme.typography.labelLarge,
                color = RukColors.PurpleLight,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.toggleMode() }
                    .padding(vertical = 8.dp)
            )

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("\u2014", color = RukColors.GreyDim)
                Text("  or  ", style = MaterialTheme.typography.labelSmall, color = RukColors.GreyDim)
                Text("\u2014", color = RukColors.GreyDim)
            }
            Spacer(Modifier.height(16.dp))

            GoogleSignInButton(
                enabled = !state.busy,
                onToken = { token -> vm.google(token, onAuthenticated) },
                onError = { message -> vm.setError(message) }
            )

            Spacer(Modifier.height(28.dp))
            Text(
                "Your provider API keys are stored encrypted on the server and are " +
                    "never embedded in the app. Sign in to sync your chats and history.",
                style = MaterialTheme.typography.labelSmall,
                color = RukColors.GreyDim,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RukField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = RukColors.Grey) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = ImeAction.Next
        ),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RukColors.Purple,
            unfocusedBorderColor = RukColors.SurfaceBorder,
            focusedTextColor = RukColors.White,
            unfocusedTextColor = RukColors.White,
            focusedLabelColor = RukColors.PurpleLight,
            unfocusedLabelColor = RukColors.Grey,
            cursorColor = RukColors.PurpleLight
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
