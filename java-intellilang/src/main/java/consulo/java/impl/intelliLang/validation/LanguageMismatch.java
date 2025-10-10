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
import consulo.java.impl.intelliLang.pattern.PatternValidator;
import consulo.java.impl.intelliLang.util.AnnotateFix;
import consulo.java.impl.intelliLang.util.AnnotationUtilEx;
import consulo.java.impl.intelliLang.util.PsiUtilEx;
import consulo.language.Language;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.inject.advanced.Configuration;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.Set;

@ExtensionImpl
public class LanguageMismatch extends LocalInspectionTool {
  public boolean CHECK_NON_ANNOTATED_REFERENCES = true;

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
  public LocalizeValue getGroupDisplayName() {
    return PatternValidator.LANGUAGE_INJECTION;
  }

  @Nonnull
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("Language Mismatch");
  }

  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel jPanel = new JPanel(new BorderLayout());
    final JCheckBox jCheckBox =
        new JCheckBox("Flag usages of non-annotated elements where the usage context " + "implies a certain language");
    jCheckBox.setSelected(CHECK_NON_ANNOTATED_REFERENCES);
    jCheckBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        CHECK_NON_ANNOTATED_REFERENCES = jCheckBox.isSelected();
      }
    });
    jPanel.add(jCheckBox, BorderLayout.NORTH);
    return jPanel;
  }

  @Nonnull
  public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      final Pair<String, ? extends Set<String>> annotationName = Configuration.getProjectInstance(holder.getProject()).getAdvancedConfiguration().getLanguageAnnotationPair();

      public void visitExpression(PsiExpression expression) {
        checkExpression(expression, holder, annotationName);
      }

      @Override
      public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
        final PsiExpression expr = expression.getExpression();
        if (expr != null) {
          expr.accept(this);
        }
      }

      public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiElement element = expression.resolve();
        if (!(element instanceof PsiModifierListOwner)) {
          return;
        }
        checkExpression(expression, holder, annotationName);
      }
    };
  }

  private void checkExpression(PsiExpression expression, ProblemsHolder holder, Pair<String, ? extends Set<String>> annotationName) {
    final PsiType type = expression.getType();
    if (type == null || !PsiUtilEx.isStringOrStringArray(type)) {
      return;
    }

    final PsiModifierListOwner contextOwner = AnnotationUtilEx.getAnnotatedElementFor(expression, AnnotationUtilEx.LookupType.CONTEXT_ONLY);
    if (contextOwner != null && PsiUtilEx.isLanguageAnnotationTarget(contextOwner)) {
      final PsiAnnotation[] annotations = AnnotationUtilEx.getAnnotationFrom(contextOwner, annotationName, true);
      if (annotations.length > 0) {
        final String expected = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
        if (expected != null) {
          final PsiModifierListOwner declOwner =
              AnnotationUtilEx.getAnnotatedElementFor(expression, AnnotationUtilEx.LookupType.PREFER_DECLARATION);
          if (declOwner != null && PsiUtilEx.isLanguageAnnotationTarget(declOwner)) {
            final PsiAnnotation[] as = AnnotationUtilEx.getAnnotationFrom(declOwner, annotationName, true);
            if (as.length > 0) {
              final String actual = AnnotationUtilEx.calcAnnotationValue(as, "value");
              if (!expected.equals(actual)) {
                // language annotation values from context and declaration don't match
                holder.registerProblem(expression, "Language mismatch: Expected '" + expected + "', got '" + actual + "'");
              }
            }
            else if (CHECK_NON_ANNOTATED_REFERENCES) {
              final PsiElement var =
                  PsiTreeUtil.getParentOfType(expression, PsiVariable.class, PsiExpressionList.class, PsiAssignmentExpression.class);
              // only nag about direct assignment or passing the reference as parameter
              if (var instanceof PsiVariable) {
                if (((PsiVariable)var).getInitializer() != expression) {
                  return;
                }
              }
              else if (var instanceof PsiExpressionList) {
                final PsiExpressionList list = (PsiExpressionList)var;
                if (Arrays.asList(list.getExpressions()).indexOf(expression) == -1) {
                  return;
                }
              }
              else if (var instanceof PsiAssignmentExpression) {
                final PsiAssignmentExpression a = (PsiAssignmentExpression)var;
                if (a.getRExpression() != expression) {
                  return;
                }
              }
              // context implies language, but declaration isn't annotated
              final PsiAnnotation annotation = annotations[annotations.length - 1];
              final String initializer = annotation.getParameterList().getText();
              final AnnotateFix fix = new AnnotateFix(declOwner, annotation.getQualifiedName(), initializer) {
                @Nonnull
                public LocalizeValue getName() {
                  return initializer == null ? super.getName() : LocalizeValue.join(super.getName(), LocalizeValue.of(initializer));
                }
              };

              if (fix.canApply()) {
                holder.registerProblem(expression, "Language problem: Found non-annotated reference where '" + expected + "' is expected",
                                       fix);
              }
              else {
                holder.registerProblem(expression, "Language problem: Found non-annotated reference where '" + expected + "' is expected");
              }
            }
          }
        }
      }
    }
  }

  @Nonnull
  @NonNls
  public String getShortName() {
    return "LanguageMismatch";
  }
}
