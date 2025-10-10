/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.forloop;

import com.intellij.java.impl.ipp.forloop.ReplaceForEachLoopWithIteratorForLoopIntention;
import com.siyeh.ipp.IPPTestCase;
import com.siyeh.localize.IntentionPowerPackLocalize;

/**
 * @see ReplaceForEachLoopWithIteratorForLoopIntention
 */
public abstract class ReplaceForEachLoopWithIteratorForLoopIntentionTest extends IPPTestCase {
    public void testBareCollectionLoop() {
        doTest();
    }

    public void testGenericTypes() {
        doTest();
    }

    public void testBoundedTypes() {
        doTest();
    }

    public void testPrecedence() {
        doTest();
    }

    public void testWildcards() {
        doTest();
    }

    @Override
    protected String getIntentionName() {
        return IntentionPowerPackLocalize.replaceForEachLoopWithIteratorForLoopIntentionName().get();
    }

    @Override
    protected String getRelativePath() {
        return "forloop/iterator";
    }
}
