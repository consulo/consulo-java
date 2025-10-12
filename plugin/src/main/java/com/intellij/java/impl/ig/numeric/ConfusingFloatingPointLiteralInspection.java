/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.numeric;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class ConfusingFloatingPointLiteralInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.confusingFloatingPointLiteralDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.confusingFloatingPointLiteralProblemDescriptor().get();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ConfusingFloatingPointLiteralFix();
  }

  private static class ConfusingFloatingPointLiteralFix
    extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.confusingFloatingPointLiteralChangeQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiExpression literalExpression = (PsiExpression)descriptor.getPsiElement();
      final String text = literalExpression.getText();
      final String newText = getCanonicalForm(text);
      replaceExpression(literalExpression, newText);
    }

    private static String getCanonicalForm(String text) {
      final boolean isHexadecimal = text.startsWith("0x") || text.startsWith("0X");
      int breakPoint = text.indexOf((int)'e');
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'E');
      }
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'f');
      }
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'F');
      }
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'p');
      }
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'P');
      }
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'d');
      }
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'D');
      }
      final String suffix;
      final String prefix;
      if (breakPoint < 0) {
        suffix = "";
        prefix = text;
      }
      else {
        suffix = text.substring(breakPoint);
        prefix = text.substring(0, breakPoint);
      }
      final int indexPoint = prefix.indexOf((int)'.');
      if (indexPoint < 0) {
        return prefix + ".0" + suffix;
      }
      else if (isHexadecimal && indexPoint == 2) {
        return prefix.substring(0, 2) + '0' + prefix.substring(2) + suffix;
      }
      else if (indexPoint == 0) {
        return '0' + prefix + suffix;
      }
      else {
        return prefix + '0' + suffix;
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConfusingFloatingPointLiteralVisitor();
  }

  private static class ConfusingFloatingPointLiteralVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(
      @Nonnull PsiLiteralExpression literal) {
      super.visitLiteralExpression(literal);
      final PsiType type = literal.getType();
      if (type == null) {
        return;
      }
      if (!(type.equals(PsiType.FLOAT) || type.equals(PsiType.DOUBLE))) {
        return;
      }
      final String text = literal.getText();
      if (text == null) {
        return;
      }
      if (!isConfusing(text)) {
        return;
      }
      registerError(literal);
    }


    private static boolean isConfusing(@Nullable CharSequence text) {
      if (text == null) {
        return false;
      }
      final int length = text.length();
      if (length < 3) {
        return true;
      }
      boolean hexadecimal = true;
      final char firstChar = text.charAt(0);
      if (firstChar != '0') {
        hexadecimal = false;
      }
      else if (firstChar < '0' && firstChar > '9') {
        return true;
      }
      final char secondChar = text.charAt(1);
      if (hexadecimal) {
        if (secondChar != 'x' && secondChar != 'X') {
          hexadecimal = false;
        }
      }
      int index = hexadecimal ? 2 : 1;
      char nextChar = text.charAt(index);
      if (hexadecimal && (nextChar < '0' || nextChar > '9')) {
        return true;
      }
      while (nextChar >= '0' && nextChar <= '9') {
        index++;
        if (index >= length) {
          return true;
        }
        nextChar = text.charAt(index);
      }
      if (nextChar != '.') {
        return true;
      }
      index++;
      if (index >= length) {
        return true;
      }
      nextChar = text.charAt(index);
      if (nextChar < '0' || nextChar > '9') {
        return true;
      }
      while (nextChar >= '0' && nextChar <= '9') {
        index++;
        if (index >= length) {
          return hexadecimal;
        }
        nextChar = text.charAt(index);
      }
      if (hexadecimal) {
        if (nextChar != 'p' && nextChar != 'P') {
          return true;
        }
      }
      else {
        if (nextChar != 'e' && nextChar != 'E') {
          if (nextChar == 'f' || nextChar == 'F' ||
              nextChar == 'd' || nextChar == 'D') {
            if (index == length - 1) {
              return false;
            }
          }
          return true;
        }
      }
      index++;
      if (index >= length) {
        return true;
      }
      nextChar = text.charAt(index);
      if (nextChar == '-') {
        index++;
        if (index >= length) {
          return true;
        }
        nextChar = text.charAt(index);
      }
      while (nextChar >= '0' && nextChar <= '9') {
        index++;
        if (index >= length) {
          return false;
        }
        nextChar = text.charAt(index);
      }
      // ignore trailing f, F, d or D
      return false;
    }
  }
}