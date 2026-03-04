package com.example.aerogcsclone.authentication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAndConditionsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Conditions", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF23272A)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF23272A))
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pavaman Aviation – Drone Management App",
                color = Color(0xFF87CEEB),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle("1. Acceptance of Terms")
            SectionBody("By installing or using this App, you agree to these Terms & Conditions. If you disagree, discontinue use immediately. This App is intended solely for use within India under DGCA regulations.")

            SectionTitle("2. Eligibility & User Responsibilities")
            SectionBody(
                "To use this App, you must:\n" +
                "• Be at least 18 years of age\n" +
                "• Hold a valid Remote Pilot Certificate (RPC) for commercial operations\n" +
                "• Ensure your drone has a valid Unique Identification Number (UIN) registered on the DigitalSky Platform\n" +
                "• Comply with all DGCA Drone Rules 2021 and the Bharatiya Vayuyan Adhiniyam, 2024"
            )

            SectionTitle("3. Data We Collect")
            SectionBody(
                "We collect the following data to operate the App and comply with DGCA regulations:\n" +
                "• Personal details: name, email, mobile number\n" +
                "• Drone details: UIN, make/model, serial number, firmware version\n" +
                "• Real-time GPS location of your device and drone during active flights\n" +
                "• Flight logs: timestamps, duration, distance, altitude, speed, battery status\n" +
                "• Device info: Android device ID, OS version, IP address\n" +
                "• Telemetry & sensor data collected automatically during flight\n\n" +
                "We practice data minimisation — only data necessary for app functionality and legal compliance is collected."
            )

            SectionTitle("4. Location Data")
            SectionBody(
                "This App collects precise, real-time GPS location data during flight operations. This is mandatory for DGCA NPNT compliance and airspace safety. Disabling location access will prevent the App from functioning. Background location permission ('Allow all the time') is required only when a flight is active — we do not collect location at any other time."
            )

            SectionTitle("5. DGCA Compliance & DigitalSky Integration")
            SectionBody(
                "The App enforces the following DGCA requirements:\n" +
                "• NPNT (No Permission No Takeoff): Flights are automatically blocked without a valid DGCA permission artefact\n" +
                "• Airspace zones: Green (no permission < 400 ft), Yellow (permission required), Red (no operations permitted)\n" +
                "• Altitude limit: Maximum 400 ft / 120 m AGL in Green Zones\n" +
                "• UIN and RPC verification before each flight session\n\n" +
                "Flight data shared with the DigitalSky Platform is governed by DGCA's own privacy terms."
            )

            SectionTitle("6. Pilot Obligations")
            SectionBody(
                "You are personally responsible for:\n" +
                "• Obtaining required airspace permissions before every flight in controlled zones\n" +
                "• Maintaining Visual Line of Sight (VLOS) at all times unless BVLOS permission is granted\n" +
                "• Not flying over hospitals, schools, crowds, government buildings, or private property without authorization\n" +
                "• Obtaining consent before capturing identifiable images or videos of individuals\n" +
                "• Reporting accidents, incidents, or near-misses to DGCA as required by law\n" +
                "• Not sharing App credentials with unauthorized persons\n\n" +
                "Violations of DGCA regulations may result in suspension of pilot privileges, fines, or criminal liability. The App Developer bears no liability for pilot violations."
            )

            SectionTitle("7. Data Storage & Security")
            SectionBody(
                "All data is stored securely on Amazon Web Services (AWS) infrastructure:\n" +
                "• Encryption at rest using AES-256; in transit using TLS 1.2/1.3 (HTTPS)\n" +
                "• Role-based access controls (RBAC) limit access to authorized personnel only\n" +
                "• AWS complies with ISO 27001 and SOC 2 certifications\n\n" +
                "Data may be processed in AWS data centers outside India (e.g., Singapore, US East). By using the App, you consent to this transfer. We will migrate to AWS India (ap-south-1) region to the extent possible in line with DPDP Act 2023 data localisation requirements."
            )

            SectionTitle("8. Data Retention")
            SectionBody(
                "All flight logs, location data, telemetry, and operational records are retained indefinitely as part of a mandatory regulatory audit trail required by DGCA. You cannot request deletion of flight or operational data — doing so would compromise the integrity of the audit trail and may violate applicable law."
            )

            SectionTitle("9. Third-Party Sharing")
            SectionBody(
                "We do not sell your data. We may share data only with:\n" +
                "• DGCA / DigitalSky Platform — as mandated by the Drone Rules 2021\n" +
                "• Amazon Web Services (AWS) — as our cloud hosting provider (data processor)\n" +
                "• Law enforcement or courts — where required by law or court order\n" +
                "• Emergency responders — in the event of a reported drone accident\n\n" +
                "You cannot opt out of data sharing with DGCA — this is a mandatory regulatory obligation."
            )

            SectionTitle("10. Your Rights (DPDP Act 2023)")
            SectionBody(
                "As a Data Principal under India's DPDP Act 2023, you have the right to:\n" +
                "• Access a copy of your personal data\n" +
                "• Correct inaccurate or incomplete data\n" +
                "• Withdraw consent for future collection (note: this disables flight features)\n" +
                "• Lodge grievances with our Grievance Officer\n" +
                "• Nominate a representative to act on your behalf\n\n" +
                "To exercise these rights, email pavaman.official@gmail.com with the subject 'DPDP Rights Request'. We respond within 30 days."
            )

            SectionTitle("11. Disclaimer & Liability")
            SectionBody(
                "The App is provided 'as is' without warranties of uninterrupted availability or error-free operation. We are not liable for:\n" +
                "• Accidents, property damage, or injuries arising from drone operations\n" +
                "• Violations of DGCA regulations by pilots or operators\n" +
                "• Third-party service failures, including AWS outages\n" +
                "• Inaccuracies in geofencing data sourced from DGCA databases\n\n" +
                "We recommend all commercial operators obtain third-party liability insurance as advised by DGCA. Nothing herein limits liability where restricted by Indian law."
            )

            SectionTitle("12. Children's Privacy")
            SectionBody(
                "This App is not intended for individuals under 18 years of age. We do not knowingly collect data from minors. If we become aware a minor has registered, we will immediately suspend their account and delete associated data."
            )

            SectionTitle("13. Data Breach Notification")
            SectionBody(
                "In the event of a data breach posing risk to your rights, we will notify affected users via email and in-app notification within 72 hours of becoming aware. We will also report to the Data Protection Board of India within the prescribed timeline and take immediate steps to contain the breach."
            )

            SectionTitle("14. Changes to These Terms")
            SectionBody(
                "We may update these Terms from time to time to reflect changes in law, DGCA regulations, or our practices. You will be notified via in-app notification and email. Continued use of the App after notification constitutes acceptance of the updated Terms."
            )

            SectionTitle("15. Contact & Grievance Officer")
            SectionBody(
                "For questions, concerns, or data requests:\n" +
                "Developer: Pavaman Aviation\n" +
                "Email: pavaman.official@gmail.com\n" +
                "Grievance Response Time: Acknowledgement within 48 hours; resolution within 30 days\n\n" +
                "If unsatisfied, you may escalate to the Data Protection Board of India once operational under the DPDP Act 2023."
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = title,
        color = Color(0xFF87CEEB),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun SectionBody(body: String) {
    Text(
        text = body,
        color = Color.White,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
}

