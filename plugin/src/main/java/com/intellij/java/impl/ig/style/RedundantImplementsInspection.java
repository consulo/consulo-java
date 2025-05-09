/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiReferenceList;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class RedundantImplementsInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSerializable = false;
  @SuppressWarnings({"PublicField"})
  public boolean ignoreCloneable = false;

  @Override
  @Nonnull
  public String getID() {
    return "RedundantInterfaceDeclaration";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.redundantImplementsDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.redundantImplementsProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel checkboxOptionsPanel = new MultipleCheckboxOptionsPanel(this);
    checkboxOptionsPanel.addCheckbox(InspectionGadgetsLocalize.ignoreSerializableOption().get(), "ignoreSerializable");
    checkboxOptionsPanel.addCheckbox(InspectionGadgetsLocalize.ignoreCloneableOption().get(), "ignoreCloneable");
    return checkboxOptionsPanel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RedundantImplementsFix();
  }

  private static class RedundantImplementsFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.redundantImplementsRemoveQuickfix().get();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement implementReference = descriptor.getPsiElement();
      deleteElement(implementReference);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantImplementsVisitor();
  }

  private class RedundantImplementsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      if (aClass.isAnnotationType()) {
        return;
      }
      if (aClass.isInterface()) {
        checkInterface(aClass);
      }
      else {
        checkConcreteClass(aClass);
      }
    }

    private void checkInterface(PsiClass aClass) {
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] extendsElements =
        extendsList.getReferenceElements();
      for (final PsiJavaCodeReferenceElement extendsElement :
        extendsElements) {
        final PsiElement referent = extendsElement.resolve();
        if (!(referent instanceof PsiClass)) {
          continue;
        }
        final PsiClass extendedInterface = (PsiClass)referent;
        checkExtendedInterface(extendedInterface, extendsElement,
                               extendsElements);
      }
    }

    private void checkConcreteClass(PsiClass aClass) {
      final PsiReferenceList extendsList = aClass.getExtendsList();
      final PsiReferenceList implementsList = aClass.getImplementsList();
      if (extendsList == null || implementsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] extendsElements =
        extendsList.getReferenceElements();
      final PsiJavaCodeReferenceElement[] implementsElements =
        implementsList.getReferenceElements();
      for (final PsiJavaCodeReferenceElement implementsElement :
        implementsElements) {
        final PsiElement referent = implementsElement.resolve();
        if (!(referent instanceof PsiClass)) {
          continue;
        }
        final PsiClass implementedClass = (PsiClass)referent;
        checkImplementedClass(implementedClass, implementsElement,
                              extendsElements, implementsElements);
      }
    }

    private void checkImplementedClass(
      PsiClass implementedClass,
      PsiJavaCodeReferenceElement implementsElement,
      PsiJavaCodeReferenceElement[] extendsElements,
      PsiJavaCodeReferenceElement[] implementsElements) {
      final String qualifiedName = implementedClass.getQualifiedName();
      if (ignoreSerializable && CommonClassNames.JAVA_IO_SERIALIZABLE.equals(qualifiedName)) {
        return;
      }
      else if (ignoreCloneable && CommonClassNames.JAVA_LANG_CLONEABLE.equals(qualifiedName)) {
        return;
      }
      for (final PsiJavaCodeReferenceElement extendsElement :
        extendsElements) {
        final PsiElement extendsReferent = extendsElement.resolve();
        if (!(extendsReferent instanceof PsiClass)) {
          continue;
        }
        final PsiClass extendedClass = (PsiClass)extendsReferent;
        if (extendedClass.isInheritor(implementedClass, true)) {
          registerError(implementsElement);
          return;
        }
      }
      for (final PsiJavaCodeReferenceElement testImplementElement :
        implementsElements) {
        if (testImplementElement.equals(implementsElement)) {
          continue;
        }
        final PsiElement implementsReferent =
          testImplementElement.resolve();
        if (!(implementsReferent instanceof PsiClass)) {
          continue;
        }
        final PsiClass testImplementedClass =
          (PsiClass)implementsReferent;
        if (testImplementedClass.isInheritor(implementedClass, true)) {
          registerError(implementsElement);
          return;
        }
      }
    }

    private void checkExtendedInterface(
      PsiClass extendedInterface,
      PsiJavaCodeReferenceElement extendsElement,
      PsiJavaCodeReferenceElement[] extendsElements) {
      for (final PsiJavaCodeReferenceElement testExtendsElement :
        extendsElements) {
        if (testExtendsElement.equals(extendsElement)) {
          continue;
        }
        final PsiElement implementsReferent =
          testExtendsElement.resolve();
        if (!(implementsReferent instanceof PsiClass)) {
          continue;
        }
        final PsiClass testExtendedInterface =
          (PsiClass)implementsReferent;
        if (testExtendedInterface.isInheritor(extendedInterface, true)) {
          registerError(extendsElement);
          return;
        }
      }
    }
  }
}