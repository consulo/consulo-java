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
package com.intellij.java.impl.ig.classlayout;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.collection.SmartList;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ExtensionImpl
public class MissingOverrideAnnotationInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreObjectMethods = true;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreAnonymousClassMethods = false;

  @Override
  @Nonnull
  public String getID() {
    return "override";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.missingOverrideAnnotationDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.missingOverrideAnnotationProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsLocalize.ignoreEqualsHashcodeAndTostring().get(), "ignoreObjectMethods");
    panel.addCheckbox(InspectionGadgetsLocalize.ignoreMethodsInAnonymousClasses().get(), "ignoreAnonymousClassMethods");
    return panel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new MissingOverrideAnnotationFix();
  }

  private static class MissingOverrideAnnotationFix extends InspectionGadgetsFix {
    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.missingOverrideAnnotationAddQuickfix().get();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement identifier = descriptor.getPsiElement();
      final PsiElement parent = identifier.getParent();
      if (!(parent instanceof PsiModifierListOwner)) {
        return;
      }
      final PsiModifierListOwner modifierListOwner =
        (PsiModifierListOwner)parent;
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiAnnotation annotation =
        factory.createAnnotationFromText("@java.lang.Override",
                                         modifierListOwner);
      final PsiModifierList modifierList =
        modifierListOwner.getModifierList();
      if (modifierList == null) {
        return;
      }
      modifierList.addAfter(annotation, null);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MissingOverrideAnnotationVisitor();
  }

  private class MissingOverrideAnnotationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      if (!PsiUtil.isLanguageLevel5OrHigher(method)) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      if (method.isConstructor()) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.PRIVATE) ||
          method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass methodClass = method.getContainingClass();
      if (methodClass == null) {
        return;
      }
      if (ignoreAnonymousClassMethods &&
          methodClass instanceof PsiAnonymousClass) {
        return;
      }
      final boolean useJdk6Rules =
        PsiUtil.isLanguageLevel6OrHigher(method);
      if (useJdk6Rules) {
        if (!isJdk6Override(method, methodClass)) {
          return;
        }
      }
      else if (!isJdk5Override(method, methodClass)) {
        return;
      }
      if (ignoreObjectMethods && (MethodUtils.isHashCode(method) ||
                                  MethodUtils.isEquals(method) ||
                                  MethodUtils.isToString(method))) {
        return;
      }
      if (hasOverrideAnnotation(method)) {
        return;
      }
      registerMethodError(method);
    }

    private boolean hasOverrideAnnotation(
      PsiModifierListOwner element) {
      final PsiModifierList modifierList = element.getModifierList();
      if (modifierList == null) {
        return false;
      }
      final PsiAnnotation annotation =
        modifierList.findAnnotation("java.lang.Override");
      return annotation != null;
    }

    private boolean isJdk6Override(PsiMethod method, PsiClass methodClass) {
      final PsiMethod[] superMethods =
        getSuperMethodsInJavaSense(method, methodClass);
      if (superMethods.length <= 0) {
        return false;
      }
      // is override except if this is an interface method
      // overriding a protected method in java.lang.Object
      // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6501053
      if (!methodClass.isInterface()) {
        return true;
      }
      for (PsiMethod superMethod : superMethods) {
        if (!superMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
          return true;
        }
      }
      return false;
    }

    private boolean isJdk5Override(PsiMethod method, PsiClass methodClass) {
      final PsiMethod[] superMethods =
        getSuperMethodsInJavaSense(method, methodClass);
      for (PsiMethod superMethod : superMethods) {
        final PsiClass superClass = superMethod.getContainingClass();
        if (superClass == null) {
          continue;
        }
        if (superClass.isInterface()) {
          continue;
        }
        if (methodClass.isInterface() &&
            superMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
          // only true for J2SE java.lang.Object.clone(), but might
          // be different on other/newer java platforms
          continue;
        }
        return true;
      }
      return false;
    }

    private PsiMethod[] getSuperMethodsInJavaSense(
      @Nonnull PsiMethod method, @Nonnull PsiClass methodClass) {
      final PsiMethod[] superMethods = method.findSuperMethods();
      final List<PsiMethod> toExclude = new SmartList<PsiMethod>();
      for (PsiMethod superMethod : superMethods) {
        final PsiClass superClass = superMethod.getContainingClass();
        if (!InheritanceUtil.isInheritorOrSelf(methodClass, superClass,
                                               true)) {
          toExclude.add(superMethod);
        }
      }
      if (!toExclude.isEmpty()) {
        final List<PsiMethod> result =
          new ArrayList<PsiMethod>(Arrays.asList(superMethods));
        result.removeAll(toExclude);
        return result.toArray(new PsiMethod[result.size()]);
      }
      return superMethods;
    }
  }
}
