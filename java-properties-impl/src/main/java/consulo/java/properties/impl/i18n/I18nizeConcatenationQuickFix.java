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
package consulo.java.properties.impl.i18n;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiConcatenationUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.java.properties.impl.psi.I18nizedTextGenerator;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class I18nizeConcatenationQuickFix extends I18nizeQuickFix{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeConcatenationQuickFix");
  @NonNls private static final String PARAMETERS_OPTION_KEY = "PARAMETERS";

  @Override
  public void checkApplicability(final PsiFile psiFile, final Editor editor) throws IncorrectOperationException {
    PsiPolyadicExpression concatenation = getEnclosingLiteralConcatenation(psiFile, editor);
    if (concatenation != null) return;
    throw new IncorrectOperationException(CodeInsightLocalize.quickfixI18nConcatentationError().get());
  }

  @RequiredReadAction
  @Override
  public JavaI18nizeQuickFixDialog createDialog(Project project, Editor editor, PsiFile psiFile) {
    PsiPolyadicExpression concatenation = getEnclosingLiteralConcatenation(psiFile, editor);
    PsiLiteralExpression literalExpression = getContainingLiteral(concatenation);
    if (literalExpression == null) return null;
    return createDialog(project, psiFile, literalExpression);
  }

  @Override
  @Nonnull
  public LocalizeValue getName() {
    return CodeInsightLocalize.quickfixI18nConcatentation();
  }

  @Override
  @RequiredReadAction
  protected PsiElement doReplacementInJava(
    @Nonnull final PsiFile psiFile,
    @Nonnull final Editor editor,
    @Nullable PsiLiteralExpression literalExpression,
    String i18nizedText
  ) throws IncorrectOperationException {
    PsiPolyadicExpression concatenation = getEnclosingLiteralConcatenation(psiFile, editor);
    PsiExpression expression = JavaPsiFacade.getInstance(psiFile.getProject()).getElementFactory()
      .createExpressionFromText(i18nizedText, concatenation);
    return concatenation.replace(expression);
  }

  @RequiredReadAction
  private static String composeParametersText(final List<PsiExpression> args) {
    final StringBuilder result = new StringBuilder();
    for (Iterator<PsiExpression> iterator = args.iterator(); iterator.hasNext();) {
      PsiExpression psiExpression = iterator.next();
      result.append(psiExpression.getText());
      if (iterator.hasNext()) {
        result.append(",");
      }
    }
    return result.toString();
  }

  @Override
  @RequiredUIAccess
  protected JavaI18nizeQuickFixDialog createDialog(final Project project, final PsiFile context, final PsiLiteralExpression literalExpression) {
    PsiPolyadicExpression concatenation = getEnclosingLiteralConcatenation(literalExpression);
    StringBuilder formatString = new StringBuilder();
    final List<PsiExpression> args = new ArrayList<>();
    try {
      PsiConcatenationUtil.buildFormatString(concatenation, formatString, args, false);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    return new JavaI18nizeQuickFixDialog(
      project,
      context,
      literalExpression,
      formatString.toString(),
      null,
      true,
      true
    ) {
      @Override
      @Nullable
      protected String getTemplateName() {
        return myResourceBundleManager.getConcatenationTemplateName();
      }

      @Override
      protected String generateText(
        final I18nizedTextGenerator textGenerator,
        final String propertyKey,
        final PropertiesFile propertiesFile,
        final PsiLiteralExpression literalExpression
      ) {
        return textGenerator.getI18nizedConcatenationText(propertyKey, composeParametersText(args), propertiesFile, literalExpression);
      }

      @Override
      public PsiExpression[] getParameters() {
        return args.toArray(new PsiExpression[args.size()]);
      }

      @Override
      protected void addAdditionalAttributes(final Map<String, String> attributes) {
        attributes.put(PARAMETERS_OPTION_KEY, composeParametersText(args));
      }
    };
  }

  @Nullable
  private static PsiPolyadicExpression getEnclosingLiteralConcatenation(@Nonnull PsiFile file, @Nonnull Editor editor) {
    final PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
    return getEnclosingLiteralConcatenation(elementAt);
  }

  @Nullable
  public static PsiPolyadicExpression getEnclosingLiteralConcatenation(final PsiElement psiElement) {
    PsiPolyadicExpression element = PsiTreeUtil.getParentOfType(psiElement, PsiPolyadicExpression.class, false, PsiMember.class);
    if (element == null) return null;

    PsiPolyadicExpression concatenation = null;
    boolean stringLiteralOccured = false;
    while (true) {
      if (element.getOperationTokenType() != JavaTokenType.PLUS) return concatenation;
      for (PsiExpression operand : element.getOperands()) {
        stringLiteralOccured |= operand instanceof PsiLiteralExpression literalExpression && literalExpression.getValue() instanceof String;
      }

      if (stringLiteralOccured) {
        concatenation = element;
      }
      PsiElement parent = element.getParent();
      if (parent instanceof PsiPolyadicExpression polyadicExpression) {
        element = polyadicExpression;
      }
      else {
        return concatenation;
      }
    }
  }

  private static PsiLiteralExpression getContainingLiteral(final PsiPolyadicExpression concatenation) {
    for (PsiExpression operand : concatenation.getOperands()) {
      if (operand instanceof PsiLiteralExpression) {
        return (PsiLiteralExpression)operand;
      }
    }
    return null;
  }
}
