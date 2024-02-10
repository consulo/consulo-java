/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.inspection;

import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.java.generate.GenerateToStringContext;
import org.jetbrains.java.generate.GenerateToStringUtils;

/**
 * Inspection to check if the current class overrides the toString() method.
 * <p/>
 * This inspection will use filter information from the GenerateToString plugin settings to exclude certain fields (eg. constants etc.).
 * Warns if the class has fields to be dumped and does not have a toString method.
 */
@ExtensionImpl
public class ClassHasNoToStringMethodInspection extends AbstractToStringInspection {
  @Override
  @Nonnull
  public String getDisplayName() {
    return "Class does not override 'toString()' method";
  }

  @Override
  @Nonnull
  public String getShortName() {
    return "ClassHasNoToStringMethod";
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        LocalInspectionToolSession session,
                                        Object state) {
    ClassHasNoToStringMethodInspectionState inspectionState = (ClassHasNoToStringMethodInspectionState)state;
    return new JavaElementVisitor() {
      @Override
      public void visitClass(PsiClass clazz) {
        if (AbstractToStringInspection.log.isDebugEnabled()) {
          AbstractToStringInspection.log.debug("checkClass: clazz=" + clazz);
        }

        // must be a class
        final PsiIdentifier nameIdentifier = clazz.getNameIdentifier();
        if (nameIdentifier == null || clazz.getName() == null) {
          return;
        }

        if (inspectionState.excludeException && InheritanceUtil.isInheritor(clazz, JavaClassNames.JAVA_LANG_THROWABLE)) {
          return;
        }
        if (inspectionState.excludeDeprecated && clazz.isDeprecated()) {
          return;
        }
        if (inspectionState.excludeEnum && clazz.isEnum()) {
          return;
        }
        if (inspectionState.excludeAbstract && clazz.hasModifierProperty(PsiModifier.ABSTRACT)) {
          return;
        }
        if (inspectionState.excludeTestCode && TestFrameworks.getInstance().isTestClass(clazz)) {
          return;
        }
        if (inspectionState.excludeInnerClasses && clazz.getContainingClass() != null) {
          return;
        }

        // if it is an excluded class - then skip
        if (StringUtil.isNotEmpty(inspectionState.excludeClassNames)) {
          String name = clazz.getName();
          if (name != null && name.matches(inspectionState.excludeClassNames)) {
            return;
          }
        }

        // must have fields
        PsiField[] fields = clazz.getFields();
        if (fields.length == 0) {
          return;
        }

        // get list of fields and getter methods supposed to be dumped in the toString method
        fields = GenerateToStringUtils.filterAvailableFields(clazz, GenerateToStringContext.getConfig().getFilterPattern());
        PsiMethod[] methods = null;
        if (GenerateToStringContext.getConfig().isEnableMethods()) {
          // okay 'getters in code generation' is enabled so check
          methods = GenerateToStringUtils.filterAvailableMethods(clazz, GenerateToStringContext.getConfig().getFilterPattern());
        }

        // there should be any fields
        if (Math.max(fields.length, methods == null ? 0 : methods.length) == 0) {
          return;
        }

        // okay some fields/getter methods are supposed to dumped, does a toString method exist
        final PsiMethod[] toStringMethods = clazz.findMethodsByName("toString", false);
        for (PsiMethod method : toStringMethods) {
          final PsiParameterList parameterList = method.getParameterList();
          if (parameterList.getParametersCount() == 0) {
            // toString() method found
            return;
          }
        }
        final PsiMethod[] superMethods = clazz.findMethodsByName("toString", true);
        for (PsiMethod method : superMethods) {
          final PsiParameterList parameterList = method.getParameterList();
          if (parameterList.getParametersCount() != 0) {
            continue;
          }
          if (method.hasModifierProperty(PsiModifier.FINAL)) {
            // final toString() in super class found
            return;
          }
        }
        holder.registerProblem(nameIdentifier, "Class '" + clazz.getName() + "' does not override 'toString()' method",
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, GenerateToStringQuickFix.getInstance());
      }
    };
  }
}
