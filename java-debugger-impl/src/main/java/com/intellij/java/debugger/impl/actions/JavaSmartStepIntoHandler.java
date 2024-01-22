/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.Range;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import java.util.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.11
 */
@ExtensionImpl
public class JavaSmartStepIntoHandler extends JvmSmartStepIntoHandler {
  @Override
  public boolean isAvailable(final SourcePosition position) {
    final PsiFile file = position.getFile();
    return file.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @Override
  @Nonnull
  public List<SmartStepTarget> findSmartStepTargets(final SourcePosition position) {
    final int line = position.getLine();
    if (line < 0) {
      return Collections.emptyList(); // the document has been changed
    }

    final PsiFile file = position.getFile();
    final VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) {
      // the file is not physical
      return Collections.emptyList();
    }

    final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
    if (doc == null) {
      return Collections.emptyList();
    }
    if (line >= doc.getLineCount()) {
      return Collections.emptyList(); // the document has been changed
    }
    final int startOffset = doc.getLineStartOffset(line);
    final TextRange lineRange = new TextRange(startOffset, doc.getLineEndOffset(line));
    final int offset = CharArrayUtil.shiftForward(doc.getCharsSequence(), startOffset, " \t");
    PsiElement element = file.findElementAt(offset);
    if (element != null && !(element instanceof PsiCompiledElement)) {
      do {
        final PsiElement parent = element.getParent();
        if (parent == null || (parent.getTextOffset() < lineRange.getStartOffset())) {
          break;
        }
        element = parent;
      }
      while (true);

      final Set<SmartStepTarget> targets = new LinkedHashSet<SmartStepTarget>();

      final Range<Integer> lines =
        new Range<Integer>(doc.getLineNumber(element.getTextOffset()), doc.getLineNumber(element.getTextOffset() +
                                                                                           element.getTextLength()));

      final PsiElementVisitor methodCollector = new JavaRecursiveElementVisitor() {
        final Stack<PsiMethod> myContextStack = new Stack<PsiMethod>();
        final Stack<String> myParamNameStack = new Stack<String>();
        private int myNextLambdaExpressionOrdinal = 0;

        @Nullable
        private String getCurrentParamName() {
          return myParamNameStack.isEmpty() ? null : myParamNameStack.peek();
        }

        @Override
        public void visitAnonymousClass(PsiAnonymousClass aClass) {
          for (PsiMethod psiMethod : aClass.getMethods()) {
            targets.add(new MethodSmartStepTarget(psiMethod, getCurrentParamName(), psiMethod.getBody(), true, lines));
          }
        }

        public void visitLambdaExpression(PsiLambdaExpression expression) {
          targets.add(new LambdaSmartStepTarget(expression, getCurrentParamName(), expression.getBody(), myNextLambdaExpressionOrdinal++,
                                                lines));
        }

        @Override
        public void visitStatement(PsiStatement statement) {
          if (lineRange.intersects(statement.getTextRange())) {
            super.visitStatement(statement);
          }
        }

        public void visitExpressionList(PsiExpressionList expressionList) {
          final PsiMethod psiMethod = myContextStack.isEmpty() ? null : myContextStack.peek();
          if (psiMethod != null) {
            final String methodName = psiMethod.getName();
            final PsiExpression[] expressions = expressionList.getExpressions();
            final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
            for (int idx = 0; idx < expressions.length; idx++) {
              final String paramName = (idx < parameters.length && !parameters[idx].isVarArgs()) ? parameters[idx].getName() : "arg" +
                (idx + 1);
              myParamNameStack.push(methodName + ": " + paramName + ".");
              final PsiExpression argExpression = expressions[idx];
              try {
                argExpression.accept(this);
              }
              finally {
                myParamNameStack.pop();
              }
            }
          }
          else {
            super.visitExpressionList(expressionList);
          }
        }

        @Override
        public void visitCallExpression(final PsiCallExpression expression) {
          final PsiMethod psiMethod = expression.resolveMethod();
          if (psiMethod != null) {
            myContextStack.push(psiMethod);
            targets.add(new MethodSmartStepTarget(psiMethod, null, expression instanceof PsiMethodCallExpression ? (
              (PsiMethodCallExpression)expression).getMethodExpression().getReferenceNameElement() : expression instanceof
              PsiNewExpression ? ((PsiNewExpression)expression).getClassOrAnonymousClassReference() : expression, false, lines));
          }
          try {
            super.visitCallExpression(expression);
          }
          finally {
            if (psiMethod != null) {
              myContextStack.pop();
            }
          }
        }

      };

      element.accept(methodCollector);
      for (PsiElement sibling = element.getNextSibling(); sibling != null; sibling = sibling.getNextSibling()) {
        if (!lineRange.intersects(sibling.getTextRange())) {
          break;
        }
        sibling.accept(methodCollector);
      }
      return new ArrayList<>(targets);
    }
    return Collections.emptyList();
  }

}
