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
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.engine.ContextUtil;
import com.intellij.java.debugger.engine.StackFrameContext;
import consulo.application.ApplicationManager;
import consulo.document.Document;
import consulo.project.Project;
import consulo.application.util.function.Computable;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.CharArrayUtil;
import jakarta.annotation.Nullable;

/**
 * User: lex
 * Date: Oct 29, 2003
 * Time: 9:29:36 PM
 */
public class PositionUtil extends ContextUtil {
  public static SourcePosition getSourcePosition(final StackFrameContext context) {
    if(context instanceof DebuggerContextImpl) return ((DebuggerContextImpl)context).getSourcePosition();

    return ContextUtil.getSourcePosition(context);
  }

  public static PsiElement getContextElement(final StackFrameContext context) {
    if(context instanceof DebuggerContextImpl) return ((DebuggerContextImpl) context).getContextElement();

    return ContextUtil.getContextElement(context);
  }

  @Nullable
  public static <T extends PsiElement> T getPsiElementAt(final Project project, final Class<T> expectedPsiElementClass, final SourcePosition sourcePosition) {
    return ApplicationManager.getApplication().runReadAction(new Computable<T>() {
      public T compute() {
        final PsiFile psiFile = sourcePosition.getFile();
        final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
        if(document == null) {
          return null;
        }
        final int spOffset = sourcePosition.getOffset();
        if (spOffset < 0) {
          return null;
        }
        final int offset = CharArrayUtil.shiftForward(document.getCharsSequence(), spOffset, " \t");
        return PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), expectedPsiElementClass, false);
      }
    });
  }
}
