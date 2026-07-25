package com.example.fishy.feature.report

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.data.settings.FishySettings
import com.example.fishy.domain.model.ShipmentEventType
import com.example.fishy.domain.report.ReportGenerator
import com.example.fishy.domain.report.ReportTemplate
import com.example.fishy.ui.components.FishyButton
import com.example.fishy.ui.components.FishySentenceKeyboardOptions
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    shipmentId: Long,
    onBack: () -> Unit
) {
    val repo = FishyApp.instance.repository
    val settingsRepo = FishyApp.instance.settingsRepository
    val settings by settingsRepo.settings.collectAsState(initial = FishySettings())
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var payload by remember { mutableStateOf(com.example.fishy.domain.model.ShipmentPayload()) }
    var templateBody by remember { mutableStateOf(ReportTemplate.defaultBody()) }
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var displayText by remember { mutableStateOf("") }

    // Wait for DataStore (not collectAsState initial defaults) so auto-spaces aren't flaky.
    LaunchedEffect(
        shipmentId,
        settings.effectiveAutoSpaceContainers,
        settings.effectiveAutoSpaceVehicles,
        settings.effectiveThousandsSeparator
    ) {
        if (editing) return@LaunchedEffect
        val s = settingsRepo.settings.first()
        val entity = repo.getShipment(shipmentId) ?: return@LaunchedEffect
        val loaded = FishyJson.decodePayload(entity.payloadJson)
        val tpl = ReportTemplate.defaultBody()
        val text = ReportGenerator.generate(
            payload = loaded,
            templateBody = tpl,
            formatContainerSpaces = s.effectiveAutoSpaceContainers,
            formatVehicleSpaces = s.effectiveAutoSpaceVehicles,
            thousandsSeparator = s.effectiveThousandsSeparator
        )
        ensureActive()
        payload = loaded
        templateBody = tpl
        displayText = text
        editText = text
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.report)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(displayText))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                    }
                    IconButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, displayText)
                        }
                        context.startActivity(Intent.createChooser(share, context.getString(R.string.export_txt)))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (editing) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    keyboardOptions = FishySentenceKeyboardOptions
                )
                TextButton(onClick = {
                    scope.launch {
                        val updated = payload.copy(editedReportText = editText)
                        repo.updateArchivedShipment(shipmentId, updated)
                        repo.log(
                            shipmentId.toString(),
                            ShipmentEventType.REPORT_EDITED,
                            context.getString(R.string.history_msg_report_edited)
                        )
                        payload = updated
                        displayText = editText
                        editing = false
                    }
                }) { Text(stringResource(R.string.save)) }
                TextButton(onClick = {
                    scope.launch {
                        val s = settingsRepo.settings.first()
                        val regenerated = ReportGenerator.generate(
                            payload = payload.copy(editedReportText = null),
                            templateBody = templateBody,
                            formatContainerSpaces = s.effectiveAutoSpaceContainers,
                            formatVehicleSpaces = s.effectiveAutoSpaceVehicles,
                            thousandsSeparator = s.effectiveThousandsSeparator
                        )
                        ensureActive()
                        editText = regenerated
                        displayText = regenerated
                        val updated = payload.copy(editedReportText = null)
                        repo.updateArchivedShipment(shipmentId, updated)
                        payload = updated
                        editing = false
                    }
                }) { Text(stringResource(R.string.reset_report)) }
            } else {
                SelectionContainer(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(displayText)
                }
                FishyButton(
                    onClick = {
                        editing = true
                        editText = displayText
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.edit_report)) }
            }
        }
    }
}
