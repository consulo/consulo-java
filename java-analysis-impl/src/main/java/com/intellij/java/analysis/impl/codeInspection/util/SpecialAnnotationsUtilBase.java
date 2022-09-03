/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection.util;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.language.editor.inspection.scheme.InspectionProfileManager;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import com.intellij.java.language.psi.PsiAnnotation;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.application.util.function.Processor;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class SpecialAnnotationsUtilBase {
  public static LocalQuickFix createAddToSpecialAnnotationsListQuickFix(@Nonnull final String text,
                                                                        @Nonnull final String family,
                                                                        @Nonnull final List<String> targetList,
                                                                        @Nonnull final String qualifiedName,
                                                                        final PsiElement context) {
    return new LocalQuickFix() {
      @Override
      @Nonnull
      public String getName() {
        return text;
      }

      @Override
      @Nonnull
      public String getFamilyName() {
        return family;
      }

      @Override
      public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
        doQuickFixInternal(project, targetList, qualifiedName);
      }
    };
  }

  public static void doQuickFixInternal(@Nonnull Project project, @Nonnull List<String> targetList, @Nonnull String qualifiedName) {
    targetList.add(qualifiedName);
    Collections.sort(targetList);
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    //correct save settings

    //TODO lesya
    InspectionProfileManager.getInstance().fireProfileChanged(inspectionProfile);
    /*
    try {
      inspectionProfile.save();
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
    }

    */
  }

  public static void createAddToSpecialAnnotationFixes(@Nonnull PsiModifierListOwner owner, @Nonnull Processor<String> processor) {
    final PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null) {
      final PsiAnnotation[] psiAnnotations = modifierList.getAnnotations();
      for (PsiAnnotation psiAnnotation : psiAnnotations) {
        @NonNls final String name = psiAnnotation.getQualifiedName();
        if (name == null) continue;
        if (name.startsWith("java.") || name.startsWith("javax.") ||
            name.startsWith("org.jetbrains.") && AnnotationUtil.isJetbrainsAnnotation(StringUtil.getShortName(name)))
          continue;
        if (!processor.process(name)) break;
      }
    }
  }
}
