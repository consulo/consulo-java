
package com.siyeh.ipp.concatenation;

import com.siyeh.ipp.IPPTestCase;
import com.siyeh.localize.IntentionPowerPackLocalize;

/**
 * @author Bas Leijdekkers
 */
public abstract class MergeCallSequenceToChainIntentionTest extends IPPTestCase {

    public void testAppend() {
        doTest();
    }

    @Override
    protected String getIntentionName() {
        return IntentionPowerPackLocalize.mergeCallSequenceToChainIntentionName().get();
    }

    @Override
    protected String getRelativePath() {
        return "concatenation/merge_sequence";
    }
}
