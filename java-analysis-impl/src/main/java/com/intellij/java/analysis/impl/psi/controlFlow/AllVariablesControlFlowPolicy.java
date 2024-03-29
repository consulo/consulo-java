/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 6, 2002
 * Time: 6:16:17 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.analysis.impl.psi.controlFlow;

import com.intellij.java.language.impl.psi.controlFlow.ControlFlowPolicy;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiVariable;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

public class AllVariablesControlFlowPolicy implements ControlFlowPolicy {
  private static final AllVariablesControlFlowPolicy INSTANCE = new AllVariablesControlFlowPolicy();

  @Override
  public PsiVariable getUsedVariable(@Nonnull PsiReferenceExpression refExpr) {
    PsiElement resolved = refExpr.resolve();
    return resolved instanceof PsiVariable ? (PsiVariable) resolved : null;
  }

  @Override
  public boolean isParameterAccepted(@Nonnull PsiParameter psiParameter) {
    return true;
  }

  @Override
  public boolean isLocalVariableAccepted(@Nonnull PsiLocalVariable psiVariable) {
    return true;
  }

  public static AllVariablesControlFlowPolicy getInstance() {
    return INSTANCE;
  }
}
