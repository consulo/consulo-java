/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.naming;

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class NonExceptionNameEndsWithExceptionInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.nonExceptionNameEndsWithExceptionDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.nonExceptionNameEndsWithExceptionProblemDescriptor().get();
  }

  @Override
  @Nonnull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    String name = (String)infos[0];
    Boolean onTheFly = (Boolean)infos[1];
    if (onTheFly.booleanValue()) {
      return new InspectionGadgetsFix[]{new RenameFix(),
        new ExtendExceptionFix(name)};
    }
    else {
      return new InspectionGadgetsFix[]{
        new ExtendExceptionFix(name)};
    }
  }

  private static class ExtendExceptionFix extends InspectionGadgetsFix {

    private final String name;

    ExtendExceptionFix(String name) {
      this.name = name;
    }

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.nonExceptionNameEndsWithExceptionQuickfix(name);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass)) {
        return;
      }
      PsiClass aClass = (PsiClass)parent;
      PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiElementFactory factory = facade.getElementFactory();
      GlobalSearchScope scope = aClass.getResolveScope();
      PsiJavaCodeReferenceElement reference =
        factory.createReferenceElementByFQClassName(CommonClassNames.JAVA_LANG_EXCEPTION, scope);
      PsiJavaCodeReferenceElement[] referenceElements =
        extendsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement :
        referenceElements) {
        referenceElement.delete();
      }
      extendsList.add(reference);
    }
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonExceptionNameEndsWithExceptionVisitor();
  }

  private static class NonExceptionNameEndsWithExceptionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      // no call to super, so it doesn't drill down into inner classes
      String className = aClass.getName();
      if (className == null) {
        return;
      }
      @NonNls String exception = "Exception";
      if (!className.endsWith(exception)) {
        return;
      }
      if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_EXCEPTION)) {
        return;
      }
      registerClassError(aClass, className, Boolean.valueOf(isOnTheFly()));
    }
  }
}