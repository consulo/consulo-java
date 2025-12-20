/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.codeStyle.arrangement;

import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.arrangement.ArrangementSettings;
import consulo.language.codeStyle.arrangement.ArrangementUtil;
import consulo.language.codeStyle.arrangement.match.ArrangementSectionRule;
import consulo.language.codeStyle.arrangement.std.ArrangementSettingsToken;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.util.collection.Stack;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.function.Consumer;

import static consulo.language.codeStyle.arrangement.std.StdArrangementTokens.Section.END_SECTION;
import static consulo.language.codeStyle.arrangement.std.StdArrangementTokens.Section.START_SECTION;

/**
 * Class that is able to detect arrangement section start/end from comment element.
 * <p/>
 * The detection is based on arrangement settings.
 *
 * @author Svetlana.Zemlyanskaya
 */
public class ArrangementSectionDetector {
  private final Document myDocument;
  private final ArrangementSettings mySettings;
  private final Consumer<ArrangementSectionEntryTemplate> mySectionEntryProducer;
  private final Stack<ArrangementSectionRule> myOpenedSections = new Stack<>();

  public ArrangementSectionDetector(
    @Nullable Document document, @Nonnull ArrangementSettings settings, @Nonnull Consumer<ArrangementSectionEntryTemplate> producer) {
    myDocument = document;
    mySettings = settings;
    mySectionEntryProducer = producer;
  }

  /**
   * Check if comment can be recognized as section start/end
   *
   * @return true for section comment, false otherwise
   */
  public boolean processComment(@Nonnull PsiComment comment) {
    TextRange range = comment.getTextRange();
    TextRange expandedRange = myDocument == null ? range : ArrangementUtil.expandToLineIfPossible(range, myDocument);
    TextRange sectionTextRange = new TextRange(expandedRange.getStartOffset(), expandedRange.getEndOffset());

    String commentText = comment.getText().trim();
    ArrangementSectionRule openSectionRule = isSectionStartComment(mySettings, commentText);
    if (openSectionRule != null) {
      mySectionEntryProducer.accept(new ArrangementSectionEntryTemplate(comment, START_SECTION, sectionTextRange, commentText));
      myOpenedSections.push(openSectionRule);
      return true;
    }

    if (!myOpenedSections.isEmpty()) {
      ArrangementSectionRule lastSection = myOpenedSections.peek();
      if (lastSection.getEndComment() != null && StringUtil.equals(commentText, lastSection.getEndComment())) {
        mySectionEntryProducer.accept(new ArrangementSectionEntryTemplate(comment, END_SECTION, sectionTextRange, commentText));
        myOpenedSections.pop();
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static ArrangementSectionRule isSectionStartComment(@Nonnull ArrangementSettings settings, @Nonnull String comment) {
    for (ArrangementSectionRule rule : settings.getSections()) {
      if (rule.getStartComment() != null && StringUtil.equals(comment, rule.getStartComment())) {
        return rule;
      }
    }
    return null;
  }

  public static class ArrangementSectionEntryTemplate {
    private PsiElement myElement;
    private ArrangementSettingsToken myToken;
    private TextRange myTextRange;
    private String myText;

    public ArrangementSectionEntryTemplate(
      @Nonnull PsiElement element, @Nonnull ArrangementSettingsToken token, @Nonnull TextRange range, @Nonnull String text) {
      myElement = element;
      myToken = token;
      myTextRange = range;
      myText = text;
    }

    public PsiElement getElement() {
      return myElement;
    }

    public ArrangementSettingsToken getToken() {
      return myToken;
    }

    public TextRange getTextRange() {
      return myTextRange;
    }

    public String getText() {
      return myText;
    }
  }
}
