package com.example.fishy.feature.templates

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.local.entity.DictionaryEntity
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.ui.components.CenteredDialogTitle
import com.example.fishy.ui.components.CenteredEmptyBody
import com.example.fishy.ui.components.ConfirmDeleteDialog
import com.example.fishy.ui.components.DialogCancelConfirmActions
import com.example.fishy.ui.components.EmptyListPlaceholder
import com.example.fishy.ui.components.FabContentClearance
import com.example.fishy.ui.components.FabEndInsetForScrollbar
import com.example.fishy.ui.components.FishyFloatingActionButton
import com.example.fishy.ui.components.FishySentenceKeyboardOptions
import com.example.fishy.ui.components.HintedScrollableTabs
import com.example.fishy.ui.components.LazyListScrollIndicator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TemplatesScreen(onBack: () -> Unit) {
    val types = listOf(
        DictionaryType.CUSTOMER to stringResource(R.string.dict_customers),
        DictionaryType.PORT to stringResource(R.string.dict_ports),
        DictionaryType.VESSEL to stringResource(R.string.dict_vessels),
        DictionaryType.PRODUCT to stringResource(R.string.dict_products),
        DictionaryType.MANUFACTURER to stringResource(R.string.dict_manufacturers)
    )
    var tab by remember { mutableIntStateOf(0) }
    val repo = FishyApp.instance.repository
    val dictType = types[tab].first
    var items by remember { mutableStateOf<List<DictionaryEntity>?>(null) }
    LaunchedEffect(dictType) {
        items = null
        repo.observeDictionary(dictType).collect { items = it }
    }
    val scope = rememberCoroutineScope()
    var dialogValue by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<DictionaryEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DictionaryEntity?>(null) }
    val listState = rememberLazyListState()

    val emptyEmoji = when (dictType) {
        DictionaryType.CUSTOMER -> "💼"
        DictionaryType.PORT -> "⚓️"
        DictionaryType.VESSEL -> "🛳️"
        DictionaryType.PRODUCT -> "🐟"
        DictionaryType.MANUFACTURER -> "🏭"
    }
    val emptyTitle = when (dictType) {
        DictionaryType.CUSTOMER -> stringResource(R.string.templates_empty_customers)
        DictionaryType.PORT -> stringResource(R.string.templates_empty_ports)
        DictionaryType.VESSEL -> stringResource(R.string.templates_empty_vessels)
        DictionaryType.PRODUCT -> stringResource(R.string.templates_empty_products)
        DictionaryType.MANUFACTURER -> stringResource(R.string.templates_empty_manufacturers)
    }

    fun openAddEditor() {
        editing = null
        dialogValue = ""
        showEditor = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_templates)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FishyFloatingActionButton(
                onClick = { openAddEditor() },
                modifier = Modifier.padding(end = FabEndInsetForScrollbar)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }
    ) { padding ->
        val list = items
        CenteredEmptyBody(
            isEmpty = list != null && list.isEmpty(),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            topChrome = {
                HintedScrollableTabs(
                    selectedIndex = tab,
                    titles = types.map { it.second },
                    onSelect = { tab = it }
                )
            },
            empty = {
                EmptyListPlaceholder(
                    emoji = emptyEmoji,
                    title = emptyTitle,
                    hint = stringResource(R.string.templates_empty_hint)
                )
            }
        ) {
            if (list == null) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(bottom = FabContentClearance)
                    ) {
                        items(list, key = { it.id }) { item ->
                            Card(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        editing = item
                                        dialogValue = item.value
                                        showEditor = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.value, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        editing = item
                                        dialogValue = item.value
                                        showEditor = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                                    }
                                    IconButton(onClick = { pendingDelete = item }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                    LazyListScrollIndicator(
                        listState = listState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }

    if (showEditor) {
        val addTitle = when (types[tab].first) {
            DictionaryType.CUSTOMER -> stringResource(R.string.add_customer)
            DictionaryType.PORT -> stringResource(R.string.add_port_dict)
            DictionaryType.VESSEL -> stringResource(R.string.add_vessel_dict)
            DictionaryType.PRODUCT -> stringResource(R.string.add_product_dict)
            DictionaryType.MANUFACTURER -> stringResource(R.string.add_manufacturer)
        }
        AlertDialog(
            onDismissRequest = { showEditor = false },
            containerColor = MaterialTheme.colorScheme.background,
            title = {
                CenteredDialogTitle(
                    if (editing == null) addTitle else stringResource(R.string.edit)
                )
            },
            text = {
                OutlinedTextField(
                    value = dialogValue,
                    onValueChange = { dialogValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = FishySentenceKeyboardOptions
                )
            },
            confirmButton = {
                DialogCancelConfirmActions(
                    onCancel = { showEditor = false },
                    onConfirm = {
                        scope.launch {
                            val entity = editing?.copy(value = dialogValue)
                                ?: DictionaryEntity(type = types[tab].first.key, value = dialogValue)
                            repo.upsertDictionary(entity)
                            dialogValue = ""
                            editing = null
                            showEditor = false
                        }
                    },
                    confirmText = stringResource(R.string.save)
                )
            }
        )
    }

    pendingDelete?.let { item ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_dict_title),
            message = stringResource(R.string.delete_dict_msg, item.value),
            onConfirm = {
                scope.launch {
                    repo.deleteDictionary(item.id)
                    pendingDelete = null
                }
            },
            onDismiss = { pendingDelete = null }
        )
    }
}
