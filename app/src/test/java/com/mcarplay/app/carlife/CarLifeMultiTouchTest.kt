package com.mcarplay.app.carlife

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarLifeMultiTouchTest {
    @Test
    fun singleFingerStillUsesDiscreteMessages() {
        val state = CarLifeMultiTouchState()
        val down =
            state.onFrame(
                listOf(CarLifeMultiTouchState.Contact(0, 10, 20, down = true)),
                width = 100,
                height = 100,
            )
        assertEquals(
            listOf(CarLifeWireTouch(CarLifeProtocol.TOUCH_DOWN, 10, 20)),
            down,
        )
        val move =
            state.onFrame(
                listOf(CarLifeMultiTouchState.Contact(0, 30, 40, down = true)),
                width = 100,
                height = 100,
            )
        assertEquals(
            listOf(CarLifeWireTouch(CarLifeProtocol.TOUCH_MOVE, 30, 40)),
            move,
        )
        val up =
            state.onFrame(
                listOf(CarLifeMultiTouchState.Contact(0, 30, 40, down = false)),
                width = 100,
                height = 100,
            )
        assertEquals(
            listOf(CarLifeWireTouch(CarLifeProtocol.TOUCH_UP, 30, 40)),
            up,
        )
    }

    @Test
    fun secondFingerSwitchesToCompositeTouchAction() {
        val state = CarLifeMultiTouchState()
        state.onFrame(
            listOf(CarLifeMultiTouchState.Contact(0, 10, 20, down = true)),
            width = 100,
            height = 100,
        )
        val act = CarLifeProtocol.TouchAction
        val secondDown =
            state.onFrame(
                listOf(
                    CarLifeMultiTouchState.Contact(0, 10, 20, down = true),
                    CarLifeMultiTouchState.Contact(1, 80, 20, down = true),
                ),
                width = 100,
                height = 100,
            )
        // 一条消息带两个点：x/y=手指0，pointerX/pointerY=手指1
        assertEquals(
            listOf(
                CarLifeWireTouch(
                    CarLifeProtocol.TOUCH_ACTION,
                    10,
                    20,
                    act.withPointer(act.POINTER_DOWN, 1),
                    80,
                    20,
                ),
            ),
            secondDown,
        )
        val spread =
            state.onFrame(
                listOf(
                    CarLifeMultiTouchState.Contact(0, 5, 20, down = true),
                    CarLifeMultiTouchState.Contact(1, 90, 20, down = true),
                ),
                width = 100,
                height = 100,
            )
        assertEquals(
            listOf(
                CarLifeWireTouch(CarLifeProtocol.TOUCH_ACTION, 5, 20, act.MOVE, 90, 20),
            ),
            spread,
        )
    }

    @Test
    fun dualFingerSameFrameStartsOnCompositeDown() {
        val state = CarLifeMultiTouchState()
        val act = CarLifeProtocol.TouchAction
        val down =
            state.onFrame(
                listOf(
                    CarLifeMultiTouchState.Contact(0, 40, 50, down = true),
                    CarLifeMultiTouchState.Contact(1, 60, 50, down = true),
                ),
                width = 100,
                height = 100,
            )
        assertEquals(
            listOf(CarLifeWireTouch(CarLifeProtocol.TOUCH_ACTION, 40, 50, act.DOWN, 60, 50)),
            down,
        )
    }

    @Test
    fun dualFingerReleaseSendsPointerUpThenUp() {
        val state = CarLifeMultiTouchState()
        val act = CarLifeProtocol.TouchAction
        state.onFrame(
            listOf(
                CarLifeMultiTouchState.Contact(0, 40, 50, down = true),
                CarLifeMultiTouchState.Contact(1, 60, 50, down = true),
            ),
            width = 100,
            height = 100,
        )
        val up =
            state.onFrame(
                listOf(
                    CarLifeMultiTouchState.Contact(0, 40, 50, down = false),
                    CarLifeMultiTouchState.Contact(1, 60, 50, down = false),
                ),
                width = 100,
                height = 100,
            )
        assertEquals(
            listOf(
                CarLifeWireTouch(
                    CarLifeProtocol.TOUCH_ACTION,
                    40,
                    50,
                    act.withPointer(act.POINTER_UP, 1),
                    60,
                    50,
                ),
                CarLifeWireTouch(CarLifeProtocol.TOUCH_ACTION, 40, 50, act.UP, 60, 50),
            ),
            up,
        )
    }

    @Test
    fun touchActionPayloadEncodesActionXy() {
        val payload = CarLifeProtocol.touchActionPayload(0x105, 12, 34)
        // field1=action, field2=x, field3=y — same layout as CarPToCarL
        assertArrayEquals(
            CarLifeProtocol.pbInt(1, 0x105) + CarLifeProtocol.pbInt(2, 12) + CarLifeProtocol.pbInt(3, 34),
            payload,
        )
    }
}
