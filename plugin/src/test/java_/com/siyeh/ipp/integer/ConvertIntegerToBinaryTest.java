/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.siyeh.ipp.integer;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

public abstract class ConvertIntegerToBinaryTest extends IPPTestCase {
  public void testDecToBin1() { doTest(); }
  public void testDecToBin2() { doTest(); }
  public void testHexToBin1() { doTest(); }
  public void testHexToBin2() { doTest(); }
  public void testOctToBin1() { doTest(); }
  public void testOctToBin2() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("convert.integer.to.binary.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "integer";
  }
}
