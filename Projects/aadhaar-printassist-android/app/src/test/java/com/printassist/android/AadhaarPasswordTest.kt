package com.printassist.android
import org.junit.Assert.assertEquals
import org.junit.Test
class AadhaarPasswordTest {
    @Test fun officialPatterns() {
        assertEquals("KORI1978", AadhaarPassword.generate("KORIVI NAGAMANI", "1978"))
        assertEquals("SURE1990", AadhaarPassword.generate("Suresh Kumar", "1990"))
        assertEquals("SAIK1990", AadhaarPassword.generate("Sai Kumar", "1990"))
        assertEquals("P.KU1990", AadhaarPassword.generate("P. Kumar", "1990"))
        assertEquals("RIA1990", AadhaarPassword.generate("Ria", "1990"))
    }
    @Test(expected = IllegalArgumentException::class) fun futureYear() { AadhaarPassword.generate("Test", "2030", 2026) }
    @Test(expected = IllegalArgumentException::class) fun emptyName() { AadhaarPassword.generate("  ", "1990") }
    @Test(expected = IllegalArgumentException::class) fun malformedYear() { AadhaarPassword.generate("Test", "90") }
}
