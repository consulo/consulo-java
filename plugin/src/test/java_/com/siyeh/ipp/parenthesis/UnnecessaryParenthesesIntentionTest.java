package com.siyeh.ipp.parenthesis;

import com.intellij.java.impl.ipp.parenthesis.RemoveUnnecessaryParenthesesIntention;
import com.siyeh.ipp.IPPTestCase;
import com.siyeh.localize.IntentionPowerPackLocalize;

/**
 * @see RemoveUnnecessaryParenthesesIntention
 */
public abstract class UnnecessaryParenthesesIntentionTest extends IPPTestCase {

    public void testPolyadic() {
        doTest();
    }

    public void testCommutative() {
        doTest();
    }

    public void testWrapping() {
        doTest();
    }

    public void testNotCommutative() {
        assertIntentionNotAvailable();
    }

    public void testStringParentheses() {
        assertIntentionNotAvailable();
    }

    public void testComparisonParentheses() {
        assertIntentionNotAvailable();
    }

    public void testNotCommutative2() {
        doTest();
    }

    public void testArrayInitializer() {
        doTest();
    }

    public void testArrayAccessExpression() {
        doTest();
    }

    public void testArrayAccessExpression2() {
        doTest();
    }

    public void testSimplePrecedence() {
        assertIntentionNotAvailable();
    }

    @Override
    protected String getRelativePath() {
        return "parentheses";
    }

    @Override
    protected String getIntentionName() {
        return IntentionPowerPackLocalize.removeUnnecessaryParenthesesIntentionName().get();
    }
}