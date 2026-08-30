package com.fmorea.syncthing.onboarding.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.fmorea.syncthing.syncthing.Avatar
import com.fmorea.syncthing.syncthing.ImageCropper
import com.fmorea.syncthing.syncthing.UserProfile
import java.io.File

@Composable
fun ProfilePhotoPage(
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
    
    var hasPhoto by remember { mutableStateOf(false) }
    var photoUpdateKey by remember { mutableIntStateOf(0) }
    var croppingImageFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(myId, rootDir) {
        if (myId.isNotBlank()) {
            val photo = UserProfile.findPhoto(myId, myId, rootDir)
            hasPhoto = photo != null
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                val tempFile = File(context.cacheDir, "crop_input_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                croppingImageFile = tempFile
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    if (croppingImageFile != null) {
        ImageCropper(
            imageFile = croppingImageFile!!,
            onCropDone = { croppedFile ->
                try {
                    val timestamp = System.currentTimeMillis()
                    val destFile = File(rootDir, "${myId}_${myId}_$timestamp.jpg")
                    
                    // Clean up old photos
                    rootDir.listFiles { _, name -> 
                        name.startsWith("${myId}_") && 
                        (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp"))
                    }?.forEach { it.delete() }

                    croppedFile.inputStream().use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    hasPhoto = true
                    photoUpdateKey++
                    croppingImageFile = null
                    croppedFile.delete() 
                } catch (e: Exception) {
                    croppingImageFile = null
                }
            },
            onDismiss = {
                croppingImageFile?.delete()
                croppingImageFile = null
            }
        )
    }

    OnboardingScaffold(
        icon = OnboardingIcon.Logo,
        title = "Foto Profilo",
        description = "Scegli una foto e ritagliala per farla stare nel cerchio. Questo aiuterà i tuoi amici a riconoscerti.",
        pageIndex = pageIndex,
        pageCount = uiState.pages.size,
        nextLabel = if (hasPhoto) stringResource(R.string.cont) else "Salta",
        requestTvFocus = requestTvFocus,
        onBack = onBack,
        onNext = onContinue,
        action = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                key(photoUpdateKey) {
                    Avatar(deviceId = myId, size = 120)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasPhoto) "Cambia Foto" else "Scegli Foto")
                }
            }
        }
    )
}
