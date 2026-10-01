package com.motoristapro.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatosTest {
    @Test fun centavos() {
        assertEquals(4590L, "45,90".paraCentavos())
        assertEquals(4590L, "45.90".paraCentavos())
        assertEquals(123456L, "R$ 1.234,56".paraCentavos())
        assertEquals(20000L, "200".paraCentavos())
        assertNull("".paraCentavos())
        assertNull("abc".paraCentavos())
        assertNull("0".paraCentavos())
        assertEquals(0L, "0,00".paraCentavosOuZero())
        assertNull("-5".paraCentavosOuZero())
    }

    @Test fun litros() {
        assertEquals(34500L, "34,5".litrosParaMl())
        assertEquals(40123L, "40.123".litrosParaMl())
    }
}
