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
package com.intellij.java.impl.codeInspection.concurrencyAnnotations;

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.analysis.impl.codeInspection.concurrencyAnnotations.JCiPUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;

/**
 * check locks according to http://www.javaconcurrencyinpractice.com/annotations/doc/net/jcip/annotations/GuardedBy.html
 */
@ExtensionImpl
public class UnknownGuardInspection extends BaseJavaLocalInspectionTool {

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesConcurrencyAnnotationIssues().get();
  }

  @Override
  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Unknown @GuardedBy field";
  }

  @Override
  @Nonnull
  public String getShortName() {
    return "UnknownGuard";
  }

  @Override
  @Nonnull
  public PsiElementVisitor buildVisitorImpl(@Nonnull ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    return new Visitor(holder);
  }

  private static class Visitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    public Visitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      if (!JCiPUtil.isGuardedByAnnotation(annotation)) {
        return;
      }
      final String guardValue = JCiPUtil.getGuardValue(annotation);
      if (guardValue == null || "this".equals(guardValue) || "itself".equals(guardValue)) {
        return;
      }
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(annotation, PsiClass.class);
      if (containingClass == null) {
        return;
      }

      if (containsFieldOrMethod(containingClass, guardValue)) return;

      //class-name.class
      final Project project = containingClass.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      if (guardValue.endsWith(".class") &&
          facade.findClass(StringUtil.getPackageName(guardValue), GlobalSearchScope.allScope(project)) != null) {
        return;
      }

      //class-name.field-name
      final String classFQName = StringUtil.getPackageName(guardValue);
      final PsiClass gClass = facade.findClass(classFQName, GlobalSearchScope.allScope(project));
      if (gClass != null) {
        final String fieldName = StringUtil.getShortName(guardValue);
        if (gClass.findFieldByName(fieldName, true) != null) {
          return;
        }
        //class-name.this
        if (fieldName.equals("this")) {
          return;
        }
      }

      //class-name.this.field-name/method-name
      final int thisIdx = guardValue.indexOf("this");
      if (thisIdx > -1 && thisIdx + 1 < guardValue.length()) {
        final PsiClass lockClass;
        if (thisIdx == 0) {
          lockClass = containingClass;
        }
        else {
          final String fqn = guardValue.substring(0, thisIdx - 1);
          lockClass = facade.findClass(fqn, GlobalSearchScope.allScope(project));
        }

        if (lockClass != null) {
          final String fieldName = guardValue.substring(thisIdx + "this".length() + 1);
          if (containsFieldOrMethod(lockClass, fieldName)) {
            return;
          }
        }
      }

      final PsiAnnotationMemberValue member = annotation.findAttributeValue("value");
      if (member == null) {
        return;
      }
      myHolder.registerProblem(member, "Unknown @GuardedBy field #ref #loc");
    }

    private static boolean containsFieldOrMethod(PsiClass containingClass, String fieldOrMethod) {
      //field-name
      if (containingClass.findFieldByName(fieldOrMethod, true) != null) {
        return true;
      }

      //method-name
      if (fieldOrMethod.endsWith("()")) {
        final PsiMethod[] methods = containingClass.findMethodsByName(StringUtil.trimEnd(fieldOrMethod, "()"), true);
        for (PsiMethod method : methods) {
          if (method.getParameterList().getParameters().length == 0) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void visitDocTag(PsiDocTag psiDocTag) {
      super.visitDocTag(psiDocTag);
      if (!JCiPUtil.isGuardedByTag(psiDocTag)) {
        return;
      }
      final String guardValue = JCiPUtil.getGuardValue(psiDocTag);
      if ("this".equals(guardValue)) {
        return;
      }
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(psiDocTag, PsiClass.class);
      if (containingClass == null) {
        return;
      }
      final PsiField guardField = containingClass.findFieldByName(guardValue, true);
      if (guardField != null) {
        return;
      }
      myHolder.registerProblem(psiDocTag, "Unknown @GuardedBy field \"" + guardValue + "\" #loc");
    }
  }
}
