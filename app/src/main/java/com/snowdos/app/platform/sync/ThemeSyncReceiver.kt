package com.snowdos.app.platform.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.snowdos.app.core.common.DispatcherProvider
import com.snowdos.app.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives signed appearance sync broadcasts from SnowSplit (`com.snowsplit.app`) and applies
 * incoming updates under strict loop-prevention and trust checks.
 */
@AndroidEntryPoint
class ThemeSyncReceiver : BroadcastReceiver() {

    @Inject
    lateinit var partnerAppDetector: PartnerAppDetector

    @Inject
    lateinit var themeSyncManager: ThemeSyncManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != PartnerAppConfig.SYNC_ACTION) return
        val payloadJson = intent.getStringExtra(PartnerAppConfig.EXTRA_PAYLOAD_JSON) ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + dispatcherProvider.io).launch {
            try {
                val trusted = partnerAppDetector.refreshPartnerStatus()
                if (!trusted) return@launch

                val settings = settingsRepository.observeSettings().first()
                if (!settings.crossAppSyncEnabled || !settings.partnerAppConnected) return@launch

                val payload = SnowAppearanceSyncPayload.fromJson(payloadJson) ?: return@launch
                if (payload.sourcePackage != PartnerAppConfig.PARTNER_PACKAGE) return@launch

                themeSyncManager.applyIncomingPayload(payload)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
