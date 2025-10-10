package com.siyeh.ipp.exceptions;

import com.siyeh.ipp.IPPTestCase;
import com.siyeh.localize.IntentionPowerPackLocalize;

/**
 * @author Bas Leijdekkers
 */
public abstract class SplitTryWithMultipleResourcesIntentionTest extends IPPTestCase {

    public void testSimple() {
        doTest();
    }

    public void testWithCatch() {
        doTest();
    }

    @Override
    protected String getIntentionName() {
        return IntentionPowerPackLocalize.splitTryWithMultipleResourcesIntentionName().get();
    }

    @Override
    protected String getRelativePath() {
        return "exceptions/splitTry";
    }
}
