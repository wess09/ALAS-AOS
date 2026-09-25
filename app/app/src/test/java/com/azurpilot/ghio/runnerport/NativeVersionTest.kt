package com.azurpilot.ghio.runnerport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TouchArgs.contact 版本闸的判定；旧 fw 不填该字段，误读过闸会读到栈残值 */
class NativeVersionTest {

    @Test
    fun `低于配对版本不读 contact`() {
        assertFalse(NativeVersion.fillsTouchContact("5.11.9"))
        assertFalse(NativeVersion.fillsTouchContact("v5.12.2"))
        assertFalse(NativeVersion.fillsTouchContact("5.12"))
    }

    @Test
    fun `配对版本起过闸`() {
        assertTrue(NativeVersion.fillsTouchContact("5.12.3"))
        assertTrue(NativeVersion.fillsTouchContact("v5.12.3"))
        assertTrue(NativeVersion.fillsTouchContact(" 5.12.3 "))
        assertTrue(NativeVersion.fillsTouchContact("5.12.10"))
        assertTrue(NativeVersion.fillsTouchContact("5.13.0"))
        assertTrue(NativeVersion.fillsTouchContact("6.0.0"))
    }

    @Test
    fun `预发布按低于所标版本处理`() {
        assertFalse(NativeVersion.fillsTouchContact("v5.12.0-beta.1"))
        assertFalse(NativeVersion.fillsTouchContact("v5.12.3-rc.1"))
    }

    @Test
    fun `拿不到或解析不了按不支持`() {
        assertFalse(NativeVersion.fillsTouchContact(null))
        assertFalse(NativeVersion.fillsTouchContact(""))
        assertFalse(NativeVersion.fillsTouchContact("garbage"))
        assertFalse(NativeVersion.fillsTouchContact("version 5.12.3"))
    }
}
