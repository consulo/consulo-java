/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author mike
 */
public class AddExceptionToThrowsFix extends BaseIntentionAction implements SyntheticIntentionAction {
  private final PsiElement myWrongElement;

  public AddExceptionToThrowsFix(@Nonnull PsiElement wrongElement) {
    myWrongElement = wrongElement;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@Nonnull final Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
      return;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final List<PsiClassType> exceptions = new ArrayList<>();
    final PsiMethod targetMethod = collectExceptions(exceptions);
    if (targetMethod == null) {
      return;
    }

    Set<PsiClassType> unhandledExceptions = new HashSet<>(exceptions);

    addExceptionsToThrowsList(project, targetMethod, unhandledExceptions);
  }

  static void addExceptionsToThrowsList(@Nonnull final Project project,
                                        @Nonnull final PsiMethod targetMethod,
                                        @Nonnull final Set<PsiClassType> unhandledExceptions) {
    final PsiMethod[] superMethods = getSuperMethods(targetMethod);

    boolean hasSuperMethodsWithoutExceptions = hasSuperMethodsWithoutExceptions(superMethods, unhandledExceptions);

    final boolean processSuperMethods;
    if (hasSuperMethodsWithoutExceptions && superMethods.length > 0) {
      int result =
        ApplicationManager.getApplication().isUnitTestMode() ? Messages.YES : Messages.showYesNoCancelDialog(JavaQuickFixBundle.message(
          "add.exception.to.throws.inherited.method.warning" +
            ".text",
          targetMethod.getName()), JavaQuickFixBundle.message("method.is.inherited.warning.title"), Messages.getQuestionIcon());

      if (result == Messages.YES) {
        processSuperMethods = true;
      }
      else if (result == Messages.NO) {
        processSuperMethods = false;
      }
      else {
        return;
      }
    }
    else {
      processSuperMethods = false;
    }

    List<PsiElement> toModify = new ArrayList<>();
    toModify.add(targetMethod);
    if (processSuperMethods) {
      Collections.addAll(toModify, superMethods);
    }
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(toModify)) {
      return;
    }
    WriteAction.run(() ->
                    {
                      processMethod(project, targetMethod, unhandledExceptions);

                      if (processSuperMethods) {
                        for (PsiMethod superMethod : superMethods) {
                          processMethod(project, superMethod, unhandledExceptions);
                        }
                      }
                    });
  }

  @Nonnull
  private static PsiMethod[] getSuperMethods(@Nonnull PsiMethod targetMethod) {
    List<PsiMethod> result = new ArrayList<>();
    collectSuperMethods(targetMethod, result);
    return result.toArray(new PsiMethod[result.size()]);
  }

  private static void collectSuperMethods(@Nonnull PsiMethod method, @Nonnull List<PsiMethod> result) {
    PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      result.add(superMethod);
      collectSuperMethods(superMethod, result);
    }
  }

  private static boolean hasSuperMethodsWithoutExceptions(@Nonnull PsiMethod[] superMethods,
                                                          @Nonnull Set<PsiClassType> unhandledExceptions) {
    for (PsiMethod superMethod : superMethods) {
      PsiClassType[] referencedTypes = superMethod.getThrowsList().getReferencedTypes();

      Set<PsiClassType> exceptions = new HashSet<>(unhandledExceptions);
      for (PsiClassType referencedType : referencedTypes) {
        for (PsiClassType exception : unhandledExceptions) {
          if (referencedType.isAssignableFrom(exception)) {
            exceptions.remove(exception);
          }
        }
      }

      if (!exceptions.isEmpty()) {
        return true;
      }
    }

    return false;
  }

  private static void processMethod(@Nonnull Project project,
                                    @Nonnull PsiMethod targetMethod,
                                    @Nonnull Set<PsiClassType> unhandledExceptions) throws IncorrectOperationException {
    for (PsiClassType unhandledException : unhandledExceptions) {
      PsiClass exceptionClass = unhandledException.resolve();
      if (exceptionClass != null) {
        PsiUtil.addException(targetMethod, exceptionClass);
      }
    }

    CodeStyleManager.getInstance(project).reformat(targetMethod.getThrowsList());
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }
    if (!myWrongElement.isValid()) {
      return false;
    }

    final List<PsiClassType> unhandled = new ArrayList<>();
    if (collectExceptions(unhandled) == null) {
      return false;
    }

    setText(JavaQuickFixBundle.message("add.exception.to.throws.text", unhandled.size()));
    return true;
  }

  @Nullable
  private PsiMethod collectExceptions(List<PsiClassType> unhandled) {
    PsiElement targetElement = null;
    PsiMethod targetMethod = null;

    final PsiElement psiElement = myWrongElement instanceof PsiMethodReferenceExpression ? myWrongElement : PsiTreeUtil.getParentOfType(
      myWrongElement,
      PsiFunctionalExpression.class,
      PsiMethod
        .class);
    if (psiElement instanceof PsiFunctionalExpression) {
      targetMethod = LambdaUtil.getFunctionalInterfaceMethod(psiElement);
      targetElement = psiElement instanceof PsiLambdaExpression ? ((PsiLambdaExpression)psiElement).getBody() : psiElement;
    }
    else if (psiElement instanceof PsiMethod) {
      targetMethod = (PsiMethod)psiElement;
      targetElement = psiElement;
    }

    if (targetElement == null || targetMethod == null || !targetMethod.getThrowsList().isPhysical()) {
      return null;
    }
    List<PsiClassType> exceptions = getUnhandledExceptions(myWrongElement, targetElement, targetMethod);
    if (exceptions == null || exceptions.isEmpty()) {
      return null;
    }
    unhandled.addAll(exceptions);
    return targetMethod;
  }

  @Nullable
  private static List<PsiClassType> getUnhandledExceptions(@Nullable PsiElement element, PsiElement topElement, PsiMethod targetMethod) {
    if (element == null || element == topElement && !(topElement instanceof PsiMethodReferenceExpression)) {
      return null;
    }
    List<PsiClassType> unhandledExceptions = ExceptionUtil.getUnhandledExceptions(element);
    if (!filterInProjectExceptions(targetMethod, unhandledExceptions).isEmpty()) {
      return unhandledExceptions;
    }
    if (topElement instanceof PsiMethodReferenceExpression) {
      return null;
    }
    return getUnhandledExceptions(element.getParent(), topElement, targetMethod);
  }

  @Nonnull
  private static Set<PsiClassType> filterInProjectExceptions(@Nullable PsiMethod targetMethod,
                                                             @Nonnull List<PsiClassType> unhandledExceptions) {
    if (targetMethod == null) {
      return Collections.emptySet();
    }

    Set<PsiClassType> result = new HashSet<>();

    if (targetMethod.getManager().isInProject(targetMethod)) {
      PsiMethod[] superMethods = targetMethod.findSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        Set<PsiClassType> classTypes = filterInProjectExceptions(superMethod, unhandledExceptions);
        result.addAll(classTypes);
      }

      if (superMethods.length == 0) {
        result.addAll(unhandledExceptions);
      }
    }
    else {
      PsiClassType[] referencedTypes = targetMethod.getThrowsList().getReferencedTypes();
      for (PsiClassType referencedType : referencedTypes) {
        PsiClass psiClass = referencedType.resolve();
        if (psiClass == null) {
          continue;
        }
        for (PsiClassType exception : unhandledExceptions) {
          if (referencedType.isAssignableFrom(exception)) {
            result.add(exception);
          }
        }
      }
    }

    return result;
  }
}
