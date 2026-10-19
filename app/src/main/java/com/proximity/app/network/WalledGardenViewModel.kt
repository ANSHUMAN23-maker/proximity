// Copyright © 2026 Proximity. All rights reserved.
// Proprietary and confidential — No license granted.
// See LICENSE file for full terms.

package com.proximity.app.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WalledGardenViewModel(
    private val detector: WalledGardenDetector
) : ViewModel() {

    private val _state = MutableStateFlow<WalledGardenResult>(WalledGardenResult.Connected)
    val state: StateFlow<WalledGardenResult> = _state.asStateFlow()

    private val checkMutex = Mutex()
    private var lastCheckTimeMs = 0L
    private val cacheTtlMs = 45_000L // 45 seconds

    private var runningJob: kotlinx.coroutines.Job? = null

    fun check(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckTimeMs <= cacheTtlMs) return

        if (runningJob?.isActive == true && !force) return

        runningJob?.cancel()
        runningJob = viewModelScope.launch {
            checkMutex.withLock {
                if (!force && System.currentTimeMillis() - lastCheckTimeMs <= cacheTtlMs) return@withLock
                _state.value = detector.check()
                lastCheckTimeMs = System.currentTimeMillis()
            }
        }
    }

    class Factory(private val detector: WalledGardenDetector) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WalledGardenViewModel(detector) as T
        }
    }
}
