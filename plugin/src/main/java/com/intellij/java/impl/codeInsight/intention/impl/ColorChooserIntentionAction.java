/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: spleaner
 * Date: Aug 22, 2007
 * Time: 3:44:10 PM
 */
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.patterns.PsiJavaPatterns;
import com.intellij.java.language.patterns.PsiMethodPattern;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.ColorChooser;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

/**
 * @author spleaner
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "id.ColorChooserIntentionAction", fileExtensions = "java", categories = {"Java", "Other"})
public class ColorChooserIntentionAction extends BaseColorIntentionAction {
  private static final PsiMethodPattern DECODE_METHOD = PsiJavaPatterns.psiMethod().definedInClass(JAVA_AWT_COLOR).withName("decode");

  private static final PsiMethodPattern GET_COLOR_METHOD = PsiJavaPatterns.psiMethod().definedInClass(JAVA_AWT_COLOR).withName("getColor");

  public ColorChooserIntentionAction() {
    setText(CodeInsightLocalize.intentionColorChooserDialog().get());
  }

  @Override
  public boolean isAvailable(@Nonnull final Project project, final Editor editor, @Nonnull final PsiElement element) {
    return super.isAvailable(project, editor, element) || isInsideDecodeOrGetColorMethod(element);
  }

  public static boolean isInsideDecodeOrGetColorMethod(PsiElement element) {
    if (element instanceof PsiJavaToken javaToken && javaToken.getTokenType() == JavaTokenType.STRING_LITERAL) {
      element = element.getParent();
    }

    return PsiJavaPatterns.psiExpression().methodCallParameter(0, DECODE_METHOD).accepts(element)
      || PsiJavaPatterns.psiExpression().methodCallParameter(0, GET_COLOR_METHOD).accepts(element);
  }

  @Override
  @RequiredReadAction
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) {
      return;
    }

    final JComponent editorComponent = editor.getComponent();
    if (isInsideDecodeOrGetColorMethod(element)) {
      invokeForMethodParam(editorComponent, element);
    } else {
      invokeForConstructor(editorComponent, element);
    }
  }

  @RequiredReadAction
  private void invokeForMethodParam(JComponent editorComponent, PsiElement element) {
    final PsiLiteralExpression literal = PsiTreeUtil.getParentOfType(element, PsiLiteralExpression.class);
    if (literal == null) {
      return;
    }
    final String text = StringUtil.unquoteString(literal.getText());
    final int radix = text.startsWith("0x") || text.startsWith("0X") || text.startsWith("#") ? 16 : text.startsWith("0") ? 8 : 10;
    final String hexPrefix = radix == 16 ? text.startsWith("#") ? "#" : text.substring(0, 2) : null;

    Color oldColor;
    try {
      oldColor = Color.decode(text);
    } catch (NumberFormatException e) {
      oldColor = JBColor.GRAY;
    }

    final Color temp = oldColor;

    ColorChooser.chooseColor(editorComponent, getText(), temp, color ->
    {
      if (color == null) {
        return;
      }

      WriteCommandAction.runWriteCommandAction(element.getProject(), () ->
      {
        final int rgb = color.getRGB() - ((255 & 0xFF) << 24);
        if (rgb != temp.getRGB()) {
          final String newText = radix == 16 ? hexPrefix + String.format("%6s", Integer.toHexString(rgb)).replace(' ', '0') : radix == 8 ? "0" + Integer.toOctalString(rgb) : Integer.toString(rgb);
          final PsiManager manager = literal.getManager();
          final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
          final PsiExpression newLiteral = factory.createExpressionFromText("\"" + newText + "\"", literal);
          literal.replace(newLiteral);
        }
      });
    });
  }

  private void invokeForConstructor(JComponent editorComponent, PsiElement element) {
    final PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
    if (expression == null) {
      return;
    }

    Color c = null;

    final PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList != null) {
      final PsiExpression[] expressions = argumentList.getExpressions();
      int[] values = new int[expressions.length];
      float[] values2 = new float[expressions.length];
      int i = 0;
      int j = 0;
      for (final PsiExpression each : expressions) {
        if (each instanceof PsiLiteralExpression literalExpression) {
          final Object o = literalExpression.getValue();
          if (o instanceof Integer intValue) {
            values[i] = intValue;
            i++;
          } else if (o instanceof Float floatValue) {
            values2[j] = floatValue;
            j++;
          }
        }
      }

      try {
        if (i == expressions.length) {
          switch (values.length) {
            case 1:
              c = new Color(values[0]);
              break;
            case 3:
              c = new Color(values[0], values[1], values[2]);
              break;
            case 4:
              c = new Color(values[0], values[1], values[2], values[3]);
              break;
            default:
              break;
          }
        } else if (j == expressions.length) {
          switch (values2.length) {
            case 3:
              c = new Color(values2[0], values2[1], values2[2]);
              break;
            case 4:
              c = new Color(values2[0], values2[1], values2[2], values2[3]);
              break;
            default:
              break;
          }
        }
      } catch (Exception e) {
        c = JBColor.GRAY;
      }
    }

    c = (c == null) ? JBColor.GRAY : c;

    replaceColor(editorComponent, expression, c);
  }

  private void replaceColor(JComponent editorComponent, PsiNewExpression expression, Color oldColor) {
    ColorChooser.chooseColor(editorComponent, getText(), oldColor, true, color ->
    {
      if (color == null) {
        return;
      }

      WriteCommandAction.runWriteCommandAction(expression.getProject(), () ->
      {
        final PsiManager manager = expression.getManager();
        final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        final PsiExpression newCall = factory.createExpressionFromText("new " + JAVA_AWT_COLOR + "(" + color.getRed() + ", " + color.getGreen() + ", " + color.getBlue() + (color.getAlpha() < 255 ? ", " + color.getAlpha() : "") + ")", expression);
        final PsiElement insertedElement = expression.replace(newCall);
        final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
        codeStyleManager.reformat(insertedElement);
      });
    });
  }
}
