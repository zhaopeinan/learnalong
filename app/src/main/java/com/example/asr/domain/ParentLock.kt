package com.example.asr.domain

/**
 * 家长锁 PIN（对应小程序 utils/mode.ts）：
 * 进入孩子端免密（家长把手机递给孩子），退出孩子端必须验证 PIN。
 */
object ParentLock {

    private val PIN_PATTERN = Regex("^\\d{4,6}$")

    /** 是否已设置过 PIN（小程序 hasPin: parentPin.length >= 4） */
    fun hasPin(saved: String): Boolean = saved.length >= 4

    /** 校验 PIN；未设置过时拒绝（小程序 verifyPin） */
    fun verify(saved: String, input: String): Boolean = hasPin(saved) && saved == input.trim()

    /** 新 PIN 合法性：4-6 位数字（小程序 setPin） */
    fun isValidNewPin(pin: String): Boolean = PIN_PATTERN.matches(pin.trim())
}
