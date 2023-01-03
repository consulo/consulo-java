/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.fqnames;

import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ImportUtils;

class FullyQualifiedNamePredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaCodeReferenceElement)) {
      return false;
    }
    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
    if (!referenceElement.isQualified()) {
      return false;
    }
    final PsiElement parent = referenceElement.getParent();
    if (parent instanceof PsiMethodCallExpression || parent instanceof PsiAssignmentExpression || parent instanceof PsiVariable) {
      return false;
    }
    if (PsiTreeUtil.getParentOfType(element, PsiImportStatementBase.class, PsiPackageStatement.class, JavaCodeFragment.class) != null) {
      return false;
    }
    final PsiElement qualifier = referenceElement.getQualifier();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement)) {
      return false;
    }
    final PsiJavaCodeReferenceElement qualifierReferenceElement = (PsiJavaCodeReferenceElement)qualifier;
    final PsiElement resolved = qualifierReferenceElement.resolve();
    if (!(resolved instanceof PsiJavaPackage)) {
      if (!(resolved instanceof PsiClass)) {
        return false;
      }
      final Project project = element.getProject();
      final JavaCodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
      if (!codeStyleSettings.INSERT_INNER_CLASS_IMPORTS) {
        return false;
      }
    }
    final PsiElement target = referenceElement.resolve();
    if (!(target instanceof PsiClass)) {
      return false;
    }
    final PsiClass aClass = (PsiClass)target;
    final String fqName = aClass.getQualifiedName();
    if (fqName == null) {
      return false;
    }
    return ImportUtils.nameCanBeImported(fqName, element);
  }
}