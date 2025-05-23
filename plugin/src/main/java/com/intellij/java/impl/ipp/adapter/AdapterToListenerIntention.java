/*
 * Copyright 2009 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.adapter;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.AdapterToListenerIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class AdapterToListenerIntention extends MutablyNamedIntention {

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new AdapterToListenerPredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final String text = element.getText();
    return IntentionPowerPackBundle.message(
      "adapter.to.listener.intention.name", text);
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element)
    throws IncorrectOperationException {
    final PsiElement parent = element.getParent();
    final PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiClass)) {
      return;
    }
    final PsiClass aClass = (PsiClass)grandParent;
    final PsiReferenceList extendsList = aClass.getExtendsList();
    if (extendsList == null) {
      return;
    }
    final PsiJavaCodeReferenceElement[] extendsReferences =
      extendsList.getReferenceElements();
    if (extendsReferences.length != 1) {
      return;
    }
    final PsiJavaCodeReferenceElement extendsReference =
      extendsReferences[0];
    final PsiElement target = extendsReference.resolve();
    if (!(target instanceof PsiClass)) {
      return;
    }
    final PsiClass extendsClass = (PsiClass)target;
    final PsiReferenceList implementsList =
      extendsClass.getImplementsList();
    if (implementsList == null) {
      return;
    }
    final PsiJavaCodeReferenceElement[] implementsReferences =
      implementsList.getReferenceElements();
    final List<PsiJavaCodeReferenceElement> listenerReferences =
      new ArrayList();
    for (PsiJavaCodeReferenceElement implementsReference :
      implementsReferences) {
      final String name = implementsReference.getReferenceName();
      if (name != null && !name.endsWith("Listener")) {
        continue;
      }
      final PsiElement implementsTarget = implementsReference.resolve();
      if (!(implementsTarget instanceof PsiClass)) {
        continue;
      }
      final PsiClass implementsClass = (PsiClass)implementsTarget;
      if (!implementsClass.isInterface()) {
        continue;
      }
      final PsiMethod[] methods = implementsClass.getMethods();
      for (PsiMethod method : methods) {
        final PsiMethod overridingMethod =
          aClass.findMethodBySignature(method, false);
        if (overridingMethod == null) {
          implementMethodInClass(method, aClass);
          continue;
        }
        final PsiMethod[] superMethods =
          overridingMethod.findSuperMethods(implementsClass);
        for (PsiMethod superMethod : superMethods) {
          if (!superMethod.equals(method)) {
            continue;
          }
          removeCallsToSuperMethodFromMethod(overridingMethod,
                                             extendsClass);
        }
      }
      listenerReferences.add(implementsReference);
    }
    extendsReference.delete();
    final PsiReferenceList referenceList = aClass.getImplementsList();
    if (referenceList != null) {
      for (PsiJavaCodeReferenceElement listenerReference :
        listenerReferences) {
        referenceList.add(listenerReference);
      }
    }
  }

  private static void removeCallsToSuperMethodFromMethod(
    @Nonnull PsiMethod overridingMethod,
    @Nonnull PsiClass superClass) {
    final PsiCodeBlock body = overridingMethod.getBody();
    if (body == null) {
      return;
    }
    final PsiStatement[] statements = body.getStatements();
    for (PsiStatement statement : statements) {
      if (!(statement instanceof PsiExpressionStatement)) {
        continue;
      }
      final PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement)statement;
      final PsiExpression expression =
        expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        continue;
      }
      final PsiMethodCallExpression
        methodCallExpression =
        (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiSuperExpression)) {
        continue;
      }
      final PsiMethod targetMethod =
        methodCallExpression.resolveMethod();
      if (targetMethod == null) {
        continue;
      }
      final PsiClass containingClass = targetMethod.getContainingClass();
      if (!superClass.equals(containingClass)) {
        continue;
      }
      statement.delete();
    }
  }

  private static void implementMethodInClass(@Nonnull PsiMethod method,
                                             @Nonnull PsiClass aClass) {
    final PsiMethod newMethod = (PsiMethod)aClass.add(method);
    final PsiDocComment comment = newMethod.getDocComment();
    if (comment != null) {
      comment.delete();
    }
    final PsiModifierList modifierList = newMethod.getModifierList();
    modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
    final Project project = aClass.getProject();
    final JavaCodeStyleSettings codeStyleSettings =
      CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
    if (codeStyleSettings.INSERT_OVERRIDE_ANNOTATION &&
        PsiUtil.isLanguageLevel6OrHigher(aClass)) {
      modifierList.addAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
    }
    final PsiElementFactory factory =
      JavaPsiFacade.getElementFactory(project);
    final PsiCodeBlock codeBlock = factory.createCodeBlock();
    newMethod.add(codeBlock);
  }
}