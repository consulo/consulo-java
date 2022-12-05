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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.impl.codeInsight.lookup.TypedLookupItem;
import com.intellij.java.language.psi.PsiType;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.editor.completion.ClassConditionKey;
import consulo.language.editor.completion.CompletionStyleUtil;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementDecorator;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.psi.PsiElement;

import javax.annotation.Nullable;

/**
 * @author peter
 */
public class CastingLookupElementDecorator extends LookupElementDecorator<LookupElement> implements TypedLookupItem {
  public static final ClassConditionKey<CastingLookupElementDecorator> CLASS_CONDITION_KEY = ClassConditionKey.create(CastingLookupElementDecorator.class);

  private final LookupElement myCastItem;
  private final PsiType myCastType;

  @Nullable
  private static String getItemText(LookupElementPresentation base, LookupElement castItem) {
    final LookupElementPresentation castPresentation = new LookupElementPresentation();
    castItem.renderElement(castPresentation);
    return castPresentation.getItemText();
  }

  private CastingLookupElementDecorator(LookupElement delegate, PsiType castType) {
    super(delegate);
    myCastType = castType;
    myCastItem = PsiTypeLookupItem.createLookupItem(castType, (PsiElement) delegate.getObject());
  }

  @Override
  public PsiType getType() {
    return myCastType;
  }

  @Override
  public String toString() {
    return "(" + myCastItem.getLookupString() + ")" + getDelegate().getLookupString();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    getDelegate().renderElement(presentation);
    final String castType = getItemText(presentation, getCastItem());
    presentation.setItemText("(" + castType + ")" + presentation.getItemText());
    presentation.setTypeText(castType);
  }

  @Override
  public void handleInsert(InsertionContext context) {
    final CommonCodeStyleSettings settings = CompletionStyleUtil.getCodeStyleSettings(context);
    String spaceWithin = settings.SPACE_WITHIN_CAST_PARENTHESES ? " " : "";
    String spaceAfter = settings.SPACE_AFTER_TYPE_CAST ? " " : "";
    final Editor editor = context.getEditor();
    editor.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), "(" + spaceWithin + spaceWithin + ")" + spaceAfter);
    CompletionUtilCore.emulateInsertion(context, context.getStartOffset() + 1 + spaceWithin.length(), myCastItem);

    CompletionUtilCore.emulateInsertion(getDelegate(), context.getTailOffset(), context);
  }

  public LookupElement getCastItem() {
    return myCastItem;
  }

  static LookupElement createCastingElement(final LookupElement delegate, PsiType castTo) {
    return new CastingLookupElementDecorator(delegate, castTo);
  }
}
