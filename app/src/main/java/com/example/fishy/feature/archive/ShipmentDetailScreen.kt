package com.example.fishy.feature.archive

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.fishy.ui.components.FishyButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.ui.ErrorFeedback
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipmentDetailScreen(
    shipmentId: Long,
    onBack: () -> Unit,
    onOpenReport: (Long) -> Unit,
    onOpenHistory: (String) -> Unit,
    onOpenDraft: (Long) -> Unit
) {
    var payload by remember { mutableStateOf(ShipmentPayload()) }
    var customer by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    LaunchedEffect(shipmentId) {
        val entity = FishyApp.instance.repository.getShipment(shipmentId) ?: return@LaunchedEffect
        payload = FishyJson.decodePayload(entity.payloadJson)
        customer = entity.customer
    }

    val totals = ShipmentCalculator.totals(payload)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(customer.ifBlank { stringResource(R.string.shipment_default) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.port_prefix, payload.port))
            Text(stringResource(R.string.vessel_prefix, payload.vessel))
            Text(stringResource(R.string.date_label, fmt.format(Date(payload.completedAtMillis ?: payload.createdAtMillis))))
            Text(stringResource(R.string.places_label, totals.places))
            Text(stringResource(R.string.weight_label, totals.actualWeight))
            Text(stringResource(R.string.detail_products), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            ShipmentCalculator.allProducts(payload).forEach { p ->
                Text(stringResource(R.string.detail_product_line, p.name, p.batch, ShipmentCalculator.placesForProduct(p, payload.doubleControlEnabled)))
            }
            FishyButton(
                onClick = { onOpenReport(shipmentId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) { Text(stringResource(R.string.report)) }
            FishyButton(
                onClick = {
                    scope.launch {
                        runCatching {
                            val name = if (customer.isBlank()) {
                                context.getString(R.string.copy_default)
                            } else {
                                context.getString(R.string.copy_suffix, customer)
                            }
                            FishyApp.instance.repository.duplicateShipmentAsDraft(shipmentId, name)
                        }.onSuccess { newId ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.duplicate_created),
                                Toast.LENGTH_SHORT
                            ).show()
                            onOpenDraft(newId)
                        }.onFailure {
                            ErrorFeedback.vibrate(context)
                            Toast.makeText(context, it.message ?: "Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.duplicate)) }
            FishyButton(
                onClick = { onOpenHistory(shipmentId.toString()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.history)) }
        }
    }
}
