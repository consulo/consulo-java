/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.impl.intelliLang.validation;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import consulo.ide.impl.intelliLang.Configuration;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.java.impl.intelliLang.pattern.PatternValidator;
import consulo.java.impl.intelliLang.util.PsiUtilEx;
import consulo.java.impl.intelliLang.util.RemoveAnnotationFix;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;

public class InjectionNotApplicable extends LocalInspectionTool {

  @Nonnull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  public String getGroupDisplayName() {
    return PatternValidator.LANGUAGE_INJECTION;
  }

  @Nonnull
  public String getDisplayName() {
    return "Injection Annotation not applicable";
  }

  @Nonnull
  public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      final String annotationName = Configuration.getProjectInstance(holder.getProject()).getAdvancedConfiguration().getLanguageAnnotationClass();

      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        final String name = annotation.getQualifiedName();
        if (annotationName.equals(name)) {
          checkAnnotation(annotation, holder);
        }
        else if (name != null) {
          final PsiClass psiClass = JavaPsiFacade.getInstance(annotation.getProject()).findClass(name, annotation.getResolveScope());
          if (psiClass != null && AnnotationUtil.isAnnotated(psiClass, annotationName, false, false)) {
            checkAnnotation(annotation, holder);
          }
        }
      }
    };
  }

  private void checkAnnotation(PsiAnnotation annotation, ProblemsHolder holder) {
    final PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class);
    if (owner instanceof PsiVariable) {
      final PsiType type = ((PsiVariable)owner).getType();
      if (!PsiUtilEx.isStringOrStringArray(type)) {
        registerProblem(annotation, holder);
      }
    }
    else if (owner instanceof PsiMethod) {
      final PsiType type = ((PsiMethod)owner).getReturnType();
      if (type == null || !PsiUtilEx.isStringOrStringArray(type)) {
        registerProblem(annotation, holder);
      }
    }
  }

  private void registerProblem(PsiAnnotation annotation, ProblemsHolder holder) {
    holder.registerProblem(annotation, "Language Injection is only applicable to elements of type String", new RemoveAnnotationFix(this));
  }

  @Nonnull
  @NonNls
  public String getShortName() {
    return "InjectionNotApplicable";
  }
}
