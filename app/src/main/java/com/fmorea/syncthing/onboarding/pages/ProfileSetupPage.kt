package com.fmorea.syncthing.onboarding.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.fmorea.syncthing.R
import com.fmorea.syncthing.onboarding.OnboardingIcon
import com.fmorea.syncthing.onboarding.OnboardingScaffold
import com.fmorea.syncthing.onboarding.OnboardingUiState
import com.fmorea.syncthing.service.Constants
import com.fmorea.syncthing.syncthing.UserProfile
import java.io.File

@Composable
fun ProfileSetupPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    requestTvFocus: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val myId = remember(uiState.hasConfig) { prefs.getString(Constants.PREF_LOCAL_DEVICE_ID, "") ?: "" }
    val rootDir = remember { File(context.filesDir, Constants.LINKTHING_DIR_NAME) }
    
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }

    LaunchedEffect(myId, rootDir) {
        if (myId.isNotBlank()) {
            val profile = UserProfile.load(myId, myId, rootDir)
            firstName = profile.firstName
            lastName = profile.lastName
            phoneNumber = profile.phoneNumber ?: ""
        }
    }

    OnboardingScaffold(
        icon = OnboardingIcon.Logo,
        title = "Il tuo Profilo",
        description = "Inserisci il tuo nome per farti riconoscere dai tuoi amici nella rete EtherMesh.",
        pageIndex = pageIndex,
        pageCount = uiState.pages.size,
        nextLabel = stringResource(R.string.cont),
        requestTvFocus = requestTvFocus,
        onBack = onBack,
        onNext = {
            if (myId.isNotBlank()) {
                val currentProfile = UserProfile.load(myId, myId, rootDir)
                val newProfile = currentProfile.copy(
                    firstName = firstName,
                    lastName = lastName,
                    phoneNumber = phoneNumber.ifBlank { null },
                    discloserId = myId
                )
                UserProfile.save(newProfile, myId, rootDir)
            }
            onContinue()
        },
        action = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Cognome") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Telefono (opzionale)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("+39...") }
                )
                
                Text(
                    "Queste informazioni verranno condivise solo con i dispositivi a cui ti connetterai direttamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    )
}
