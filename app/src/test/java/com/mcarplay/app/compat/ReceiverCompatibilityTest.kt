package com.mcarplay.app.compat

import com.mcarplay.app.iap2.ReceiverProfile
import com.mcarplay.app.net.AirPlayFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverCompatibilityTest {
    private val winPlay =
        ReceiverProfile.Signals(
            name = "WinPlay",
            model = "WinPlay",
            manufacturer = "Roylyl",
            firmwareVersion = "1.2.4",
            features = AirPlayFeatures.HK_PAIRING_AND_ENCRYPT,
            externalAccessoryProtocols = setOf("io.github.roylyl.winplay"),
        )
    private val diPlay =
        ReceiverProfile.Signals(
            name = "DiPlay",
            model = "custom-hu",
            manufacturer = "custom-maker",
            firmwareVersion = "0.1.0",
            externalAccessoryProtocols = setOf("com.shilapi.xcertplay"),
        )
    private val mib3 =
        ReceiverProfile.Signals(
            model = "AUDI 5163",
            manufacturer = "DELPHI",
            sendIds = setOf(0x4301, 0xea03),
            receiveIds = setOf(0x5703, 0x4e09, 0x4e0b, 0x4e0d, 0x4e0e),
        )

    @Test
    fun receiverMatrixKeepsRulesSeparate() {
        val windows = ReceiverCompatibility.resolve(winPlay)
        assertEquals(ReceiverCompatibility.Family.WINPLAY, windows.family)
        assertEquals(ReceiverCompatibility.BluetoothRole.NETWORK_HANDOFF, windows.bluetoothRole)
        assertTrue(windows.requiresRealBluetoothIdentity)
        assertFalse(windows.allowBluetoothRetireRetry)
        assertTrue(windows.rules.contains("winplay.1.2.4.network-handoff"))
        val windows125 = ReceiverCompatibility.resolve(winPlay.copy(firmwareVersion = "1.2.5"))
        assertEquals(ReceiverCompatibility.BluetoothRole.NETWORK_HANDOFF, windows125.bluetoothRole)
        assertTrue(windows125.requiresRealBluetoothIdentity)
        assertTrue(windows125.rules.contains("winplay.1.2.5.network-handoff"))
        assertFalse(windows125.rules.contains("winplay.unverified-firmware"))
        val android = ReceiverCompatibility.resolve(diPlay)
        assertEquals(ReceiverCompatibility.Family.DIPLAY, android.family)
        assertTrue(android.allowBluetoothRetireRetry)
        assertEquals(ReceiverCompatibility.Family.CLASSIC_MIB3, ReceiverCompatibility.resolve(mib3).family)
        assertFalse(ReceiverCompatibility.resolve(mib3).allowBluetoothRetireRetry)
    }

    @Test
    fun namesAndUnverifiedVersionsCannotEnableDestructiveExceptions() {
        val spoofed = ReceiverCompatibility.resolve(diPlay.copy(externalAccessoryProtocols = emptySet()))
        assertEquals(ReceiverCompatibility.Family.GENERIC, spoofed.family)
        assertFalse(spoofed.allowBluetoothRetireRetry)
        assertFalse(ReceiverCompatibility.resolve(diPlay.copy(firmwareVersion = "0.2.0")).allowBluetoothRetireRetry)
        val nextWindows = ReceiverCompatibility.resolve(winPlay.copy(firmwareVersion = "1.3.0"))
        assertEquals(ReceiverCompatibility.BluetoothRole.KEEP_DURING_BRINGUP, nextWindows.bluetoothRole)
        assertTrue(nextWindows.rules.contains("winplay.unverified-firmware"))
    }

    @Test
    fun protocolCapabilitiesTakePrecedenceOverReceiverNameAndStackVersion() {
        val plain = ReceiverCompatibility.resolve(winPlay.copy(features = 0, srcvers = "950.7.1"))
        assertEquals(ReceiverCompatibility.PairingMode.ADVERTISED_PLAIN, plain.pairingMode)
        val modernOldVersion = ReceiverCompatibility.resolve(winPlay.copy(srcvers = "210.81"))
        assertEquals(ReceiverCompatibility.PairingMode.PAIR_AND_ENCRYPT, modernOldVersion.pairingMode)
        assertEquals(ReceiverCompatibility.PairingMode.PROBE, ReceiverCompatibility.resolve(ReceiverProfile.Signals()).pairingMode)
    }

    @Test
    fun liveMediaSurvivesBluetoothLossForEveryFamily() {
        listOf(winPlay, diPlay, mib3, ReceiverProfile.Signals()).forEach { signals ->
            val action = ReceiverCompatibility.bluetoothLossAction(ReceiverCompatibility.resolve(signals), true, false)
            assertEquals(ReceiverCompatibility.BluetoothLossAction.KEEP_NETWORK_SESSION, action)
        }
    }

    @Test
    fun onlyConfirmedBootstrapReceiverCanReleaseBluetoothBeforeVideoStarts() {
        listOf(diPlay, mib3, ReceiverProfile.Signals()).forEach { signals ->
            val action = ReceiverCompatibility.bluetoothLossAction(ReceiverCompatibility.resolve(signals), false, true)
            assertEquals(ReceiverCompatibility.BluetoothLossAction.END_SESSION, action)
        }
        val windows = ReceiverCompatibility.resolve(winPlay)
        assertEquals(
            ReceiverCompatibility.BluetoothLossAction.KEEP_NETWORK_SESSION,
            ReceiverCompatibility.bluetoothLossAction(windows, false, true),
        )
        assertEquals(
            ReceiverCompatibility.BluetoothLossAction.END_SESSION,
            ReceiverCompatibility.bluetoothLossAction(windows, false, false),
        )
    }

    /** 瑞虎 8Plus（iAP2 自报 Cloudrive / Tiggo / CHERY）：奇瑞其余适配默认生效，disableBluetooth 默认关、仅开关打开时启用。 */
    @Test
    fun onlyCheryReleasesBluetoothAfterSetup() {
        val chery = ReceiverProfile.Signals(name = "Cloudrive", model = "Tiggo", manufacturer = "CHERY")
        val defaultPolicy = ReceiverCompatibility.resolve(chery)
        assertFalse(defaultPolicy.releaseBluetoothAfterSetup)
        assertFalse("chery.disable-bluetooth" in defaultPolicy.rules)
        assertTrue(defaultPolicy.reportWifiMacInSetup)
        assertTrue("chery.real-wifi-mac" in defaultPolicy.rules)
        assertTrue(defaultPolicy.skipIapTunnel)
        assertTrue("chery.no-iap-tunnel" in defaultPolicy.rules)
        assertTrue(defaultPolicy.sendAppleClientHeaders)
        assertTrue("chery.apple-client-headers" in defaultPolicy.rules)
        assertTrue(defaultPolicy.iphoneAuthSequence)
        assertTrue("chery.iphone-auth-sequence" in defaultPolicy.rules)
        assertEquals(32, defaultPolicy.pinnedMfiChallengeLength)
        assertTrue("chery.mfi-32-only" in defaultPolicy.rules)

        val optInPolicy = ReceiverCompatibility.resolve(chery, cheryDisableBluetooth = true)
        assertTrue(optInPolicy.releaseBluetoothAfterSetup)
        assertTrue("chery.disable-bluetooth" in optInPolicy.rules)

        listOf(winPlay, diPlay, mib3, ReceiverProfile.Signals(), ReceiverProfile.Signals(name = "CHERY")).forEach { signals ->
            val other = ReceiverCompatibility.resolve(signals, cheryDisableBluetooth = true)
            assertFalse(other.releaseBluetoothAfterSetup)
            assertFalse("chery.disable-bluetooth" in other.rules)
            assertFalse(other.reportWifiMacInSetup)
            assertFalse(other.skipIapTunnel)
            assertFalse(other.sendAppleClientHeaders)
            assertFalse(other.iphoneAuthSequence)
            assertNull(other.pinnedMfiChallengeLength)
        }
    }

    @Test
    fun requestedBluetoothReleaseKeepsSessionBeforeVideoStarts() {
        val policy = ReceiverCompatibility.resolve(ReceiverProfile.Signals(manufacturer = "CHERY"))
        assertEquals(
            ReceiverCompatibility.BluetoothLossAction.KEEP_NETWORK_SESSION,
            ReceiverCompatibility.bluetoothLossAction(policy, false, false, bluetoothReleased = true),
        )
        assertEquals(
            ReceiverCompatibility.BluetoothLossAction.END_SESSION,
            ReceiverCompatibility.bluetoothLossAction(policy, false, false, bluetoothReleased = false),
        )
    }

    @Test
    fun singleRecordIsLimitedToTheObservedCheryStack() {
        val observed = ReceiverProfile.Signals(manufacturer = "CHERY", model = "Tiggo", srcvers = "320.17.7")
        val policy = ReceiverCompatibility.resolve(observed)
        assertTrue(policy.singleRecordPerSession)
        assertTrue("chery.tiggo.320.17.7.single-record" in policy.rules)
        listOf(
            observed.copy(manufacturer = "OTHER"),
            observed.copy(model = "OTHER"),
            observed.copy(srcvers = "320.17.8"),
            observed.copy(srcvers = null),
            mib3,
        ).forEach { assertFalse(ReceiverCompatibility.resolve(it).singleRecordPerSession) }
    }

    @Test
    fun timeoutAndResetNeverAuthorizePlaintextOrUnknownBluetoothRetirement() {
        listOf(winPlay, mib3, ReceiverProfile.Signals()).forEach { signals ->
            val decision = failedPairing(signals)
            assertFalse(decision.useBtRetireHack)
            assertFalse(decision.usePlainSession)
        }
        assertTrue(failedPairing(diPlay).useBtRetireHack)
        assertFalse(failedPairing(diPlay).usePlainSession)
    }

    @Test
    fun forbiddenPairingBlocksEveryFallbackIncludingKnownBenchException() {
        listOf(winPlay, diPlay, mib3, ReceiverProfile.Signals()).forEach { signals ->
            val decision = failedPairing(signals, forbidden = true, unsupported = true)
            assertFalse(decision.useBtRetireHack)
            assertFalse(decision.usePlainSession)
        }
    }

    @Test
    fun explicitUnsupportedPairingPermitsPlainSession() {
        assertTrue(failedPairing(ReceiverProfile.Signals(), unsupported = true).usePlainSession)
        assertTrue(failedPairing(ReceiverProfile.Signals(features = 0)).usePlainSession)
    }

    @Test
    fun audioMemoryIsScopedToFirmwareStackAndCapabilities() {
        val original = ReceiverCompatibility.resolve(winPlay, "102|media|8388608", "receiver-a").memoryKey
        assertNotEquals(
            original,
            ReceiverCompatibility.resolve(winPlay.copy(firmwareVersion = "1.3.0"), "102|media|8388608", "receiver-a").memoryKey,
        )
        assertNotEquals(
            original,
            ReceiverCompatibility.resolve(winPlay.copy(srcvers = "951.1"), "102|media|8388608", "receiver-a").memoryKey,
        )
        assertNotEquals(original, ReceiverCompatibility.resolve(winPlay, "100|media|32768", "receiver-a").memoryKey)
        assertNotEquals(original, ReceiverCompatibility.resolve(winPlay, "102|media|8388608", "receiver-b").memoryKey)
        assertEquals(
            original,
            ReceiverCompatibility.resolve(winPlay.copy(name = "renamed-device"), "102|media|8388608", "receiver-a").memoryKey,
        )
        assertNull(ReceiverCompatibility.resolve(winPlay).memoryKey)
        assertNull(ReceiverCompatibility.resolve(ReceiverProfile.Signals(name = "unknown-receiver")).memoryKey)
    }

    @Test
    fun switchingReceiverNeverKeepsPreviousSoftwareRules() {
        ReceiverCompatibility.resolve(winPlay)
        val unknown = ReceiverCompatibility.resolve(ReceiverProfile.Signals())
        assertEquals(ReceiverCompatibility.Family.GENERIC, unknown.family)
        assertFalse(unknown.requiresRealBluetoothIdentity)
        assertFalse(unknown.allowBluetoothRetireRetry)
        assertEquals(ReceiverCompatibility.BluetoothRole.KEEP_DURING_BRINGUP, unknown.bluetoothRole)
    }

    @Test
    fun policyDiagnosticsDoNotContainRawDeviceIdentity() {
        val policy = ReceiverCompatibility.resolve(winPlay.copy(name = "private-hotspot", manufacturer = "AA:BB:CC:DD:EE:FF"))
        val text = ReceiverCompatibility.describe(policy)
        assertFalse(text.contains("private-hotspot"))
        assertFalse(text.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(text.contains("protocol.pair_and_encrypt"))
    }

    private fun failedPairing(
        signals: ReceiverProfile.Signals,
        forbidden: Boolean = false,
        unsupported: Boolean = false,
    ): ReceiverCompatibility.PairingFallback =
        ReceiverCompatibility.pairingFallback(
            ReceiverCompatibility.resolve(signals),
            keysEstablished = false,
            noPairing = false,
            pairSetupRejected = false,
            pairingForbidden = forbidden,
            attemptedPairing = true,
            pairingUnsupported = unsupported,
        )

    @Test
    fun onlyVolkswagenModelsReconnectOnStreamingInvites() {
        val id7 =
            ReceiverCompatibility.resolve(
                ReceiverProfile.Signals(model = "VW4131", manufacturer = "Volkswagen", srcvers = "320.17.7"),
            )
        assertTrue(id7.reconnectOnStreamingInvite)
        assertTrue(id7.rules.contains("vw.streaming-invite-reconnect"))
        val passat =
            ReceiverCompatibility.resolve(
                ReceiverProfile.Signals(model = "VW4211", manufacturer = "Toppower", srcvers = "320.17.3"),
            )
        assertTrue(passat.reconnectOnStreamingInvite)
        for (other in listOf(
            winPlay,
            diPlay,
            mib3,
            ReceiverProfile.Signals(model = "Tiggo", manufacturer = "CHERY", srcvers = "320.17.7"),
        )) {
            val policy = ReceiverCompatibility.resolve(other)
            assertFalse(policy.reconnectOnStreamingInvite)
            assertFalse(policy.rules.contains("vw.streaming-invite-reconnect"))
        }
        assertFalse(ReceiverCompatibility.resolve(ReceiverProfile.Signals(model = "VW")).reconnectOnStreamingInvite)
        assertFalse(ReceiverCompatibility.resolve(ReceiverProfile.Signals(model = "VW41310")).reconnectOnStreamingInvite)
        assertFalse(ReceiverCompatibility.resolve(ReceiverProfile.Signals()).reconnectOnStreamingInvite)
    }
}
