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

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.intelliLang.Configuration;
import consulo.ide.impl.intelliLang.inject.InjectedLanguage;
import consulo.language.Language;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.java.impl.intelliLang.pattern.PatternValidator;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@ExtensionImpl
public class UnknownLanguageID extends LocalInspectionTool {

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

  @Nullable
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Nonnull
  public String getDisplayName() {
    return "Unknown Language ID";
  }

  @Nonnull
  public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      final String annotationName = Configuration.getProjectInstance(holder.getProject()).getAdvancedConfiguration().getLanguageAnnotationClass();

      @Override
      public void visitNameValuePair(PsiNameValuePair valuePair) {
        final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(valuePair, PsiAnnotation.class);
        if (annotation != null) {
          final String qualifiedName = annotation.getQualifiedName();
          if (annotationName.equals(qualifiedName)) {
            final String name = valuePair.getName();
            if (name == null || "value".equals(name)) {
              final PsiAnnotationMemberValue value = valuePair.getValue();
              if (value instanceof PsiExpression) {
                final PsiExpression expression = (PsiExpression)value;
                final Object id = JavaPsiFacade.getInstance(expression.getProject()).
                  getConstantEvaluationHelper().computeConstantExpression(expression);
                if (id instanceof String) {
                  final Language language = InjectedLanguage.findLanguageById((String)id);
                  if (language == null) {
                    holder.registerProblem(expression, "Unknown language '" + id + "'", ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
                  }
                }
              }
            }
          }
        }
      }
    };
  }

  @Nonnull
  @NonNls
  public String getShortName() {
    return "UnknownLanguage";
  }
}
