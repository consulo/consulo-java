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
package com.intellij.java.impl.ig.classlayout;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

@ExtensionImpl
public class ListenerMayUseAdapterInspection extends BaseInspection {

  public boolean checkForEmptyMethods = true;

  @Override
  @Nls
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.listenerMayUseAdapterDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    final String className = aClass.getName();
    final PsiClass adapterClass = (PsiClass)infos[1];
    final String adapterName = adapterClass.getName();
    return InspectionGadgetsLocalize.listenerMayUseAdapterProblemDescriptor(className, adapterName).get();
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.listenerMayUseAdapterEmtpyMethodsOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "checkForEmptyMethods");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass adapterClass = (PsiClass)infos[1];
    return new ListenerMayUseAdapterFix(adapterClass);
  }

  private static class ListenerMayUseAdapterFix extends InspectionGadgetsFix {

    private final PsiClass adapterClass;

    ListenerMayUseAdapterFix(@Nonnull PsiClass adapterClass) {
      this.adapterClass = adapterClass;
    }

    @Nonnull
    @RequiredReadAction
    public String getName() {
      return InspectionGadgetsLocalize.listenerMayUseAdapterQuickfix(adapterClass.getName()).get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiJavaCodeReferenceElement element = (PsiJavaCodeReferenceElement)descriptor.getPsiElement();
      final PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (aClass == null) {
        return;
      }
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiMethod[] methods = aClass.getMethods();
      if (methods.length > 0) {
        final PsiElement target = element.resolve();
        if (!(target instanceof PsiClass)) {
          return;
        }
        final PsiClass interfaceClass = (PsiClass)target;
        for (PsiMethod method : methods) {
          final PsiCodeBlock body = method.getBody();
          if (body == null) {
            continue;
          }
          final PsiStatement[] statements = body.getStatements();
          if (statements.length != 0) {
            continue;
          }
          final PsiMethod[] superMethods = method.findSuperMethods(
            interfaceClass);
          if (superMethods.length > 0) {
            method.delete();
          }
        }
      }
      element.delete();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory elementFactory =
        psiFacade.getElementFactory();
      final PsiJavaCodeReferenceElement referenceElement =
        elementFactory.createClassReferenceElement(adapterClass);
      extendsList.add(referenceElement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ListenerMayUseAdapterVisitor();
  }

  private class ListenerMayUseAdapterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(PsiClass aClass) {
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] extendsReferences =
        extendsList.getReferenceElements();
      if (extendsReferences.length > 0) {
        return;
      }
      final PsiReferenceList implementsList = aClass.getImplementsList();
      if (implementsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] implementsReferences =
        implementsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement implementsReference :
        implementsReferences) {
        checkReference(aClass, implementsReference);
      }
    }

    private void checkReference(
      @Nonnull PsiClass aClass,
      @Nonnull PsiJavaCodeReferenceElement implementsReference) {
      final PsiElement target = implementsReference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass implementsClass = (PsiClass)target;
      final String className = implementsClass.getQualifiedName();
      if (className == null || !className.endsWith("Listener")) {
        return;
      }
      final String adapterName = className.substring(0,
                                                     className.length() - 8) + "Adapter";
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(
        aClass.getProject());
      final GlobalSearchScope scope =
        implementsClass.getResolveScope();
      final PsiClass adapterClass = psiFacade.findClass(adapterName,
                                                        scope);
      if (adapterClass == null) {
        return;
      }
      if (aClass.equals(adapterClass)) {
        return;
      }
      if (!adapterClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      final PsiReferenceList implementsList =
        adapterClass.getImplementsList();
      if (implementsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] referenceElements =
        implementsList.getReferenceElements();
      boolean adapterImplementsListener = false;
      for (PsiJavaCodeReferenceElement referenceElement :
        referenceElements) {
        final PsiElement implementsTarget = referenceElement.resolve();
        if (!implementsClass.equals(implementsTarget)) {
          continue;
        }
        adapterImplementsListener = true;
      }
      if (!adapterImplementsListener) {
        return;
      }
      if (checkForEmptyMethods) {
        boolean emptyMethodFound = false;
        final PsiMethod[] methods = aClass.getMethods();
        for (PsiMethod method : methods) {
          final PsiCodeBlock body = method.getBody();
          if (body == null) {
            continue;
          }
          final PsiStatement[] statements = body.getStatements();
          if (statements.length != 0) {
            continue;
          }
          final PsiMethod[] superMethods =
            method.findSuperMethods(implementsClass);
          if (superMethods.length == 0) {
            continue;
          }
          emptyMethodFound = true;
          break;
        }
        if (!emptyMethodFound) {
          return;
        }
      }
      registerError(implementsReference, aClass, adapterClass);
    }
  }
}
