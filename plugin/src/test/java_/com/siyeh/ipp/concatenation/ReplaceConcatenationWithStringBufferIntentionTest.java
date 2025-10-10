package com.siyeh.ipp.concatenation;

import com.siyeh.ipp.IPPTestCase;
import com.siyeh.localize.IntentionPowerPackLocalize;

/**
 * @author Bas Leijdekkers
 */
public abstract class ReplaceConcatenationWithStringBufferIntentionTest extends IPPTestCase {

    public void testNonStringConcatenationStart() {
        doTest();
    }

    public void testConcatenationInsideAppend() {
        doTest();
    }

    @Override
    protected String getIntentionName() {
        return IntentionPowerPackLocalize.replaceConcatenationWithStringBuilderIntentionName().get();
    }

    @Override
    protected String getRelativePath() {
        return "concatenation/string_builder";
    }
}
