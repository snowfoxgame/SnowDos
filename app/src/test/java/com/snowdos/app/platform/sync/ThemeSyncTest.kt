package com.snowdos.app.platform.sync

import com.snowdos.app.data.local.BackgroundImageStorage
import com.snowdos.app.domain.model.AppSettings
import com.snowdos.app.domain.model.EightBitVariant
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class ThemeSyncTest {

    @Test
    fun payloadSerialization_roundTripsPresetAndCustomBackground() {
        val original = SnowAppearanceSyncPayload(
            schemaVersion = 1,
            sourcePackage = PartnerAppConfig.PARTNER_PACKAGE,
            syncId = "sync-uuid-1234",
            updatedAtEpochMillis = 1_760_000_123_456L,
            themeMode = ThemeMode.CUSTOM.name,
            presetId = "gaming",
            customAccentHex = "#12AB89",
            gamingAccentHex = "#00E5FF",
            eightBitVariant = EightBitVariant.PASTEL_BLUE.name,
            glassOpacity = 0.31f,
            reduceLiveBlur = true,
            hasCustomBackground = true,
            customBackgroundHash = "abc123def456",
            customBackgroundContentUri = "content://com.snowsplit.app.syncprovider/background"
        )

        val json = original.toJson()
        val parsed = SnowAppearanceSyncPayload.fromJson(json)

        assertNotNull(parsed)
        assertEquals(original.schemaVersion, parsed!!.schemaVersion)
        assertEquals(original.sourcePackage, parsed.sourcePackage)
        assertEquals(original.syncId, parsed.syncId)
        assertEquals(original.updatedAtEpochMillis, parsed.updatedAtEpochMillis)
        assertEquals(original.themeMode, parsed.themeMode)
        assertEquals(original.presetId, parsed.presetId)
        assertEquals(original.customAccentHex, parsed.customAccentHex)
        assertEquals(original.gamingAccentHex, parsed.gamingAccentHex)
        assertEquals(original.eightBitVariant, parsed.eightBitVariant)
        assertEquals(original.glassOpacity, parsed.glassOpacity, 0.001f)
        assertTrue(parsed.reduceLiveBlur)
        assertTrue(parsed.hasCustomBackground)
        assertEquals("abc123def456", parsed.customBackgroundHash)
        assertEquals("content://com.snowsplit.app.syncprovider/background", parsed.customBackgroundContentUri)
        assertEquals(ThemeMode.PRESET, parsed.toAppearanceConfig("file:///tmp/bg.jpg").mode)
    }

    @Test
    fun payloadDeserialization_returnsNullForMalformedJson() {
        assertNull(SnowAppearanceSyncPayload.fromJson(null))
        assertNull(SnowAppearanceSyncPayload.fromJson(""))
        assertNull(SnowAppearanceSyncPayload.fromJson("{invalid"))
        assertNull(SnowAppearanceSyncPayload.fromJson("""{"schemaVersion":1}"""))
    }

    @Test
    fun loopGuards_rejectOwnPackageDuplicateSyncIdAndStaleRevision() {
        val basePayload = samplePartnerPayload()

        val ownSourceDecision = ThemeSyncGuards.evaluateIncoming(
            payload = basePayload.copy(sourcePackage = PartnerAppConfig.OWN_PACKAGE),
            crossAppSyncEnabled = true,
            partnerAppConnected = true,
            lastAppliedSyncId = "other-id",
            lastSyncedRevision = 100L
        )
        assertEquals(SyncDecision.Ignore(RejectReason.OWN_OR_UNTRUSTED_SOURCE), ownSourceDecision)

        val untrustedSourceDecision = ThemeSyncGuards.evaluateIncoming(
            payload = basePayload.copy(sourcePackage = "com.evil.app"),
            crossAppSyncEnabled = true,
            partnerAppConnected = true,
            lastAppliedSyncId = "other-id",
            lastSyncedRevision = 100L
        )
        assertEquals(SyncDecision.Ignore(RejectReason.OWN_OR_UNTRUSTED_SOURCE), untrustedSourceDecision)

        val duplicateIdDecision = ThemeSyncGuards.evaluateIncoming(
            payload = basePayload,
            crossAppSyncEnabled = true,
            partnerAppConnected = true,
            lastAppliedSyncId = basePayload.syncId,
            lastSyncedRevision = 100L
        )
        assertEquals(SyncDecision.Ignore(RejectReason.DUPLICATE_SYNC_ID), duplicateIdDecision)

        val staleRevisionDecision = ThemeSyncGuards.evaluateIncoming(
            payload = basePayload.copy(updatedAtEpochMillis = 500L),
            crossAppSyncEnabled = true,
            partnerAppConnected = true,
            lastAppliedSyncId = "other-id",
            lastSyncedRevision = 500L
        )
        assertEquals(SyncDecision.Ignore(RejectReason.STALE_REVISION), staleRevisionDecision)
    }

    @Test
    fun loopGuards_rejectWhenSyncDisabledOrPartnerDisconnectedOrStateIdentical() {
        val payload = samplePartnerPayload(
            presetId = "winter",
            reduceLiveBlur = false,
            hasCustomBackground = false
        )

        assertEquals(
            SyncDecision.Ignore(RejectReason.SYNC_DISABLED),
            ThemeSyncGuards.evaluateIncoming(
                payload = payload,
                crossAppSyncEnabled = false,
                partnerAppConnected = true,
                lastAppliedSyncId = "old",
                lastSyncedRevision = 10L
            )
        )

        assertEquals(
            SyncDecision.Ignore(RejectReason.PARTNER_NOT_CONNECTED),
            ThemeSyncGuards.evaluateIncoming(
                payload = payload,
                crossAppSyncEnabled = true,
                partnerAppConnected = false,
                lastAppliedSyncId = "old",
                lastSyncedRevision = 10L
            )
        )

        val identicalCurrent = ThemeAppearanceConfig(
            mode = ThemeMode.PRESET,
            presetId = "winter",
            customAccentHex = payload.customAccentHex,
            gamingAccentHex = payload.gamingAccentHex,
            eightBitVariant = EightBitVariant.PASTEL_PINK,
            backgroundImageUri = null,
            glassOpacity = payload.glassOpacity
        )
        assertEquals(
            SyncDecision.Ignore(RejectReason.IDENTICAL_STATE),
            ThemeSyncGuards.evaluateIncoming(
                payload = payload,
                crossAppSyncEnabled = true,
                partnerAppConnected = true,
                lastAppliedSyncId = "old",
                lastSyncedRevision = 10L,
                currentAppearance = identicalCurrent,
                currentReduceLiveBlur = false,
                localBackgroundHash = null
            )
        )
    }

    @Test
    fun backgroundHashCheck_avoidsRedundantCopyAndClearsRemovedBackground() {
        assertEquals(
            BackgroundSyncAction.NO_CHANGE,
            ThemeSyncGuards.resolveBackgroundSyncAction(
                hasCustomBackground = true,
                remoteBackgroundHash = "deadbeef",
                remoteContentUri = "content://com.snowsplit.app.syncprovider/background",
                localBackgroundUri = "file:///data/user/0/com.snowdos.app/files/backgrounds/bg.jpg",
                localBackgroundHash = "deadbeef"
            )
        )

        assertEquals(
            BackgroundSyncAction.COPY_FROM_PARTNER,
            ThemeSyncGuards.resolveBackgroundSyncAction(
                hasCustomBackground = true,
                remoteBackgroundHash = "newhash",
                remoteContentUri = "content://com.snowsplit.app.syncprovider/background",
                localBackgroundUri = "file:///data/user/0/com.snowdos.app/files/backgrounds/bg.jpg",
                localBackgroundHash = "oldhash"
            )
        )

        assertEquals(
            BackgroundSyncAction.CLEAR_LOCAL,
            ThemeSyncGuards.resolveBackgroundSyncAction(
                hasCustomBackground = false,
                remoteBackgroundHash = null,
                remoteContentUri = null,
                localBackgroundUri = "file:///data/user/0/com.snowdos.app/files/backgrounds/bg.jpg",
                localBackgroundHash = "oldhash"
            )
        )

        val sha1 = BackgroundImageStorage.computeSha256Hex("snow-theme-image".encodeToByteArray())
        val sha2 = BackgroundImageStorage.computeSha256Hex("snow-theme-image".encodeToByteArray())
        assertEquals(64, sha1.length)
        assertEquals(sha1, sha2)
    }

    @Test
    fun partnerDetector_transitionsCleanlyOnInstallSignatureAndUninstall() {
        val ownDigests = setOf("aaa111")

        // Installed, enabled, same SHA-256 signature -> connected = true
        assertTrue(
            evaluatePartnerTrust(
                ownDigests = ownDigests,
                partner = PartnerPackageInspection(
                    isInstalled = true,
                    isEnabled = true,
                    sha256Digests = setOf("aaa111")
                )
            )
        )

        // Different certificate -> untrusted
        assertFalse(
            evaluatePartnerTrust(
                ownDigests = ownDigests,
                partner = PartnerPackageInspection(
                    isInstalled = true,
                    isEnabled = true,
                    sha256Digests = setOf("bbb222")
                )
            )
        )

        // Disabled -> disconnected
        assertFalse(
            evaluatePartnerTrust(
                ownDigests = ownDigests,
                partner = PartnerPackageInspection(
                    isInstalled = true,
                    isEnabled = false,
                    sha256Digests = setOf("aaa111")
                )
            )
        )

        // Uninstalled -> disconnected = false
        assertFalse(
            evaluatePartnerTrust(
                ownDigests = ownDigests,
                partner = null
            )
        )
    }

    @Test
    fun partnerConfig_usesMatchingBroadcastExtraKeyForSnowSplit() {
        assertEquals("payload_json", PartnerAppConfig.EXTRA_PAYLOAD_JSON)
    }

    @Test
    fun outgoingSnapshotMeta_usesImmediateLocalRevisionBeforeDebounce() {
        val syncedFromPartner = AppSettings(
            lastSyncedRevision = 5_000L,
            lastAppliedSyncId = "partner-sync-old",
            localModifiedAtMillis = 5_000L,
            lastPublishedSyncId = "partner-sync-old"
        )

        val immediateRevision = ThemeSyncManager.nextMonotonicRevision(
            settings = syncedFromPartner,
            nowMillis = 4_900L // even if clock is behind partner revision, monotonic +1 wins
        )
        assertEquals(5_001L, immediateRevision)

        val updatedBeforeDebounce = syncedFromPartner.copy(
            localModifiedAtMillis = immediateRevision,
            lastPublishedSyncId = "local-immediate-uuid"
        )
        val (outgoingSyncId, outgoingRevision) = ThemeSyncManager.resolveEffectiveOutgoingMeta(updatedBeforeDebounce)
        assertEquals("local-immediate-uuid", outgoingSyncId)
        assertEquals(5_001L, outgoingRevision)
    }

    @Test
    fun foregroundPullSlot_deduplicatesBackToBackOnStartAndOnResume() {
        val lastPullMs = AtomicLong(Long.MIN_VALUE)
        assertTrue(ThemeSyncManager.tryAcquirePullSlot(lastPullMs, nowMs = 10_000L, cooldownMs = 1_000L))
        // Rapid onResume 25ms after onStart must be skipped
        assertFalse(ThemeSyncManager.tryAcquirePullSlot(lastPullMs, nowMs = 10_025L, cooldownMs = 1_000L))
        // After cooldown elapses, pull is allowed again
        assertTrue(ThemeSyncManager.tryAcquirePullSlot(lastPullMs, nowMs = 11_050L, cooldownMs = 1_000L))
    }

    private fun samplePartnerPayload(
        presetId: String = "gaming",
        reduceLiveBlur: Boolean = false,
        hasCustomBackground: Boolean = false
    ) = SnowAppearanceSyncPayload(
        schemaVersion = 1,
        sourcePackage = PartnerAppConfig.PARTNER_PACKAGE,
        syncId = "partner-sync-99",
        updatedAtEpochMillis = 1_000L,
        themeMode = ThemeMode.PRESET.name,
        presetId = presetId,
        customAccentHex = "#6750A4",
        gamingAccentHex = "#00E5FF",
        eightBitVariant = EightBitVariant.PASTEL_PINK.name,
        glassOpacity = 0.24f,
        reduceLiveBlur = reduceLiveBlur,
        hasCustomBackground = hasCustomBackground
    )
}
