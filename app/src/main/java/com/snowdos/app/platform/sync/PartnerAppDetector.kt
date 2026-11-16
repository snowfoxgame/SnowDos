package com.snowdos.app.platform.sync

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.snowdos.app.core.common.DispatcherProvider
import com.snowdos.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class PartnerPackageInspection(
    val isInstalled: Boolean,
    val isEnabled: Boolean,
    val sha256Digests: Set<String>
)

internal fun evaluatePartnerTrust(
    ownDigests: Set<String>,
    partner: PartnerPackageInspection?
): Boolean {
    if (partner == null) return false
    if (!partner.isInstalled || !partner.isEnabled) return false
    if (ownDigests.isEmpty() || partner.sha256Digests.isEmpty()) return false
    return ownDigests.any { it in partner.sha256Digests }
}

/**
 * Checks whether the partner app (`com.snowsplit.app`) is installed, enabled, and signed with the
 * exact same SHA-256 certificate digest, persisting the live connection flag in [SettingsRepository].
 */
@Singleton
class PartnerAppDetector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    private val _partnerConnected = MutableStateFlow(false)
    val partnerConnected: StateFlow<Boolean> = _partnerConnected.asStateFlow()

    @Volatile
    var isProcessInForeground: Boolean = false

    fun isTrustedPartnerPackage(packageName: String?): Boolean {
        if (packageName != PartnerAppConfig.PARTNER_PACKAGE) return false
        val ownDigests = readPackageSha256Digests(PartnerAppConfig.OWN_PACKAGE)
        val partner = inspectPackage(PartnerAppConfig.PARTNER_PACKAGE)
        return evaluatePartnerTrust(ownDigests = ownDigests, partner = partner)
    }

    suspend fun refreshPartnerStatus(): Boolean = withContext(dispatcherProvider.io) {
        val trusted = isTrustedPartnerPackage(PartnerAppConfig.PARTNER_PACKAGE)
        _partnerConnected.value = trusted
        settingsRepository.setPartnerAppConnected(trusted)
        trusted
    }

    private fun inspectPackage(packageName: String): PartnerPackageInspection? {
        val pm = context.packageManager
        val info = getPackageInfoWithSignatures(pm, packageName) ?: return null
        val enabled = info.applicationInfo?.enabled ?: false
        return PartnerPackageInspection(
            isInstalled = true,
            isEnabled = enabled,
            sha256Digests = extractSha256Digests(info)
        )
    }

    private fun readPackageSha256Digests(packageName: String): Set<String> {
        val info = getPackageInfoWithSignatures(context.packageManager, packageName) ?: return emptySet()
        return extractSha256Digests(info)
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfoWithSignatures(pm: PackageManager, packageName: String): PackageInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun extractSha256Digests(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            packageInfo.signatures
        } ?: return emptySet()

        val md = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig ->
            md.reset()
            md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
