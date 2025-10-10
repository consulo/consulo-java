package com.siyeh.ipp.concatenation;

import com.siyeh.ipp.IPPTestCase;
import com.siyeh.localize.IntentionPowerPackLocalize;

public abstract class JoinConcatenatedStringLiteralsIntentionTest extends IPPTestCase {
    public void testSimple() {
        doTest();
    }

    public void testPolyadic() {
        doTest();
    }

    public void testNonString() {
        doTest();
    }

    public void testNonString2() {
        doTest();
    }

    public void testNotAvailable() {
        assertIntentionNotAvailable();
    }

    public void testKeepCommentsAndWhitespace() {
        doTest();
    }

    @Override
    protected String getIntentionName() {
        return IntentionPowerPackLocalize.joinConcatenatedStringLiteralsIntentionName().get();
    }

    @Override
    protected String getRelativePath() {
        return "concatenation/join_concat";
    }
}
