/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.java.language.impl.psi.controlFlow;

import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

public abstract class SimpleInstruction extends InstructionBase {
  private static final Logger LOG = Logger.getInstance(SimpleInstruction.class);

  @Override
  public int nNext() {
    return 1;
  }

  @Override
  public int getNext(int index, int no) {
    LOG.assertTrue(no == 0);
    return index + 1;
  }

  @Override
  public void accept(@Nonnull ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitSimpleInstruction(this, offset, nextOffset);
  }
}