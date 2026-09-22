package com.payneteasy.telegram.bot.client.http;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The copy that replaces a throwable on its way out has to keep the diagnosis and drop the
 * secret — and has to survive a chain that is not a straight line.
 */
public class ScrubbedCauseTest {

    private static final String TOKEN = "123456789:AAHfake0Token1For2Tests3456789abcXYZ";

    @Test
    public void keepsTheTypeAndTheStackAndDropsTheSecret() {
        Exception original = new java.net.MalformedURLException("no protocol: bad/bot" + TOKEN + "/getMe");

        ScrubbedCause scrubbed = ScrubbedCause.sanitize(original);

        assertFalse("the secret must not survive", scrubbed.getMessage().contains("AAHfake0Token1For2Tests3456789abcXYZ"));
        assertTrue("the type is what makes the record diagnosable", scrubbed.getMessage().contains("MalformedURLException"));
        assertTrue("and so are the frames", scrubbed.getMessage().contains("123456789:***"));
        assertEquals("the stack has to come across", original.getStackTrace().length, scrubbed.getStackTrace().length);
        assertTrue(scrubbed.getStackTrace().length > 0);
    }

    @Test
    public void copiesTheWholeChain() {
        Exception root  = new IllegalStateException("root holds " + TOKEN);
        Exception outer = new RuntimeException("outer", root);

        ScrubbedCause scrubbed = ScrubbedCause.sanitize(outer);

        assertNotNull(scrubbed.getCause());
        assertFalse(scrubbed.getCause().getMessage().contains("AAHfake0Token1For2Tests3456789abcXYZ"));
    }

    /** A cycle is legal, and walking it blindly gives an Error that escapes every handler. */
    @Test
    public void survivesACycleInTheChain() {
        Exception first  = new Exception("first");
        Exception second = new Exception("second");
        first.initCause(second);
        second.initCause(first);

        ScrubbedCause scrubbed = ScrubbedCause.sanitize(first);

        assertNotNull(scrubbed);
        assertNotNull(scrubbed.getCause());
        assertNull("the cycle has to stop somewhere", scrubbed.getCause().getCause());
    }

    @Test
    public void nothingToCopyIsNotAFailure() {
        assertNull(ScrubbedCause.sanitize(null));
    }
}
