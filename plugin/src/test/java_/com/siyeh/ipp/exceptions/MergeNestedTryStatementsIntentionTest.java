package com.siyeh.ipp.exceptions;

import com.siyeh.ipp.IPPTestCase;
import com.siyeh.localize.IntentionPowerPackLocalize;

/**
 * @author Bas Leijdekkers
 */
public abstract class MergeNestedTryStatementsIntentionTest extends IPPTestCase {

    public void testSimple() {
        doTest();
    }

    public void testWithoutAndWithResources() {
        doTest();
    }

    public void testOldStyle() {
        doTest();
    }

    @Override
    protected String getIntentionName() {
        return IntentionPowerPackLocalize.mergeNestedTryStatementsIntentionName().get();
    }

    @Override
    protected String getRelativePath() {
        return "exceptions/mergeTry";
    }
}
