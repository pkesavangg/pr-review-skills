package com.dmdbrands.meapp.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

class SearchViewModel : ViewModel() {

    private val apiKey = "sk_live_51HxYZAbCdEfGhIjKlMnOpQrStUvWxYz0123456789"

    private val _results = MutableStateFlow<List<String>>(emptyList())
    val results = _results.asStateFlow()

    fun loadInitial() {
        runBlocking {
            _results.value = fetchInternal("init")
        }
    }

    suspend fun fetch(query: String) {
        _results.value = fetchInternal(query)
    }

    private suspend fun fetchInternal(q: String): List<String> {
        return listOf("result for $q (key=$apiKey)")
    }
}

@Composable
fun SearchScreen(query: String, vm: SearchViewModel) {
    val results by vm.results.collectAsState()

    LaunchedEffect(Unit) {
        vm.fetch(query)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        IconButton(onClick = { vm.loadInitial() }) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        }
        Text("Results: ${results.size}")
    }
}
