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
package com.intellij.java.impl.codeInsight.lookup;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.application.AllIcons;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Set;

/**
 * @author peter
 */
public class ExpressionLookupItem extends LookupElement implements TypedLookupItem {
  private final PsiExpression myExpression;
  private final Image myIcon;
  private final String myPresentableText;
  private final String myLookupString;
  private final Set<String> myAllLookupStrings;

  public ExpressionLookupItem(final PsiExpression expression) {
    this(expression, getExpressionIcon(expression), expression.getText(), expression.getText());
  }

  public ExpressionLookupItem(final PsiExpression expression, @Nullable Image icon, String presentableText, String... lookupStrings) {
    myExpression = expression;
    myPresentableText = presentableText;
    myIcon = icon;
    myLookupString = lookupStrings[0];
    myAllLookupStrings = Set.of(lookupStrings);
  }

  @Nullable
  private static Image getExpressionIcon(@Nonnull PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiElement element = ((PsiReferenceExpression) expression).resolve();
      if (element != null) {
        return IconDescriptorUpdaters.getIcon(element, 0);
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      return AllIcons.Nodes.Method;
    }
    return null;
  }

  @Nonnull
  @Override
  public PsiExpression getObject() {
    return myExpression;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setIcon(myIcon);
    presentation.setItemText(myPresentableText);
    PsiType type = getType();
    presentation.setTypeText(type == null ? null : type.getPresentableText());
  }

  @Override
  public PsiType getType() {
    return myExpression.getType();
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof ExpressionLookupItem && myLookupString.equals(((ExpressionLookupItem) o).myLookupString);
  }

  @Override
  public int hashCode() {
    return myLookupString.hashCode();
  }

  @Nonnull
  @Override
  public String getLookupString() {
    return myLookupString;
  }

  @Override
  public Set<String> getAllLookupStrings() {
    return myAllLookupStrings;
  }
}