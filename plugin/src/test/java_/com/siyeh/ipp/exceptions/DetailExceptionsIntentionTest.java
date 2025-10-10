package com.siyeh.ipp.exceptions;

import com.intellij.java.impl.ipp.exceptions.DetailExceptionsIntention;
import com.siyeh.ipp.IPPTestCase;
import com.siyeh.localize.IntentionPowerPackLocalize;

/**
 * @author Bas Leijdekkers
 * @see DetailExceptionsIntention
 */
public abstract class DetailExceptionsIntentionTest extends IPPTestCase {

    public void testDisjunction() {
        assertIntentionNotAvailable();
    }

    public void testSimple() {
        doTest();
    }

    public void testForeach() {
        doTest();
    }

    public void testTryWithResources() {
        doTest();
    }

    public void testPolyadicParentheses() {
        doTest();
    }

    @Override
    protected String getIntentionName() {
        return IntentionPowerPackLocalize.detailExceptionsIntentionName().get();
    }

    @Override
    protected String getRelativePath() {
        return "exceptions/detail";
    }
}
