/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.java.language.psi.PsiExpression;

public class ConditionalThrowToInstruction extends ConditionalBranchingInstruction {

  public ConditionalThrowToInstruction(int offset, PsiExpression expression) {
    super(offset, expression, Role.END);
  }

  public ConditionalThrowToInstruction(final int offset) {
    this(offset, null);
  }

  public String toString() {
    return "COND_THROW_TO " + offset;
  }

  @Override
  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitConditionalThrowToInstruction(this, offset, nextOffset);
  }
}
