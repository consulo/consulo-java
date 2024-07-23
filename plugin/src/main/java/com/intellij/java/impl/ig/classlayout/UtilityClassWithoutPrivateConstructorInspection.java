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
package com.intellij.java.impl.ig.classlayout;

import com.intellij.java.impl.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.java.impl.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.intellij.java.impl.ig.psiutils.UtilityClassUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.content.scope.SearchScope;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class UtilityClassWithoutPrivateConstructorInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreClassesWithOnlyMain = false;

  @SuppressWarnings({"PublicField"})
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.utilityClassWithoutPrivateConstructorDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.utilityClassWithoutPrivateConstructorProblemDescriptor().get();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final JPanel annotationsPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      ignorableAnnotations,
      InspectionGadgetsLocalize.ignoreIfAnnotatedBy().get()
    );
    panel.add(annotationsPanel, BorderLayout.CENTER);
    final CheckBox checkBox = new CheckBox(
      InspectionGadgetsLocalize.utilityClassWithoutPrivateConstructorOption().get(),
      this,
      "ignoreClassesWithOnlyMain"
    );
    panel.add(checkBox, BorderLayout.SOUTH);
    return panel;
  }

  @Nonnull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> fixes = new ArrayList();
    final PsiClass aClass = (PsiClass) infos[0];
    final PsiMethod constructor = getNullArgConstructor(aClass);
    if (constructor == null) {
      fixes.add(new CreateEmptyPrivateConstructor());
    } else {
      final Query<PsiReference> query = ReferencesSearch.search(constructor, constructor.getUseScope());
      final PsiReference reference = query.findFirst();
      if (reference == null) {
        fixes.add(new MakeConstructorPrivateFix());
      }
    }
    AddToIgnoreIfAnnotatedByListQuickFix.build(aClass, ignorableAnnotations, fixes);
    return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
  }

  private static class CreateEmptyPrivateConstructor extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.utilityClassWithoutPrivateConstructorCreateQuickfix().get();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement classNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = classNameIdentifier.getParent();
      if (!(parent instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass) parent;
      final Query<PsiReference> query = ReferencesSearch.search(aClass, aClass.getUseScope());
      for (PsiReference reference : query) {
        if (reference == null) {
          continue;
        }
        final PsiElement element = reference.getElement();
        final PsiElement context = element.getParent();
        if (context instanceof PsiNewExpression) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              Messages.showInfoMessage(aClass.getProject(),
                  "Utility class has instantiations, private constructor will not be created",
                  "Can't generate constructor");
            }
          });
          return;
        }
      }
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiMethod constructor = factory.createConstructor();
      final PsiModifierList modifierList = constructor.getModifierList();
      modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
      aClass.add(constructor);
      final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
      styleManager.reformat(constructor);
    }
  }

  private static class MakeConstructorPrivateFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.utilityClassWithoutPrivateConstructorMakeQuickfix().get();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement classNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = classNameIdentifier.getParent();
      if (!(parent instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass) parent;
      final PsiMethod[] constructors = aClass.getConstructors();
      for (final PsiMethod constructor : constructors) {
        final PsiParameterList parameterList = constructor.getParameterList();
        if (parameterList.getParametersCount() == 0) {
          final PsiModifierList modifiers = constructor.getModifierList();
          modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
          modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
          modifiers.setModifierProperty(PsiModifier.PRIVATE, true);
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UtilityClassWithoutPrivateConstructorVisitor();
  }

  private class UtilityClassWithoutPrivateConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (!UtilityClassUtil.isUtilityClass(aClass)) {
        return;
      }
      if (ignoreClassesWithOnlyMain && hasOnlyMain(aClass)) {
        return;
      }
      if (hasPrivateConstructor(aClass)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(aClass, ignorableAnnotations)) {
        return;
      }
      if (aClass.hasModifierProperty(PsiModifier.PRIVATE) && aClass.getConstructors().length == 0) {
        return;
      }
      final SearchScope scope = GlobalSearchScope.projectScope(aClass.getProject());
      final Query<PsiClass> query = ClassInheritorsSearch.search(aClass, scope, true, true);
      final PsiClass subclass = query.findFirst();
      if (subclass != null) {
        return;
      }
      registerClassError(aClass, aClass);
    }

    private boolean hasOnlyMain(PsiClass aClass) {
      final PsiMethod[] methods = aClass.getMethods();
      if (methods.length == 0) {
        return false;
      }
      for (PsiMethod method : methods) {
        if (method.isConstructor()) {
          continue;
        }
        if (!method.hasModifierProperty(PsiModifier.STATIC)) {
          return false;
        }
        if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
          continue;
        }
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
          return false;
        }
        final String name = method.getName();
        if (!name.equals(HardcodedMethodConstants.MAIN)) {
          return false;
        }
        final PsiType returnType = method.getReturnType();
        if (!PsiType.VOID.equals(returnType)) {
          return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
          return false;
        }
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiParameter parameter = parameters[0];
        final PsiType type = parameter.getType();
        if (!type.equalsToText("java.lang.String[]")) {
          return false;
        }
      }
      return true;
    }

    boolean hasPrivateConstructor(PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      for (final PsiMethod constructor : constructors) {
        if (constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
          return true;
        }
      }
      return false;
    }
  }

  @Nullable
  static PsiMethod getNullArgConstructor(PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    for (final PsiMethod constructor : constructors) {
      final PsiParameterList params = constructor.getParameterList();
      if (params.getParametersCount() == 0) {
        return constructor;
      }
    }
    return null;
  }
}
