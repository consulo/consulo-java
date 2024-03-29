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

class NCSSVisitor extends JavaRecursiveElementVisitor {
  private int m_statementCount = 0;

  @Override
  public void visitAnonymousClass(@Nonnull PsiAnonymousClass aClass) {
    // to call to super, to keep this from drilling down
  }

  @Override
  public void visitStatement(@Nonnull PsiStatement statement) {
    super.visitStatement(statement);
    if (statement instanceof PsiEmptyStatement ||
        statement instanceof PsiBlockStatement) {
      return;
    }
    m_statementCount++;
  }

  public int getStatementCount() {
    return m_statementCount;
  }
}
