package com.mcarplay.app.compat

import com.mcarplay.app.iap2.ReceiverProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehiclePresetTest {
    private val unknown = ReceiverProfile.Signals()
    private val chery = ReceiverProfile.Signals(manufacturer = "CHERY", model = "Tiggo")

    private fun resolve(
        signals: ReceiverProfile.Signals,
        preset: VehiclePreset,
    ) = ReceiverCompatibility.resolve(signals, preset = preset)

    @Test
    fun autoLeavesDetectedPolicyUntouched() {
        assertEquals(
            ReceiverCompatibility.resolve(chery, preset = VehiclePreset.AUTO),
            ReceiverCompatibility.applyPreset(ReceiverCompatibility.resolve(chery, preset = VehiclePreset.AUTO), VehiclePreset.AUTO),
        )
        assertFalse(resolve(unknown, VehiclePreset.AUTO).releaseBluetoothAfterSetup)
    }

    @Test
    fun cheryPresetEnablesCheryRulesOnUnknownReceiver() {
        val policy = resolve(unknown, VehiclePreset.CHERY)
        assertFalse(policy.releaseBluetoothAfterSetup)
        assertFalse("chery.disable-bluetooth" in policy.rules)
        assertTrue(policy.skipIapTunnel)
        assertTrue(policy.iphoneAuthSequence)
        assertEquals(32, policy.pinnedMfiChallengeLength)
        assertTrue("manual.chery" in policy.rules)

        val optInUnknown = ReceiverCompatibility.resolve(unknown, preset = VehiclePreset.CHERY, cheryDisableBluetooth = true)
        assertTrue(optInUnknown.releaseBluetoothAfterSetup)
        assertEquals(1, optInUnknown.rules.count { it == "chery.disable-bluetooth" })
        assertTrue("manual.chery" in optInUnknown.rules)

        val optInDetected = ReceiverCompatibility.resolve(chery, preset = VehiclePreset.CHERY, cheryDisableBluetooth = true)
        assertTrue(optInDetected.releaseBluetoothAfterSetup)
        assertEquals(1, optInDetected.rules.count { it == "chery.disable-bluetooth" })
    }

    @Test
    fun genericPresetDisablesDetectedExceptions() {
        val policy = ReceiverCompatibility.resolve(chery, preset = VehiclePreset.GENERIC, cheryDisableBluetooth = true)
        assertFalse(policy.releaseBluetoothAfterSetup)
        assertFalse(policy.sendAppleClientHeaders)
        assertNull(policy.pinnedMfiChallengeLength)
        assertTrue("manual.generic" in policy.rules)
    }

    @Test
    fun otherPresetsApplyTheirFlags() {
        assertTrue(resolve(unknown, VehiclePreset.VOLKSWAGEN).reconnectOnStreamingInvite)
        val win = resolve(unknown, VehiclePreset.WINPLAY)
        assertEquals(ReceiverCompatibility.BluetoothRole.NETWORK_HANDOFF, win.bluetoothRole)
        assertTrue(win.requiresRealBluetoothIdentity)
        assertTrue(resolve(unknown, VehiclePreset.DIPLAY).allowBluetoothRetireRetry)
        assertEquals(ReceiverProfile.LinkGeneration.CLASSIC_MIB3, resolve(unknown, VehiclePreset.CLASSIC_MIB3).linkGeneration)
    }

    @Test
    fun parseFallsBackToAuto() {
        assertEquals(VehiclePreset.AUTO, VehiclePreset.parse(null))
        assertEquals(VehiclePreset.AUTO, VehiclePreset.parse("nope"))
        assertEquals(VehiclePreset.CHERY, VehiclePreset.parse("CHERY"))
    }
}
