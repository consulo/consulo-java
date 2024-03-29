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
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.impl.engine.evaluation.TextWithImportsImpl;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class JavaEditorTextProviderImpl implements EditorTextProvider {
  private static final Logger LOG = Logger.getInstance(JavaEditorTextProviderImpl.class);

  @Override
  public TextWithImports getEditorText(PsiElement elementAtCaret) {
    String result = null;
    PsiElement element = findExpression(elementAtCaret);
    if (element == null) return null;
    if (element instanceof PsiVariable) {
      result = qualifyEnumConstant(element, ((PsiVariable)element).getName());
    }
    else if (element instanceof PsiMethod) {
      result = ((PsiMethod)element).getName() + "()";
    }
    else if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression reference = (PsiReferenceExpression)element;
      result = qualifyEnumConstant(reference.resolve(), element.getText());
    }
    else {
      result = element.getText();
    }
    return result != null? new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, result) : null;
  }

  @Nullable
  private static PsiElement findExpression(PsiElement element) {
    PsiElement e = PsiTreeUtil.getParentOfType(element, PsiVariable.class, PsiExpression.class, PsiMethod.class);
    if (e instanceof PsiVariable) {
      // return e;
    }
    else if (e instanceof PsiMethod && element.getParent() != e) {
      e = null;
    }
    else if (e instanceof PsiReferenceExpression) {
      if (e.getParent() instanceof PsiCallExpression) {
        e = e.getParent();
      }
      else if (e.getParent() instanceof PsiReferenceExpression) {
        // <caret>System.out case should not return plain class name
        PsiElement resolve = ((PsiReferenceExpression)e).resolve();
        if (resolve instanceof PsiClass) {
          e = e.getParent();
        }
      }
    }
    if (e instanceof PsiNewExpression) {
      // skip new Runnable() { ... }
      if (((PsiNewExpression)e).getAnonymousClass() != null) return null;
    }
    return e;
  }

  @Nullable
  public Pair<PsiElement, TextRange> findExpression(PsiElement element, boolean allowMethodCalls) {
    if (!(element instanceof PsiIdentifier || element instanceof PsiKeyword)) {
      return null;
    }

    PsiElement expression = null;
    PsiElement parent = element.getParent();
    if (parent instanceof PsiVariable) {
      expression = element;
    }
    else if (parent instanceof PsiReferenceExpression) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiCallExpression) {
        parent = pparent;
      }
      if (allowMethodCalls || !DebuggerUtils.hasSideEffects(parent)) {
        expression = parent;
      }
    }
    else if (parent instanceof PsiThisExpression) {
      expression = parent;
    }

    if (expression != null) {
      try {
        PsiElement context = element;
        if(parent instanceof PsiParameter) {
          try {
            context = ((PsiMethod)((PsiParameter)parent).getDeclarationScope()).getBody();
          }
          catch (Throwable ignored) {
          }
        }
        else {
          while(context != null  && !(context instanceof PsiStatement) && !(context instanceof PsiClass)) {
            context = context.getParent();
          }
        }
        TextRange textRange = expression.getTextRange();
        PsiElement psiExpression = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory().createExpressionFromText(expression.getText(), context);
        return Pair.create(psiExpression, textRange);
      }
      catch (IncorrectOperationException e) {
        LOG.debug(e);
      }
    }
    return null;
  }

  @Nullable
  private static String qualifyEnumConstant(PsiElement resolved, @Nullable String def) {
    if (resolved instanceof PsiEnumConstant) {
      final PsiEnumConstant enumConstant = (PsiEnumConstant)resolved;
      final PsiClass enumClass = enumConstant.getContainingClass();
      if (enumClass != null) {
        return enumClass.getName() + "." + enumConstant.getName();
      }
    }
    return def;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
