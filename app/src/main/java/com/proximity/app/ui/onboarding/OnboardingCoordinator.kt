// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.onboarding

import com.proximity.app.data.OperatorCatalog
import com.proximity.app.data.UserPreferencesStore

class OnboardingCoordinator(private val preferencesStore: UserPreferencesStore) {

    fun completeOnboarding(
        bankId: String,
        primarySimId: String
    ) {
        val isJio = OperatorCatalog.isJio(primarySimId)
        preferencesStore.update {
            it.copy(
                setupCompleted = true,
                selectedBankId = bankId,
                primarySimCarrierId = primarySimId,
                selectedOperator = primarySimId,
                ussdTestPassed = if (isJio) false else it.ussdTestPassed
            )
        }
    }
}
