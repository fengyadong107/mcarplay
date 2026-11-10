package com.mcarplay.app.transport

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Iap2LinkChannelTest {
    /** 写永远阻塞（模拟对端不读、L2CAP 窗口耗尽），直到 close() 才释放。 */
    private class BlockedWriteStream : DuplexStream {
        val writeStarted = CountDownLatch(1)
        private val released = CountDownLatch(1)

        override fun read(buffer: ByteArray): Int {
            released.await()
            return -1
        }

        override fun write(bytes: ByteArray) {
            writeStarted.countDown()
            released.await()
        }

        override fun close() {
            released.countDown()
        }
    }

    /**
     * 写阻塞时 close() 必须能立刻完成。
     *
     * 旧实现把所有 socket 写放在通道锁内：对端不读时 write 一直阻塞并持锁，
     * close()（同为 synchronized(lock)）跟着卡死，读线程（入站 ACK/EOF 收尾）与
     * 节拍线程（重传/协商定时器）也一起冻住——链路层没有写超时，只能靠「写不持锁」。
     */
    @Test(timeout = 5_000)
    fun closeCompletesWhileWriteIsBlocked() {
        val stream = BlockedWriteStream()
        val channel = Iap2LinkChannel(stream, name = "blocked-write")
        assertTrue("引擎启动后应立刻有帧写出并阻塞", stream.writeStarted.await(2, TimeUnit.SECONDS))
        channel.close()
        // 没超时即说明 close() 没有被阻塞中的写卡住
    }
}
