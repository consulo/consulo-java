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
package com.intellij.java.analysis.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.analysis.codeInspection.SuppressManager;
import consulo.language.editor.inspection.CustomSuppressableInspectionTool;
import consulo.language.editor.inspection.GlobalInspectionTool;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.intention.SuppressIntentionAction;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.psi.PsiElement;

import javax.annotation.Nonnull;

/**
 * Implement this abstract class in order to provide new inspection tool functionality. The major API limitation here is
 * subclasses should be stateless.
 * The other important thing is problem anchors (PsiElements) reported by <code>check&lt;XXX&gt;</code> methods should
 * lie under corresponding first parameter of one method.
 *
 * @see GlobalInspectionTool
 */
public abstract class BaseJavaLocalInspectionTool<State> extends AbstractBaseJavaLocalInspectionTool<State> implements CustomSuppressableInspectionTool {
  @Override
  public SuppressIntentionAction[] getSuppressActions(final PsiElement element) {
    return SuppressManager.getInstance().createSuppressActions(HighlightDisplayKey.find(getShortName()));
  }

  @Override
  public boolean isSuppressedFor(@Nonnull PsiElement element) {
    return isSuppressedFor(element, this);
  }

  public static boolean isSuppressedFor(@Nonnull PsiElement element, @Nonnull LocalInspectionTool tool) {
    return BaseJavaBatchLocalInspectionTool.isSuppressedFor(element, tool);
  }
}
