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

import com.intellij.java.impl.codeInsight.lookup.TypedLookupItem;
import com.intellij.java.language.psi.*;
import consulo.document.Document;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.completion.ClassConditionKey;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.OffsetKey;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementDecorator;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.util.dataholder.Key;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class JavaChainLookupElement extends LookupElementDecorator<LookupElement> implements TypedLookupItem {
  public static final Key<Boolean> CHAIN_QUALIFIER = Key.create("CHAIN_QUALIFIER");
  private static final Logger LOG = Logger.getInstance(JavaChainLookupElement.class);
  public static final ClassConditionKey<JavaChainLookupElement> CLASS_CONDITION_KEY = ClassConditionKey.create(JavaChainLookupElement.class);
  private final LookupElement myQualifier;

  public JavaChainLookupElement(LookupElement qualifier, LookupElement main) {
    super(main);
    myQualifier = qualifier;
  }

  @Nonnull
  @Override
  public String getLookupString() {
    return maybeAddParentheses(myQualifier.getLookupString()) + "." + getDelegate().getLookupString();
  }

  public LookupElement getQualifier() {
    return myQualifier;
  }

  @Override
  public Set<String> getAllLookupStrings() {
    final Set<String> strings = getDelegate().getAllLookupStrings();
    final Set<String> result = new HashSet<String>();
    result.addAll(strings);
    result.add(getLookupString());
    return result;
  }

  @Nonnull
  @Override
  public String toString() {
    return maybeAddParentheses(myQualifier.toString()) + "." + getDelegate();
  }

  private String maybeAddParentheses(String s) {
    return getQualifierObject() instanceof PsiMethod ? s + "()" : s;
  }

  @Nullable
  private Object getQualifierObject() {
    Object qObject = myQualifier.getObject();
    if (qObject instanceof ResolveResult) {
      qObject = ((ResolveResult) qObject).getElement();
    }
    return qObject;
  }

  @Override
  public boolean isValid() {
    return super.isValid() && myQualifier.isValid();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);
    final LookupElementPresentation qualifierPresentation = new LookupElementPresentation();
    myQualifier.renderElement(qualifierPresentation);
    String name = maybeAddParentheses(qualifierPresentation.getItemText());
    final String qualifierText = myQualifier.as(CastingLookupElementDecorator.CLASS_CONDITION_KEY) != null ? "(" + name + ")" : name;
    presentation.setItemText(qualifierText + "." + presentation.getItemText());

    if (myQualifier instanceof JavaPsiClassReferenceElement) {
      String locationString = ((JavaPsiClassReferenceElement) myQualifier).getLocationString();
      presentation.setTailText(StringUtil.notNullize(presentation.getTailText()) + locationString);
    }
  }

  @Override
  public void handleInsert(InsertionContext context) {
    final Document document = context.getEditor().getDocument();
    document.replaceString(context.getStartOffset(), context.getTailOffset(), ";");
    myQualifier.putUserData(CHAIN_QUALIFIER, true);
    final InsertionContext qualifierContext = CompletionUtilCore.emulateInsertion(context, context.getStartOffset(), myQualifier);
    OffsetKey oldStart = context.trackOffset(context.getStartOffset(), false);

    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(document);

    int start = CharArrayUtil.shiftForward(context.getDocument().getCharsSequence(), context.getStartOffset(), " \t\n");
    if (shouldParenthesizeQualifier(context.getFile(), start, qualifierContext.getTailOffset())) {
      final String space = CodeStyleSettingsManager.getSettings(qualifierContext.getProject()).SPACE_WITHIN_PARENTHESES ? " " : "";
      document.insertString(start, "(" + space);
      document.insertString(qualifierContext.getTailOffset(), space + ")");
    }

    final char atTail = document.getCharsSequence().charAt(context.getTailOffset() - 1);
    if (atTail != ';') {
      return;
    }
    document.replaceString(context.getTailOffset() - 1, context.getTailOffset(), ".");

    CompletionUtilCore.emulateInsertion(getDelegate(), context.getTailOffset(), context);
    context.commitDocument();

    int formatStart = context.getOffset(oldStart);
    int formatEnd = context.getTailOffset();
    if (formatStart >= 0 && formatEnd >= 0) {
      CodeStyleManager.getInstance(context.getProject()).reformatText(context.getFile(), formatStart, formatEnd);
    }
  }

  protected boolean shouldParenthesizeQualifier(final PsiFile file, final int startOffset, final int endOffset) {
    PsiElement element = file.findElementAt(startOffset);
    if (element == null) {
      return false;
    }

    PsiElement last = element;
    while (element != null &&
        element.getTextRange().getStartOffset() >= startOffset && element.getTextRange().getEndOffset() <= endOffset &&
        !(element instanceof PsiExpressionStatement)) {
      last = element;
      element = element.getParent();
    }
    PsiExpression expr = PsiTreeUtil.getParentOfType(last, PsiExpression.class, false, PsiClass.class);
    if (expr == null) {
      return false;
    }
    if (expr.getTextRange().getEndOffset() > endOffset) {
      return true;
    }

    if (expr instanceof PsiJavaCodeReferenceElement ||
        expr instanceof PsiMethodCallExpression ||
        expr instanceof PsiNewExpression ||
        expr instanceof PsiArrayAccessExpression) {
      return false;
    }

    return true;
  }

  @Nonnull
  private LookupElement getComparableQualifier() {
    final CastingLookupElementDecorator casting = myQualifier.as(CastingLookupElementDecorator.CLASS_CONDITION_KEY);
    return casting == null ? myQualifier : casting.getDelegate();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    return getComparableQualifier().equals(((JavaChainLookupElement) o).getComparableQualifier());
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + getComparableQualifier().hashCode();
  }

  @Override
  public PsiType getType() {
    final Object object = getObject();
    if (object instanceof PsiMember) {
      return JavaCompletionUtil.getQualifiedMemberReferenceType(JavaCompletionUtil.getLookupElementType(myQualifier), (PsiMember) object);
    }
    return ((PsiVariable) object).getType();
  }
}
