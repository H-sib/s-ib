package com.hsib.sib.core

import org.junit.Assert.assertTrue
import org.junit.Test

class HelloTest {
    @Test fun tinkWorksOnJvm() = assertTrue(Hello.roundTrip())
}
