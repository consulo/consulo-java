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
package com.intellij.java.impl.codeInsight.highlighting;

import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.language.LangBundle;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerBase;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.project.Project;
import consulo.util.lang.function.Condition;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class HighlightExceptionsHandler extends HighlightUsagesHandlerBase<PsiClass> {
  private final PsiElement myTarget;
  private final PsiClassType[] myClassTypes;
  private final PsiElement myPlace;
  private final Condition<PsiType> myTypeFilter;

  public HighlightExceptionsHandler(
    final Editor editor,
    final PsiFile file,
    final PsiElement target,
    final PsiClassType[] classTypes,
    final PsiElement place,
    final Condition<PsiType> typeFilter
  ) {
    super(editor, file);
    myTarget = target;
    myClassTypes = classTypes;
    myPlace = place;
    myTypeFilter = typeFilter;
  }

  @Override
  public List<PsiClass> getTargets() {
    return ChooseClassAndDoHighlightRunnable.resolveClasses(myClassTypes);
  }

  @Override
  protected void selectTargets(final List<PsiClass> targets, final Consumer<List<PsiClass>> selectionConsumer) {
    new ChooseClassAndDoHighlightRunnable(myClassTypes, myEditor, CodeInsightLocalize.highlightExceptionsThrownChooserTitle().get()) {
      @Override
      protected void selected(PsiClass... classes) {
        selectionConsumer.accept(Arrays.asList(classes));
      }
    }.run();
  }

  @Override
  @RequiredReadAction
  public void computeUsages(final List<PsiClass> targets) {
    final Project project = myEditor.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

    addOccurrence(myTarget);
    for (PsiClass aClass : targets) {
      addExceptionThrownPlaces(factory.createType(aClass));
    }
    buildStatusText(LangBundle.message("java.terms.exception"), myReadUsages.size() - 1 /* exclude target */);
  }

  private void addExceptionThrownPlaces(final PsiType type) {
    if (type instanceof PsiClassType) {
      myPlace.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);
        }

        @Override
        @RequiredReadAction
        public void visitThrowStatement(@Nonnull PsiThrowStatement statement) {
          super.visitThrowStatement(statement);
          final List<PsiClassType> actualTypes = ExceptionUtil.getUnhandledExceptions(statement, myPlace);
          for (PsiClassType actualType : actualTypes) {
            if (actualType != null && type.isAssignableFrom(actualType) && myTypeFilter.value(actualType)) {
              PsiExpression psiExpression = statement.getException();
              if (psiExpression instanceof PsiReferenceExpression) {
                addOccurrence(psiExpression);
              } else if (psiExpression instanceof PsiNewExpression newExpression) {
                PsiJavaCodeReferenceElement ref = newExpression.getClassReference();
                addOccurrence(ref);
              } else {
                addOccurrence(statement.getException());
              }
            }
          }
        }

        @Override
        @RequiredReadAction
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          PsiReference reference = expression.getMethodExpression().getReference();
          if (reference == null) return;
          List<PsiClassType> exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, myPlace);
          for (final PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && myTypeFilter.value(actualType)) {
              addOccurrence(expression.getMethodExpression());
              break;
            }
          }
        }

        @Override
        @RequiredReadAction
        public void visitNewExpression(@Nonnull PsiNewExpression expression) {
          super.visitNewExpression(expression);
          PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
          if (classReference == null) return;
          List<PsiClassType> exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, myPlace);
          for (PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && myTypeFilter.value(actualType)) {
              addOccurrence(classReference);
              break;
            }
          }
        }
      });
    }
  }
}
