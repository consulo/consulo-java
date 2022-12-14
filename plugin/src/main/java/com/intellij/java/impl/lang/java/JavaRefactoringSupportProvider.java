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
package com.intellij.java.impl.lang.java;

import com.intellij.java.impl.refactoring.changeSignature.JavaChangeSignatureHandler;
import com.intellij.java.impl.refactoring.extractInterface.ExtractInterfaceHandler;
import com.intellij.java.impl.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.java.impl.refactoring.extractSuperclass.ExtractSuperclassHandler;
import com.intellij.java.impl.refactoring.extractclass.ExtractClassHandler;
import com.intellij.java.impl.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.java.impl.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.java.impl.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.java.impl.refactoring.memberPullUp.JavaPullUpHandler;
import com.intellij.java.impl.refactoring.memberPushDown.JavaPushDownHandler;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.light.LightMethodBuilder;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.scope.SearchScope;
import consulo.language.Language;
import consulo.language.editor.refactoring.RefactoringSupportProvider;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.util.PsiTreeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author ven
 */
@ExtensionImpl
public class JavaRefactoringSupportProvider extends RefactoringSupportProvider {
  @Override
  public boolean isSafeDeleteAvailable(PsiElement element) {
    return element instanceof PsiClass || element instanceof PsiMethod || element instanceof PsiField ||
        (element instanceof PsiParameter && ((PsiParameter) element).getDeclarationScope() instanceof PsiMethod) ||
        element instanceof PsiJavaPackage || element instanceof PsiLocalVariable;
  }

  @Override
  public RefactoringActionHandler getIntroduceConstantHandler() {
    return new IntroduceConstantHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceFieldHandler() {
    return new IntroduceFieldHandler();
  }

  @Override
  public boolean isInplaceRenameAvailable(final PsiElement element, final PsiElement context) {
    return mayRenameInplace(element, context);
  }

  @Override
  public boolean isMemberInplaceRenameAvailable(PsiElement elementToRename, PsiElement context) {
    if (isDisableRefactoringForLightElement(elementToRename)) {
      return false;
    }
    return elementToRename instanceof PsiMember;
  }

  @Override
  public RefactoringActionHandler getIntroduceVariableHandler() {
    return new IntroduceVariableHandler();
  }

  @Override
  @Nullable
  public RefactoringActionHandler getExtractMethodHandler() {
    return new ExtractMethodHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceParameterHandler() {
    return new IntroduceParameterHandler();
  }

  @Override
  public RefactoringActionHandler getPullUpHandler() {
    return new JavaPullUpHandler();
  }

  @Override
  public RefactoringActionHandler getPushDownHandler() {
    return new JavaPushDownHandler();
  }

  @Override
  public RefactoringActionHandler getExtractInterfaceHandler() {
    return new ExtractInterfaceHandler();
  }

  @Override
  public RefactoringActionHandler getExtractSuperClassHandler() {
    return new ExtractSuperclassHandler();
  }

  @Override
  public ChangeSignatureHandler getChangeSignatureHandler() {
    return new JavaChangeSignatureHandler();
  }

  @Override
  public RefactoringActionHandler getExtractClassHandler() {
    return new ExtractClassHandler();
  }

  @Override
  public boolean isInplaceIntroduceAvailable(PsiElement element, PsiElement context) {
    if (!(element instanceof PsiExpression)) {
      return false;
    }
    if (context == null || context.getContainingFile() != element.getContainingFile()) {
      return false;
    }
    return true;
  }

  public static boolean mayRenameInplace(@Nonnull PsiElement elementToRename, final PsiElement nameSuggestionContext) {
    if (isDisableRefactoringForLightElement(elementToRename)) {
      return false;
    }

    if (nameSuggestionContext != null && nameSuggestionContext.getContainingFile() != elementToRename.getContainingFile()) {
      return false;
    }
    if (!(elementToRename instanceof PsiLocalVariable) &&
        !(elementToRename instanceof PsiParameter) &&
        !(elementToRename instanceof PsiLabeledStatement)) {
      return false;
    }
    SearchScope useScope = PsiSearchHelper.SERVICE.getInstance(elementToRename.getProject()).getUseScope(elementToRename);
    if (!(useScope instanceof LocalSearchScope)) {
      return false;
    }
    PsiElement[] scopeElements = ((LocalSearchScope) useScope).getScope();
    if (scopeElements.length > 1 &&                          // assume there are no elements with use scopes with holes in them
        !isElementWithComment(scopeElements) &&              // ... except a case of element and it's doc comment
        !isResourceVariable(scopeElements)) {
      return false;    // ... and badly scoped resource variables
    }
    PsiFile containingFile = elementToRename.getContainingFile();
    return PsiTreeUtil.isAncestor(containingFile, scopeElements[0], false);
  }

  public static boolean isDisableRefactoringForLightElement(@Nullable PsiElement element) {
    if (element instanceof LightMethodBuilder) {
      return true;
    }
    return false;
  }

  private static boolean isElementWithComment(final PsiElement[] scopeElements) {
    if (scopeElements.length > 2) {
      return false;
    }

    PsiDocComment comment = null;
    PsiDocCommentOwner owner = null;
    for (PsiElement element : scopeElements) {
      if (element instanceof PsiDocComment) {
        comment = (PsiDocComment) element;
      } else if (element instanceof PsiDocCommentOwner) {
        owner = (PsiDocCommentOwner) element;
      }
    }

    return comment != null && comment.getOwner() == owner;
  }

  private static boolean isResourceVariable(final PsiElement[] scopeElements) {
    return scopeElements.length == 2 &&
        scopeElements[0] instanceof PsiResourceList &&
        scopeElements[1] instanceof PsiCodeBlock;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
