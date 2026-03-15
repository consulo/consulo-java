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

import com.intellij.java.language.psi.*;

class CyclomaticComplexityVisitor extends JavaRecursiveElementVisitor {
  private int m_complexity = 1;

  @Override
  public void visitAnonymousClass(PsiAnonymousClass aClass) {
    // to call to super, to keep this from drilling down
  }

  @Override
  public void visitForStatement(PsiForStatement statement) {
    super.visitForStatement(statement);
    m_complexity++;
  }

  @Override
  public void visitForeachStatement(PsiForeachStatement statement) {
    super.visitForeachStatement(statement);
    m_complexity++;
  }

  @Override
  public void visitIfStatement(PsiIfStatement statement) {
    super.visitIfStatement(statement);
    m_complexity++;
  }

  @Override
  public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    super.visitDoWhileStatement(statement);
    m_complexity++;
  }

  @Override
  public void visitConditionalExpression(PsiConditionalExpression expression) {
    super.visitConditionalExpression(expression);
    m_complexity++;
  }

  @Override
  public void visitSwitchStatement(PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    PsiCodeBlock body = statement.getBody();
    if (body == null) {
      return;
    }
    PsiStatement[] statements = body.getStatements();
    boolean pendingLabel = false;
    for (PsiStatement child : statements) {
      if (child instanceof PsiSwitchLabelStatement) {
        if (!pendingLabel) {
          m_complexity++;
        }
        pendingLabel = true;
      }
      else {
        pendingLabel = false;
      }
    }
  }

  @Override
  public void visitWhileStatement(PsiWhileStatement statement) {
    super.visitWhileStatement(statement);
    m_complexity++;
  }

  public int getComplexity() {
    return m_complexity;
  }
}
