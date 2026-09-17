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
 * WeGlow's Terms & Conditions, read directly inside the app - no WebView, no external browser.
 * Content mirrors the legal/data-processing investigation for this app; do not add claims
 * about functionality (payments, account deletion, medical care, etc.) that does not exist yet.
 */
@Composable
fun TermsConditionsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(PageBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextBlack)
            }
            Text("Terms & Conditions", style = MaterialTheme.typography.titleLarge, color = TextBlack)
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
                title = "1. Acceptance of Terms",
                body = "These Terms & Conditions govern your use of the WeGlow mobile application.\n\n" +
                    "By creating a WeGlow account or using the application, you agree to these " +
                    "Terms & Conditions and acknowledge the WeGlow Privacy Policy.\n\n" +
                    "If you do not agree, you should not use WeGlow.",
            )

            LegalSection(
                title = "2. Age Requirement",
                body = "You must be at least 14 years old to create and use a WeGlow account.",
            )

            LegalSection(
                title = "3. What WeGlow Provides",
                body = "WeGlow provides technology-assisted features relating to skincare, " +
                    "hairstyle recommendations, product discovery, environmental information, and " +
                    "personal-care guidance.\n\n" +
                    "Features may include:\n" +
                    "• skincare questionnaires;\n" +
                    "• automated skin analysis;\n" +
                    "• face-shape analysis;\n" +
                    "• skincare recommendations;\n" +
                    "• hairstyle recommendations;\n" +
                    "• environmental information;\n" +
                    "• product discovery;\n" +
                    "• Saved Products; and\n" +
                    "• Cart functionality.\n\n" +
                    "Features may be changed, improved, removed, or added as WeGlow develops.",
            )

            LegalSection(
                title = "4. AI and Automated Analysis",
                body = "Some WeGlow features use automated machine-learning models.\n\n" +
                    "Automated results are estimates and may be incorrect or incomplete.\n\n" +
                    "Skin-analysis results, face-shape classifications, product suggestions, " +
                    "hairstyle suggestions, and other automated recommendations are not guaranteed " +
                    "to be accurate or suitable for every person.",
            )

            LegalSection(
                title = "5. Not Medical Advice",
                body = "WeGlow is not a medical service.\n\n" +
                    "Information, automated analysis, skincare suggestions, and recommendations " +
                    "provided through WeGlow are intended for general informational and " +
                    "personal-care purposes and should not be treated as:\n" +
                    "• medical advice;\n" +
                    "• a medical diagnosis;\n" +
                    "• medical treatment; or\n" +
                    "• a substitute for consultation with a qualified healthcare professional.\n\n" +
                    "Seek appropriate professional advice regarding medical or dermatological " +
                    "concerns.",
            )

            LegalSection(
                title = "6. Product Recommendations",
                body = "Product recommendations may be generated using information such as your " +
                    "questionnaire responses and supported skin-analysis results.\n\n" +
                    "A recommendation does not guarantee that a product will be suitable, " +
                    "effective, or free from irritation or allergic reaction for a particular " +
                    "user.\n\n" +
                    "Users remain responsible for reviewing product ingredients, instructions, " +
                    "warnings, and suitability before using a product.",
            )

            LegalSection(
                title = "7. Environmental Information",
                body = "Weather, UV, humidity, air-quality, and other environmental information " +
                    "may originate from third-party data providers.\n\n" +
                    "Such information may be delayed, incomplete, or inaccurate and should not be " +
                    "treated as emergency, medical, or safety-critical information.",
            )

            LegalSection(
                title = "8. User Accounts",
                body = "You are responsible for maintaining appropriate control over access to " +
                    "your WeGlow account and for providing accurate information when using the " +
                    "application.\n\n" +
                    "You must not intentionally misuse another person's account or attempt to " +
                    "obtain unauthorized access to WeGlow systems or other users' information.",
            )

            LegalSection(
                title = "9. Photos",
                body = "You should only upload or submit photographs that you have the right to " +
                    "use.\n\n" +
                    "Profile photographs may be stored as described in the Privacy Policy.\n\n" +
                    "Photos used for current skin and hairstyle scan functionality are processed " +
                    "on-device, while certain derived analysis results may be stored as described " +
                    "in the Privacy Policy.",
            )

            LegalSection(
                title = "10. Saved Products, Cart and Purchases",
                body = "WeGlow may allow users to save products and add products to a Cart.\n\n" +
                    "In the current version, these features are for product discovery and " +
                    "organization.\n\n" +
                    "WeGlow does not currently provide completed checkout, payment processing, or " +
                    "order fulfilment through the application.\n\n" +
                    "Products, prices, availability, descriptions, and other catalog information " +
                    "may change.",
            )

            LegalSection(
                title = "11. Acceptable Use",
                body = "You agree not to:\n" +
                    "• attempt unauthorized access to WeGlow systems or accounts;\n" +
                    "• interfere with the operation or security of the application;\n" +
                    "• use WeGlow for unlawful purposes;\n" +
                    "• intentionally provide malicious content or attempt to compromise the " +
                    "service; or\n" +
                    "• misuse WeGlow's functionality in a way that harms other users or the " +
                    "service.",
            )

            LegalSection(
                title = "12. Availability and Changes",
                body = "WeGlow is an evolving application. We do not guarantee that every feature " +
                    "will always be available or operate without interruption or error.\n\n" +
                    "We may modify, suspend, replace, or discontinue functionality as the " +
                    "application develops.",
            )

            LegalSection(
                title = "13. Third-Party Services",
                body = "Certain WeGlow functionality depends on third-party services, including " +
                    "authentication, backend infrastructure, environmental-data providers, and " +
                    "other technical services.\n\n" +
                    "Their availability and operation may be outside WeGlow's direct control.",
            )

            LegalSection(
                title = "14. Disclaimer",
                body = "To the extent permitted by applicable law, WeGlow is provided on an " +
                    "\"as available\" basis.\n\n" +
                    "Automated predictions, recommendations, product information, hairstyle " +
                    "recommendations, environmental information, and other application-generated " +
                    "information may contain errors.\n\n" +
                    "Nothing in these Terms excludes rights or protections that cannot lawfully be " +
                    "excluded.",
            )

            LegalSection(
                title = "15. Privacy",
                body = "Your use of WeGlow is also subject to the WeGlow Privacy Policy, which " +
                    "describes how information is handled when you use the application.",
            )

            LegalSection(
                title = "16. Changes to These Terms",
                body = "We may update these Terms & Conditions as WeGlow develops.\n\n" +
                    "Updated Terms may be made available through the application with a revised " +
                    "effective date.\n\n" +
                    "Where legally required, additional notice or consent may be requested.",
            )

            LegalSection(
                title = "17. Contact",
                body = "Questions regarding these Terms may be sent to:\n\n" +
                    "WeGlow\n" +
                    "team03weglow@gmail.com",
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}
