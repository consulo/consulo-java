/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.enumswitch;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;

import java.util.HashSet;
import java.util.Set;

class EnumSwitchPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (element instanceof PsiWhiteSpace) {
      PsiElement prevSibling = element.getPrevSibling();
      if (prevSibling instanceof PsiSwitchStatement && ErrorUtil.containsError(prevSibling)) {
        element = prevSibling;
      }
    }
    if (!(element instanceof PsiSwitchStatement)) {
      return false;
    }
    final PsiSwitchStatement switchStatement = (PsiSwitchStatement)element;
    final PsiCodeBlock body = switchStatement.getBody();
    final PsiExpression expression = switchStatement.getExpression();
    if (expression == null) {
      return false;
    }
    final PsiType type = expression.getType();
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClass enumClass = ((PsiClassType)type).resolve();
    if (enumClass == null || !enumClass.isEnum()) {
      return false;
    }
    final PsiField[] fields = enumClass.getFields();
    final Set<String> enumElements = new HashSet<String>(fields.length);
    for (final PsiField field : fields) {
      final PsiType fieldType = field.getType();
      if (fieldType.equals(type)) {
        final String fieldName = field.getName();
        enumElements.add(fieldName);
      }
    }
    if (body != null) {
      final PsiStatement[] statements = body.getStatements();
      for (PsiStatement statement : statements) {
        if (statement instanceof PsiSwitchLabelStatement) {
          final PsiSwitchLabelStatement labelStatement =
            (PsiSwitchLabelStatement)statement;
          final PsiExpression value = labelStatement.getCaseValue();
          if (value != null) {
            final String valueText = value.getText();
            enumElements.remove(valueText);
          }
        }
      }
    }
    if (enumElements.isEmpty()) {
      return false;
    }
    return true;
  }
}
