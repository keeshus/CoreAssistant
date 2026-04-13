package nl.codeinfinity.coreassistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open Source Licenses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "CoreAssistant is made possible by the following open source projects:",
                style = MaterialTheme.typography.bodyLarge
            )

            LicenseGroup(
                title = "Core Application",
                items = listOf(
                    LicenseData(
                        "CoreAssistant (MIT)",
                        "Copyright (c) 2024 Kees Hus\n\nLicensed under the MIT License."
                    ),
                    LicenseData(
                        "Android Jetpack Libraries",
                        "Licensed under the Apache License, Version 2.0."
                    )
                )
            )

            LicenseGroup(
                title = "Networking & AI",
                items = listOf(
                    LicenseData(
                        "Retrofit / OkHttp",
                        "Copyright 2013 Square, Inc.\n\nLicensed under the Apache License, Version 2.0."
                    ),
                    LicenseData(
                        "Google Gemini API",
                        "Powered by Google Gemini."
                    )
                )
            )

            LicenseGroup(
                title = "UI & Rendering",
                items = listOf(
                    LicenseData(
                        "Coil",
                        "Copyright 2023 Coil Contributors\n\nLicensed under the Apache License, Version 2.0."
                    ),
                    LicenseData(
                        "Multiplatform Markdown Renderer",
                        "Copyright 2022 Mike Penz\n\nLicensed under the Apache License, Version 2.0."
                    )
                )
            )

            LicenseGroup(
                title = "Security & Data",
                items = listOf(
                    LicenseData(
                        "SQLCipher / Zetetic",
                        "Copyright (c) 2008-2024 Zetetic LLC\n\nLicensed under the BSD-style license."
                    ),
                    LicenseData(
                        "Room Persistence Library",
                        "Licensed under the Apache License, Version 2.0."
                    )
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

data class LicenseData(val name: String, val summary: String)

@Composable
fun LicenseGroup(title: String, items: List<LicenseData>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        items.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
