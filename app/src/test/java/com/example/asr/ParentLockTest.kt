package com.example.asr

import com.example.asr.domain.ParentLock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 家长锁 PIN（对齐小程序 utils/mode.ts 的 hasPin / verifyPin / setPin） */
class ParentLockTest {

    @Test
    fun `hasPin means saved length at least 4`() {
        assertFalse(ParentLock.hasPin(""))
        assertFalse(ParentLock.hasPin("123"))
        assertTrue(ParentLock.hasPin("1234"))
        assertTrue(ParentLock.hasPin("123456"))
    }

    @Test
    fun `verify rejects when pin never set`() {
        assertFalse(ParentLock.verify("", ""))
        assertFalse(ParentLock.verify("12", "12"))
    }

    @Test
    fun `verify trims input and requires exact match`() {
        assertTrue(ParentLock.verify("1234", " 1234 "))
        assertFalse(ParentLock.verify("1234", "1235"))
        assertFalse(ParentLock.verify("1234", "123"))
    }

    @Test
    fun `new pin must be 4 to 6 digits`() {
        assertFalse(ParentLock.isValidNewPin(""))
        assertFalse(ParentLock.isValidNewPin("123"))
        assertTrue(ParentLock.isValidNewPin("1234"))
        assertTrue(ParentLock.isValidNewPin("123456"))
        assertFalse(ParentLock.isValidNewPin("1234567"))
        assertFalse(ParentLock.isValidNewPin("12a4"))
        assertTrue(ParentLock.isValidNewPin(" 1234 "))
    }
}
