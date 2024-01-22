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
package consulo.java.impl.intelliLang.pattern;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.intelliLang.Configuration;
import consulo.language.Language;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.util.lang.Pair;
import consulo.java.impl.intelliLang.util.AnnotateFix;
import consulo.java.impl.intelliLang.util.AnnotationUtilEx;
import consulo.java.impl.intelliLang.util.PsiUtilEx;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

import java.util.Set;

@ExtensionImpl
public class PatternOverriddenByNonAnnotatedMethod extends LocalInspectionTool {

  @Nullable
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Nonnull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  public String getGroupDisplayName() {
    return PatternValidator.PATTERN_VALIDATION;
  }

  @Nonnull
  public String getDisplayName() {
    return "Non-annotated Method overrides @Pattern Method";
  }

  @Nonnull
  public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      final Pair<String, ? extends Set<String>> annotationName = Configuration.getProjectInstance(holder.getProject()).getAdvancedConfiguration().getPatternAnnotationPair();

      @Override
      public void visitMethod(PsiMethod method) {
        final PsiIdentifier psiIdentifier = method.getNameIdentifier();
        if (psiIdentifier == null || !PsiUtilEx.isLanguageAnnotationTarget(method)) {
          return;
        }

        final PsiAnnotation[] annotationFrom = AnnotationUtilEx.getAnnotationFrom(method, annotationName, true, false);
        if (annotationFrom.length == 0) {
          final PsiAnnotation[] annotationFromHierarchy = AnnotationUtilEx.getAnnotationFrom(method, annotationName, true, true);
          if (annotationFromHierarchy.length > 0) {
            final String annotationClassname = annotationFromHierarchy[annotationFromHierarchy.length - 1].getQualifiedName();
            final String argList = annotationFromHierarchy[annotationFromHierarchy.length - 1].getParameterList().getText();
            holder.registerProblem(psiIdentifier, "Non-annotated Method overrides @Pattern Method",
                                   new AnnotateFix(method, annotationClassname, argList));
          }
        }
      }
    };
  }

  @Nonnull
  @NonNls
  public String getShortName() {
    return "PatternOverriddenByNonAnnotatedMethod";
  }
}
