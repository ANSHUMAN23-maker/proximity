// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.shell

import androidx.lifecycle.ViewModel
import com.proximity.app.R
import com.proximity.app.data.UserPreferencesStore
import com.proximity.app.data.UserSettings
import com.proximity.app.ui.components.ShellNavTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeScreenModel(
    private val preferencesStore: UserPreferencesStore,
    val greetingIndex: Int
) : ViewModel() {

    val settingsFlow: StateFlow<UserSettings> = preferencesStore.settingsFlow

    private val _selectedTab = MutableStateFlow(ShellNavTab.HOME)
    val selectedTab: StateFlow<ShellNavTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: ShellNavTab) {
        _selectedTab.value = tab
    }

    fun markNotificationsPromptedOnce() {
        preferencesStore.update { it.copy(promptedNotificationPermissionOnce = true) }
    }

    fun resolveGreetingStringRes(): Int {
        val greetings = listOf(
            R.string.home_greeting_1,
            R.string.home_greeting_2,
            R.string.home_greeting_3,
            R.string.home_greeting_4,
            R.string.home_greeting_5,
            R.string.home_greeting_6,
            R.string.home_greeting_7,
            R.string.home_greeting_8,
            R.string.home_greeting_9
        )
        return greetings[greetingIndex.coerceIn(0, greetings.lastIndex)]
    }
}
