package com.mvv.java22sample

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test


class ForeignJava22ExampleTest {

    @Test
    fun getStringLengthUsingCStrLen() {
        assertThat(getStringLengthUsingCStrLen("Hello")).isEqualTo(5)
        assertThat(getStringLengthUsingCStrLen("misunderstanding")).isEqualTo(16)
    }

    @Test
    @Disabled("It was used to test build configuration")
    fun testFailure() {
        org.assertj.core.api.Assertions.fail<Any>("test message")
    }
}
