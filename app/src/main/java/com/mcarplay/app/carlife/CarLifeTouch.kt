package com.mcarplay.app.carlife

enum class CarLifeTouchAction {
    DOWN,
    MOVE,
    UP,
}

/**
 * 一条待写入触摸通道的报文（单点消息或 0x68001 复合动作）。
 *
 * [pointerX]/[pointerY] 是复合动作里的第二根手指（`CarlifeTouchAction` 的
 * pointerx/pointery 字段），单点消息用不到。
 */
data class CarLifeWireTouch(
    val serviceType: Int,
    val x: Int,
    val y: Int,
    val action: Int = -1,
    val pointerX: Int = 0,
    val pointerY: Int = 0,
)

/**
 * CarLife accepts one strict touch gesture: DOWN, zero or more MOVEs, then UP.
 * Keeping the transition rules here also gives the network layer a single
 * place to synthesize a final UP when a channel or session is torn down.
 */
class CarLifeTouchState {
    private var active = false
    private var lastX = 0
    private var lastY = 0

    @Synchronized
    fun onContact(
        x: Int,
        y: Int,
        down: Boolean,
        width: Int,
        height: Int,
    ): CarLifeTouchAction? {
        if (width <= 0 || height <= 0 || x !in 0 until width || y !in 0 until height) return null
        val action =
            when {
                down && !active -> CarLifeTouchAction.DOWN
                down -> CarLifeTouchAction.MOVE
                active -> CarLifeTouchAction.UP
                else -> return null
            }
        lastX = x
        lastY = y
        active = action != CarLifeTouchAction.UP
        return action
    }

    @Synchronized
    fun releasePoint(): Pair<Int, Int>? {
        if (!active) return null
        active = false
        return lastX to lastY
    }

    @Synchronized
    fun reset() {
        active = false
    }
}

/**
 * HID 多槽触点 → CarLife 报文。单指仍走 0x68002/3/4；出现第二指后切到 0x68001。
 * 状态机对齐 CarPToCarL 的双指路径（POINTER_DOWN/UP + 共用 MOVE）。
 */
class CarLifeMultiTouchState {
    data class Contact(
        val slot: Int,
        val x: Int,
        val y: Int,
        val down: Boolean,
    )

    private var slot0Down = false
    private var slot1Down = false
    private var multiActive = false
    private var last0X = 0
    private var last0Y = 0
    private var last1X = 0
    private var last1Y = 0

