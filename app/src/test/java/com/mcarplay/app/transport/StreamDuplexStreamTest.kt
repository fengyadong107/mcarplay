package com.mcarplay.app.transport

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 回归测试：读线程阻塞在 read() 期间，写线程必须能完成 write()。
 * 旧实现 read/write 共享一把 @Synchronized 监视器，读阻塞时持锁不放，
 * 写永久等待——真机蓝牙上实际触发（日志停在 link ready 之后）。
 */
class StreamDuplexStreamTest {
    @Test
    fun writeCompletesWhileReaderBlocksInRead() {
        val source = PipedInputStream(1 shl 16)
        val sink = PipedOutputStream()
        sink.connect(source)
        val stream = StreamDuplexStream(source, sink)

        val reader =
            Thread {
                val buffer = ByteArray(1024)
                try {
                    while (stream.read(buffer) >= 0) {
                        // 持续阻塞读，模拟链路通道的读线程
                    }
                } catch (_: Exception) {
                }
            }.apply {
                isDaemon = true
                start()
            }

        // 不能靠 sleep 猜时序：读者还没进 read() 就写，旧的一把锁实现也能通过，回归被漏掉。
        // 轮询线程状态，确认它真的阻塞在 read() 上再写（WAITING/TIMED_WAITING 都算，
        // PipedInputStream 用的是 wait(1000) 定时等待）。
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (reader.state != Thread.State.WAITING &&
            reader.state != Thread.State.TIMED_WAITING &&
            System.nanoTime() < deadline
        ) {
            Thread.sleep(5)
        }
        assertTrue(
            "读线程未进入阻塞读（state=${reader.state}），本次断言无意义",
            reader.state == Thread.State.WAITING || reader.state == Thread.State.TIMED_WAITING,
        )

        val written = CountDownLatch(1)
        Thread({
            stream.write(byteArrayOf(0x40, 0x40, 0x00, 0x06, 0x1D, 0x00))
            written.countDown()
        }, "writer").apply {
            isDaemon = true
            start()
        }

        assertTrue("write 在读线程阻塞期间发生了死锁", written.await(2, TimeUnit.SECONDS))

        stream.close()
        reader.join(1_000)
    }
}
