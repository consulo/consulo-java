/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.inheritance;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiReferenceList;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;

public class ExtendsAnnotationInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "ClassExplicitlyAnnotation";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "extends.annotation.display.name");
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiClass containingClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message(
      "extends.annotation.problem.descriptor",
      containingClass.getName());
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ExtendsAnnotationVisitor();
  }

  private static class ExtendsAnnotationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      if (!PsiUtil.isLanguageLevel5OrHigher(aClass)) {
        return;
      }
      if (aClass.isAnnotationType()) {
        return;
      }
      final PsiReferenceList extendsList = aClass.getExtendsList();
      checkReferenceList(extendsList, aClass);
      final PsiReferenceList implementsList = aClass.getImplementsList();
      checkReferenceList(implementsList, aClass);
    }

    private void checkReferenceList(PsiReferenceList referenceList,
                                    PsiClass containingClass) {
      if (referenceList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] elements =
        referenceList.getReferenceElements();
      for (final PsiJavaCodeReferenceElement element : elements) {
        final PsiElement referent = element.resolve();
        if (!(referent instanceof PsiClass)) {
          continue;
        }
        final PsiClass psiClass = (PsiClass)referent;
        psiClass.isAnnotationType();
        if (psiClass.isAnnotationType()) {
          registerError(element, containingClass);
        }
      }
    }
  }
}