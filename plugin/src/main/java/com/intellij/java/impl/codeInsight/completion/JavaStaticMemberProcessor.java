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
package com.intellij.java.impl.codeInsight.completion;

import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.AutoCompletionPolicy;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.externalService.statistic.FeatureUsageTracker;
import com.intellij.java.impl.codeInsight.lookup.VariableLookupItem;
import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author peter
 */
public class JavaStaticMemberProcessor extends StaticMemberProcessor {
  private final PsiElement myOriginalPosition;

  public JavaStaticMemberProcessor(CompletionParameters parameters) {
    super(parameters.getPosition());
    myOriginalPosition = parameters.getOriginalPosition();

    final PsiFile file = parameters.getPosition().getContainingFile();
    if (file instanceof PsiJavaFile) {
      final PsiImportList importList = ((PsiJavaFile) file).getImportList();
      if (importList != null) {
        for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
          importMembersOf(statement.resolveTargetClass());
        }
      }
    }
  }

  @Nonnull
  @Override
  protected LookupElement createLookupElement(@Nonnull PsiMember member, @Nonnull final PsiClass containingClass, boolean shouldImport) {
    shouldImport |= myOriginalPosition != null && PsiTreeUtil.isAncestor(containingClass, myOriginalPosition, false);

    String exprText = member.getName() + (member instanceof PsiMethod ? "()" : "");
    PsiReference ref = JavaPsiFacade.getElementFactory(member.getProject()).createExpressionFromText(exprText, myOriginalPosition).findReferenceAt(0);
    if (ref instanceof PsiReferenceExpression && ((PsiReferenceExpression) ref).multiResolve(true).length > 0) {
      shouldImport = false;
    }

    if (member instanceof PsiMethod) {
      return AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(new GlobalMethodCallElement((PsiMethod) member, shouldImport, false));
    }
    return AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(new VariableLookupItem((PsiField) member, shouldImport) {
      @Override
      public void handleInsert(InsertionContext context) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME);

        super.handleInsert(context);
      }
    });
  }

  @Override
  protected LookupElement createLookupElement(@Nonnull List<PsiMethod> overloads, @Nonnull PsiClass containingClass, boolean shouldImport) {
    shouldImport |= myOriginalPosition != null && PsiTreeUtil.isAncestor(containingClass, myOriginalPosition, false);

    final JavaMethodCallElement element = new GlobalMethodCallElement(overloads.get(0), shouldImport, true);
    JavaCompletionUtil.putAllMethods(element, overloads);
    return element;
  }

  private static class GlobalMethodCallElement extends JavaMethodCallElement {
    public GlobalMethodCallElement(PsiMethod member, boolean shouldImport, boolean mergedOverloads) {
      super(member, shouldImport, mergedOverloads);
    }

    @Override
    public void handleInsert(InsertionContext context) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME);

      super.handleInsert(context);
    }
  }
}