    /**
     * 一帧触点 → 报文。
     *
     * 双指期间**每帧只发一条** `CarlifeTouchAction`，两个点都带上：
     * `x/y` 是第一根手指，`pointerx/pointery` 是第二根。CarWith 的
     * `CarlifeTouchManager.onActionEvent` 就是这么读的（MOVE 分支取两组坐标拼
     * 双指针 MotionEvent）；只发一组时第二根手指恒为 (0,0)，两指距离不变，
     * ScaleGestureDetector 永远不触发 —— 实测症状：双指只平移不缩放。
     */
    @Synchronized
    fun onFrame(
        contacts: List<Contact>,
        width: Int,
        height: Int,
    ): List<CarLifeWireTouch> {
        if (width <= 0 || height <= 0) return emptyList()
        val bySlot = contacts.associateBy { it.slot }
        val c0 = bySlot[0]?.takeIf { it.x in 0 until width && it.y in 0 until height }
        val c1 = bySlot[1]?.takeIf { it.x in 0 until width && it.y in 0 until height }
        val act = CarLifeProtocol.TouchAction
        val c0Down = c0?.down == true
        val c1Down = c1?.down == true
        val out = ArrayList<CarLifeWireTouch>(3)

        if (multiActive) {
            if (c0Down && c1Down) {
                if (c0!!.x != last0X || c0.y != last0Y || c1!!.x != last1X || c1.y != last1Y) {
                    last0X = c0.x
                    last0Y = c0.y
                    last1X = c1.x
                    last1Y = c1.y
                    out += CarLifeWireTouch(
                        CarLifeProtocol.TOUCH_ACTION, c0.x, c0.y, act.MOVE, c1.x, c1.y,
                    )
                }
                return out
            }
            if (!c0Down) {
                // 第一指（或两指同时）抬起：先用上一帧坐标补 POINTER_UP(1)，
                // 否则引擎留一个永不放开的幽灵触点。
                if (slot1Down) {
                    out += CarLifeWireTouch(
                        CarLifeProtocol.TOUCH_ACTION, last0X, last0Y,
                        act.withPointer(act.POINTER_UP, 1), last1X, last1Y,
                    )
                }
                out += CarLifeWireTouch(
                    CarLifeProtocol.TOUCH_ACTION, last0X, last0Y, act.UP, last1X, last1Y,
                )
                slot0Down = false
                slot1Down = false
                multiActive = false
                return out
            }
            // 第二指抬起：POINTER_UP(1)，随后第一指回到单点通道
            out += CarLifeWireTouch(
                CarLifeProtocol.TOUCH_ACTION, c0!!.x, c0.y,
                act.withPointer(act.POINTER_UP, 1), last1X, last1Y,
            )
            last0X = c0.x
            last0Y = c0.y
            slot1Down = false
            multiActive = false
        }

        if (c0Down) {
            if (!slot0Down) {
                slot0Down = true
                last0X = c0!!.x
                last0Y = c0.y
                if (c1Down) {
                    // 两指同一帧按下：**两条**，先 ACTION_DOWN 再 ACTION_POINTER_DOWN。
                    // CarWith 只在 case 5（POINTER_DOWN）里登记第二根手指（反编译
                    // CarlifeTouchManager.onActionEvent）；只发一条带两个点的 ACTION_DOWN
                    // 时它只认第 0 指，后续 MOVE 退化成单指拖——真机现象：地图朝左平移。
                    out += CarLifeWireTouch(
                        CarLifeProtocol.TOUCH_ACTION, c0.x, c0.y, act.DOWN, c1!!.x, c1.y,
                    )
                } else {
                    out += CarLifeWireTouch(CarLifeProtocol.TOUCH_DOWN, c0.x, c0.y)
                }
            }
            if (c1Down && !slot1Down) {
                // 第二指按下（同帧或后到）：切复合通道并登记指针 1。
                multiActive = true
                slot1Down = true
                last0X = c0!!.x
                last0Y = c0.y
                last1X = c1!!.x
                last1Y = c1.y
                out += CarLifeWireTouch(
                    CarLifeProtocol.TOUCH_ACTION, c0.x, c0.y,
                    act.withPointer(act.POINTER_DOWN, 1), c1.x, c1.y,
                )
            } else if (!multiActive && (c0!!.x != last0X || c0.y != last0Y)) {
                last0X = c0.x
                last0Y = c0.y
                out += CarLifeWireTouch(CarLifeProtocol.TOUCH_MOVE, c0.x, c0.y)
            }
            return out
        }

        if (slot0Down) {
            slot0Down = false
            out += CarLifeWireTouch(CarLifeProtocol.TOUCH_UP, last0X, last0Y)
        }
        return out
    }

    /** 会话拆掉时补发抬起，避免 CarWith 卡在按住。 */
    @Synchronized
    fun releaseAll(): List<CarLifeWireTouch> {
        val out = ArrayList<CarLifeWireTouch>(2)
        val act = CarLifeProtocol.TouchAction
        if (multiActive) {
            if (slot1Down) {
                out += CarLifeWireTouch(
                    CarLifeProtocol.TOUCH_ACTION, last0X, last0Y,
                    act.withPointer(act.POINTER_UP, 1), last1X, last1Y,
                )
                slot1Down = false
            }
            if (slot0Down) {
                out += CarLifeWireTouch(
                    CarLifeProtocol.TOUCH_ACTION, last0X, last0Y, act.UP, last1X, last1Y,
                )
                slot0Down = false
            }
            multiActive = false
        } else if (slot0Down) {
            out += CarLifeWireTouch(CarLifeProtocol.TOUCH_UP, last0X, last0Y)
            slot0Down = false
        }
        slot1Down = false
        return out
    }

    @Synchronized
    fun reset() {
        slot0Down = false
        slot1Down = false
        multiActive = false
    }
}

internal fun mapCarLifeTouch(
    x: Int,
    y: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
): Pair<Int, Int>? {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return null
    val mappedX = (x.toLong() * targetWidth / sourceWidth).toInt().coerceIn(0, targetWidth - 1)
    val mappedY = (y.toLong() * targetHeight / sourceHeight).toInt().coerceIn(0, targetHeight - 1)
    return mappedX to mappedY
}
