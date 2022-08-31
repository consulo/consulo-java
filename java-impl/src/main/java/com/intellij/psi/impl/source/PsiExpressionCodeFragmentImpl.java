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
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import consulo.logging.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionCodeFragment;
import com.intellij.java.language.psi.PsiType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nullable;

public class PsiExpressionCodeFragmentImpl extends PsiCodeFragmentImpl implements PsiExpressionCodeFragment {
  private static final Logger LOG = Logger.getInstance(PsiExpressionCodeFragmentImpl.class);
  private PsiType myExpectedType;

  public PsiExpressionCodeFragmentImpl(Project project,
                                       boolean isPhysical,
                                       @NonNls String name,
                                       CharSequence text,
                                       @javax.annotation.Nullable final PsiType expectedType,
                                       @Nullable PsiElement context) {
    super(project, JavaElementType.EXPRESSION_TEXT, isPhysical, name, text, context);
    setExpectedType(expectedType);
  }

  @javax.annotation.Nullable
  @Override
  public PsiExpression getExpression() {
    ASTNode exprChild = calcTreeElement().findChildByType(ElementType.EXPRESSION_BIT_SET);
    if (exprChild == null) return null;
    return (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(exprChild);
  }

  @Override
  public PsiType getExpectedType() {
    PsiType type = myExpectedType;
    if (type != null && !type.isValid()) {
      return null;
    }
    return type;
  }

  @Override
  public void setExpectedType(@javax.annotation.Nullable PsiType type) {
    myExpectedType = type;
    if (type != null) {
      LOG.assertTrue(type.isValid());
    }
  }
}
