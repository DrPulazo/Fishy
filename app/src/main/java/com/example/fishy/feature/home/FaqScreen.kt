package com.example.fishy.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.ui.components.AccordionCard
import com.example.fishy.ui.components.ColumnScrollIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(
    onBack: () -> Unit
) {
    val items = listOf(
        R.string.faq_transport_title to R.string.faq_transport_body,
        R.string.faq_delete_pallets_title to R.string.faq_delete_pallets_body,
        R.string.faq_double_control_title to R.string.faq_double_control_body,
        R.string.faq_forecast_title to R.string.faq_forecast_body,
        R.string.faq_batch_title to R.string.faq_batch_body,
        R.string.faq_gross_title to R.string.faq_gross_body,
        R.string.faq_fab_title to R.string.faq_fab_body,
        R.string.faq_simple_counter_title to R.string.faq_simple_counter_body,
        R.string.faq_input_guard_title to R.string.faq_input_guard_body,
        R.string.faq_autospacing_title to R.string.faq_autospacing_body,
        R.string.faq_prep_checklist_title to R.string.faq_prep_checklist_body,
        R.string.faq_schedule_reminders_title to R.string.faq_schedule_reminders_body,
        R.string.faq_shipment_checklist_title to R.string.faq_shipment_checklist_body,
        R.string.faq_shipment_checklist_reminders_title to R.string.faq_shipment_checklist_reminders_body,
        R.string.faq_start_schedule_title to R.string.faq_start_schedule_body,
        R.string.faq_drafts_title to R.string.faq_drafts_body,
        R.string.faq_history_title to R.string.faq_history_body
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.faq_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        val faqScroll = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 8.dp)
                    .verticalScroll(faqScroll)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { (titleRes, bodyRes) ->
                    AccordionCard(
                        title = stringResource(titleRes),
                        initiallyExpanded = false
                    ) {
                        Text(
                            text = stringResource(bodyRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            ColumnScrollIndicator(
                scrollState = faqScroll,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
            )
        }
    }
}
