package dev.vestitus.tokenizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PiiTypeTest {
    @Test
    void taxonomyIsThePublishedSetInOrder() {
        PiiType[] v = PiiType.values();
        assertEquals(10, v.length);
        assertEquals("EMAIL", v[0].name());
        assertEquals("PHONE", v[1].name());
        assertEquals("SSN", v[2].name());
        assertEquals("ADDRESS", v[3].name());
        assertEquals("POSTAL_CODE", v[4].name());
        assertEquals("NAME", v[5].name());
        assertEquals("CREDIT_CARD", v[6].name());
        assertEquals("IBAN", v[7].name());
        assertEquals("IP_ADDRESS", v[8].name());
        assertEquals("DOB", v[9].name());
    }

    @Test
    void constantNameIsTheWireString() {
        assertEquals(PiiType.CREDIT_CARD, PiiType.valueOf("CREDIT_CARD"));
    }
}
