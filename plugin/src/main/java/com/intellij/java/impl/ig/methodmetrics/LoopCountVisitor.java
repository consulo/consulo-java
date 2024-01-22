/*
 * Copyright 2003-2005 Dave Griffith
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
package com.intellij.java.impl.ig.methodmetrics;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.*;

class LoopCountVisitor extends JavaRecursiveElementVisitor {
  private int m_count = 0;


  @Override
  public void visitForStatement(@Nonnull PsiForStatement psiForStatement) {
    super.visitForStatement(psiForStatement);
    m_count++;
  }

  @Override
  public void visitForeachStatement(@Nonnull PsiForeachStatement psiForStatement) {
    super.visitForeachStatement(psiForStatement);
    m_count++;
  }

  @Override
  public void visitWhileStatement(@Nonnull PsiWhileStatement psiWhileStatement) {
    super.visitWhileStatement(psiWhileStatement);
    m_count++;
  }

  @Override
  public void visitDoWhileStatement(@Nonnull PsiDoWhileStatement psiDoWhileStatement) {
    super.visitDoWhileStatement(psiDoWhileStatement);
    m_count++;
  }

  @Override
  public void visitAnonymousClass(@Nonnull PsiAnonymousClass aClass) {
    // no call to super, to keep it from drilling into anonymous classes
  }

  public int getCount() {
    return m_count;
  }
}
