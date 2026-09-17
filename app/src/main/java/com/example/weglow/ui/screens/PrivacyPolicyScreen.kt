package com.example.weglow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.weglow.ui.components.LegalSection
import com.example.weglow.ui.theme.*

/**
 * WeGlow's Privacy Policy, read directly inside the app - no WebView, no external browser.
 * Content mirrors the legal/data-processing investigation for this app; do not add claims
 * about functionality (payments, account deletion, analytics, etc.) that does not exist yet.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(PageBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextBlack)
            }
            Text("Privacy Policy", style = MaterialTheme.typography.titleLarge, color = TextBlack)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                "Effective Date: 16 September 2026",
                style = MaterialTheme.typography.bodySmall,
                color = SoftGray,
            )

            LegalSection(
                title = "1. Introduction",
                body = "WeGlow respects your privacy and is committed to handling your personal " +
                    "information responsibly. This Privacy Policy explains what information WeGlow " +
                    "collects, how it is used, how certain information is stored, and the choices " +
                    "available to you when using the WeGlow mobile application.\n\n" +
                    "By using WeGlow, you acknowledge the practices described in this Privacy Policy.",
            )

            LegalSection(
                title = "2. Information We Collect",
                body = "Depending on the features you use, WeGlow may process:\n\n" +
                    "Account information:\n" +
                    "• Full name\n" +
                    "• Email address\n" +
                    "• Account identifier\n" +
                    "• Authentication information handled through Supabase Auth\n" +
                    "• Information provided through Google Sign-In if you choose that method\n\n" +
                    "Profile and questionnaire information:\n" +
                    "• Age range\n" +
                    "• Gender\n" +
                    "• Skin type\n" +
                    "• Skin sensitivity\n" +
                    "• Profile photo, if you choose to upload one\n" +
                    "• Face shape generated from hairstyle analysis\n\n" +
                    "Skin analysis information:\n\n" +
                    "When you use WeGlow's skin-analysis feature, the application may identify and " +
                    "record counts relating to supported visible skin concerns, including dark spots, " +
                    "open pores, blackheads, acne scars, papules, whiteheads, nodules, cysts, pustules, " +
                    "and freckles.\n\n" +
                    "These automated results may be inaccurate and are not a medical diagnosis.",
            )

            LegalSection(
                title = "3. Photos and AI Analysis",
                body = "Photos used for skin and hairstyle analysis are processed on your device " +
                    "using WeGlow's on-device machine-learning functionality.\n\n" +
                    "WeGlow does not upload your skin or hairstyle scan photographs to its backend " +
                    "as part of the current scan process.\n\n" +
                    "Certain results derived from those scans may be stored, including:\n" +
                    "• detected skin-concern counts; and\n" +
                    "• detected face shape.\n\n" +
                    "Camera-captured scan images may temporarily remain within the application's " +
                    "private cache and may be removed when replaced, when you sign out, or through " +
                    "Android's normal cache-management processes.\n\n" +
                    "If you separately choose to set a profile photo, that photo is uploaded to " +
                    "private Supabase Storage and is therefore treated differently from scan " +
                    "photographs.",
            )

            LegalSection(
                title = "4. Location and Environmental Information",
                body = "With your permission, WeGlow may access your device's foreground location " +
                    "to provide location-relevant information such as:\n" +
                    "• UV information\n" +
                    "• humidity\n" +
                    "• weather conditions\n" +
                    "• air-quality/environmental information supported by the application\n\n" +
                    "Location coordinates are used to obtain this environmental information and are " +
                    "not stored in WeGlow's application database as part of the current " +
                    "implementation.\n\n" +
                    "Location information may be transmitted to environmental-data providers when " +
                    "necessary to provide these features.",
            )

            LegalSection(
                title = "5. How We Use Information",
                body = "WeGlow uses information processed through the application to:\n" +
                    "• create and maintain your account;\n" +
                    "• personalize your experience;\n" +
                    "• maintain your profile and preferences;\n" +
                    "• perform skin and face-shape analysis;\n" +
                    "• provide skincare and hairstyle recommendations;\n" +
                    "• provide location-relevant environmental information;\n" +
                    "• maintain your Saved Products and Cart;\n" +
                    "• operate, secure, and maintain the application; and\n" +
                    "• provide functionality you request.",
            )

            LegalSection(
                title = "6. Saved Products and Cart",
                body = "When signed in, WeGlow may store products you save and products you add to " +
                    "your Cart, including their quantities.\n\n" +
                    "The current version of WeGlow does not provide completed ordering, checkout, " +
                    "or payment functionality. Therefore, WeGlow does not currently collect payment " +
                    "information through these features.",
            )

            LegalSection(
                title = "7. Third-Party Services",
                body = "WeGlow currently relies on third-party technology providers to operate " +
                    "certain features, including:\n\n" +
                    "Supabase — authentication, database services, and private profile-image " +
                    "storage.\n\n" +
                    "Google Sign-In — optional account authentication.\n\n" +
                    "WeatherAPI.com and Open-Meteo — environmental and weather-related information. " +
                    "Location coordinates may be transmitted when requesting relevant environmental " +
                    "information.\n\n" +
                    "Google ML Kit — used for on-device face detection within the current " +
                    "implementation.\n\n" +
                    "Third-party services may process information according to their own terms and " +
                    "privacy practices.",
            )

            LegalSection(
                title = "8. Advertising and Analytics",
                body = "The current version of WeGlow does not contain an advertising SDK or " +
                    "third-party analytics/tracking SDK.\n\n" +
                    "WeGlow does not currently use user information to serve third-party " +
                    "advertisements within the application.",
            )

            LegalSection(
                title = "9. Data Security",
                body = "WeGlow uses technical measures designed to protect user information, " +
                    "including authenticated access, database Row Level Security, private storage " +
                    "access controls, encrypted network connections, and server-side handling of " +
                    "sensitive API credentials where applicable.\n\n" +
                    "However, no electronic storage or transmission system can be guaranteed to be " +
                    "completely secure.",
            )

            LegalSection(
                title = "10. Your Choices",
                body = "You may choose whether to grant certain device permissions, including " +
                    "camera, location, and notification permissions.\n\n" +
                    "Some features may not function properly if the permission required for that " +
                    "feature is denied.\n\n" +
                    "You can remove individual Saved Products and Cart items and can replace your " +
                    "profile photograph through currently available application functionality.\n\n" +
                    "At present, WeGlow does not provide self-service account deletion within the " +
                    "application.\n\n" +
                    "For questions or requests concerning your account or personal information, " +
                    "contact us at:\n\n" +
                    "team03weglow@gmail.com",
            )

            LegalSection(
                title = "11. Age Requirement",
                body = "WeGlow is intended for users who are at least 14 years old.\n\n" +
                    "Users under the age of 14 should not create or use a WeGlow account.",
            )

            LegalSection(
                title = "12. Health and Medical Disclaimer",
                body = "WeGlow provides automated skincare information and recommendations for " +
                    "general informational and personal-care purposes.\n\n" +
                    "WeGlow does not provide medical diagnoses, medical treatment, or professional " +
                    "medical advice.\n\n" +
                    "Automated analysis and recommendations can be inaccurate. You should not rely " +
                    "on WeGlow as a substitute for advice, diagnosis, or treatment from a qualified " +
                    "healthcare professional.\n\n" +
                    "If you have concerns about your skin or health, seek appropriate professional " +
                    "medical advice.",
            )

            LegalSection(
                title = "13. Changes to This Privacy Policy",
                body = "We may update this Privacy Policy as WeGlow develops or its features and " +
                    "data practices change.\n\n" +
                    "Where appropriate, the updated policy will be made available through the " +
                    "application, together with an updated effective date.",
            )

            LegalSection(
                title = "14. Contact Us",
                body = "For privacy-related questions or requests:\n\n" +
                    "WeGlow\n" +
                    "team03weglow@gmail.com",
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}
