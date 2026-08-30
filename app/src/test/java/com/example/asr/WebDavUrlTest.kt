package com.example.asr

import com.example.asr.data.sync.WebDavClient
import org.junit.Assert.assertEquals
import org.junit.Test

/** WebDavClient URL 拼接 / 路径处理（纯 JVM） */
class WebDavUrlTest {

    @Test
    fun `joinUrl adds trailing slash to base`() {
        assertEquals(
            "https://dav.jianguoyun.com/dav/ASRTutor/backup.json",
            WebDavClient.joinUrl("https://dav.jianguoyun.com/dav", "ASRTutor/backup.json"),
        )
    }

    @Test
    fun `joinUrl keeps existing trailing slash and trims path slashes`() {
        assertEquals(
            "https://dav.jianguoyun.com/dav/ASRTutor/recordings/rec_1.m4a",
            WebDavClient.joinUrl("https://dav.jianguoyun.com/dav/", "/ASRTutor/recordings/rec_1.m4a/"),
        )
    }

    @Test
    fun `joinUrl encodes special chars in segments`() {
        assertEquals(
            "https://dav.jianguoyun.com/dav/ASRTutor/recordings/rec%20a.m4a",
            WebDavClient.joinUrl("https://dav.jianguoyun.com/dav/", "ASRTutor/recordings/rec a.m4a"),
        )
    }

    @Test
    fun `joinUrl empty path returns normalized base`() {
        assertEquals(
            "https://dav.jianguoyun.com/dav/",
            WebDavClient.joinUrl("https://dav.jianguoyun.com/dav", ""),
        )
    }
}
