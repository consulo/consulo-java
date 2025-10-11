/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.maturity;

import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiComment;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class TodoCommentInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.todoCommentDisplayName();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.todoCommentProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ClassWithoutToStringVisitor();
  }

  private static class ClassWithoutToStringVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitComment(PsiComment comment) {
      super.visitComment(comment);
      if (TodoUtil.isTodoComment(comment)) {
        registerError(comment);
      }
    }
  }
}