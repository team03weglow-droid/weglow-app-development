package com.example.weglow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.weglow.domain.model.Gender
import com.example.weglow.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Account-level settings reached from Profile's Quick links.
 *
 * Every field here reads from, and writes through, the same [com.example.weglow.feature.profile.ProfileViewModel]
 * / [com.example.weglow.domain.repository.ProfileRepository] / [com.example.weglow.domain.repository.AuthRepository]
 * used by ProfileScreen - this screen introduces no new source of truth. Gender editing in
 * particular is the exact same `onGenderSelected` -> `ProfileRepository.updateGender` ->
 * `profiles.gender` path Profile already used; it was moved here rather than duplicated.
 *
 * Name and email are read-only: the existing authentication architecture has no
 * change-name/change-email operation, so this screen does not invent one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    displayName: String? = null,
    email: String? = null,
    gender: String? = null,
    isUpdatingGender: Boolean = false,
    genderError: String? = null,
    onGenderSelected: (String) -> Unit = {},
    onConsumeGenderError: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    onTermsConditionsClick: () -> Unit = {},
) {
    var showGenderChooser by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(genderError) {
        if (genderError != null) {
            delay(4000)
            onConsumeGenderError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(remember { androidx.compose.foundation.ScrollState(0) })
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextBlack)
            }
            Text("Account Settings", style = MaterialTheme.typography.titleLarge, color = TextBlack)
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text("Profile", style = MaterialTheme.typography.headlineSmall, color = TextBlack, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardWhite)
            ) {
                AccountInfoRow(label = "Name", value = displayName ?: "Not set")
                HorizontalDivider(color = TrackGray)
                AccountInfoRow(label = "Email", value = email ?: "Not set")
                HorizontalDivider(color = TrackGray)
                GenderSettingsRow(
                    gender = gender,
                    isUpdating = isUpdatingGender,
                    onClick = { showGenderChooser = true },
                )
            }
            if (genderError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    genderError,
                    style = MaterialTheme.typography.bodySmall,
                    color = LogoutRed,
                    modifier = Modifier.clickable(onClick = onConsumeGenderError)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("Legal", style = MaterialTheme.typography.headlineSmall, color = TextBlack, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardWhite)
            ) {
                LegalLinkRow(label = "Privacy Policy", onClick = onPrivacyPolicyClick)
                HorizontalDivider(color = TrackGray)
                LegalLinkRow(label = "Terms & Conditions", onClick = onTermsConditionsClick)
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    if (showGenderChooser) {
        ModalBottomSheet(
            onDismissRequest = { showGenderChooser = false },
            containerColor = CardWhite,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text("Gender", style = MaterialTheme.typography.titleMedium, color = TextBlack)
                Spacer(modifier = Modifier.height(4.dp))
                Gender.OPTIONS.forEach { option ->
                    GenderOptionRow(
                        label = option,
                        isSelected = option == gender,
                    ) {
                        showGenderChooser = false
                        if (option != gender) onGenderSelected(option)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextBlack)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = SoftGray)
    }
}

@Composable
private fun GenderSettingsRow(gender: String?, isUpdating: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isUpdating, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Wc, contentDescription = null, tint = DarkGreen)
            Spacer(modifier = Modifier.width(14.dp))
            Text("Gender", style = MaterialTheme.typography.bodyLarge, color = TextBlack)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                gender ?: "Not set",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray,
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (isUpdating) {
                CircularProgressIndicator(
                    color = DarkGreen,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray)
            }
        }
    }
}

@Composable
private fun LegalLinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextBlack)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray)
    }
}

@Composable
private fun GenderOptionRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextBlack)
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = DarkGreen)
        }
    }
}
