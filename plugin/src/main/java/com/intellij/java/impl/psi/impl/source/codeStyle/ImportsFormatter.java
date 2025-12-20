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
package com.intellij.java.impl.psi.impl.source.codeStyle;

import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.codeStyle.FormattingDocumentModel;
import consulo.language.codeStyle.PostFormatProcessorHelper;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.xml.psi.XmlRecursiveElementVisitor;
import consulo.xml.psi.xml.*;
import jakarta.annotation.Nonnull;

/**
 * @author lesya
 */
public class ImportsFormatter extends XmlRecursiveElementVisitor {
  private static final Logger LOG = Logger.getInstance(ImportsFormatter.class);

  private final FormattingDocumentModel myDocumentModel;
  private final CommonCodeStyleSettings.IndentOptions myIndentOptions;
  private static final String PAGE_DIRECTIVE = "page";
  private static final String IMPORT_ATT = "import";

  private final PostFormatProcessorHelper myPostProcessor;

  public ImportsFormatter(@Nonnull CodeStyleSettings settings, @Nonnull PsiFile file) {
    myPostProcessor = new PostFormatProcessorHelper(settings);
    myDocumentModel = FormattingDocumentModel.create(file);
    myIndentOptions = settings.getIndentOptions(file.getFileType());
  }

  @Override
  public void visitXmlTag(XmlTag tag) {
    if (checkElementContainsRange(tag)) {
      super.visitXmlTag(tag);
    }
  }

  private static boolean isPageDirectiveTag(XmlTag tag) {
    return PAGE_DIRECTIVE.equals(tag.getName());
  }

  @Override
  public void visitXmlText(XmlText text) {

  }

  @Override
  public void visitXmlAttribute(XmlAttribute attribute) {
    if (isPageDirectiveTag(attribute.getParent())) {
      XmlAttributeValue valueElement = attribute.getValueElement();
      if (valueElement != null && checkRangeContainsElement(attribute) && isImportAttribute(attribute) && PostFormatProcessorHelper
          .isMultiline(valueElement)) {
        int oldLength = attribute.getTextLength();
        ASTNode valueToken = findValueToken(valueElement.getNode());
        if (valueToken != null) {
          String newAttributeValue = formatImports(valueToken.getStartOffset(), attribute.getValue());
          try {
            attribute.setValue(newAttributeValue);
          } catch (IncorrectOperationException e) {
            LOG.error(e);
          } finally {
            updateResultRange(oldLength, attribute.getTextLength());
          }
        }
      }
    }
  }

  private String formatImports(int startOffset, String value) {
    StringBuilder result = new StringBuilder();
    String offset = calcOffset(startOffset);
    String[] imports = value.split(",");
    if (imports.length >= 1) {
      result.append(imports[0]);
      for (int i = 1; i < imports.length; i++) {
        String anImport = imports[i];
        result.append(',');
        result.append('\n');
        result.append(offset);
        result.append(anImport.trim());
      }
    }
    return result.toString();
  }

  private String calcOffset(int startOffset) {
    StringBuffer result = new StringBuffer();

    int lineStartOffset = myDocumentModel.getLineStartOffset(myDocumentModel.getLineNumber(startOffset));
    int emptyLineEnd = CharArrayUtil.shiftForward(myDocumentModel.getDocument().getCharsSequence(), lineStartOffset, " \t");
    CharSequence spaces = myDocumentModel.getText(new TextRange(lineStartOffset, emptyLineEnd));

    if (spaces != null) {
      result.append(spaces.toString());
    }

    appendSpaces(result, startOffset - emptyLineEnd);

    return result.toString();
  }

  private void appendSpaces(StringBuffer result, int count) {
    if (myIndentOptions.USE_TAB_CHARACTER && !myIndentOptions.SMART_TABS) {
      int tabsCount = count / myIndentOptions.TAB_SIZE;
      int spaceCount = count - tabsCount * myIndentOptions.TAB_SIZE;
      StringUtil.repeatSymbol(result, '\t', tabsCount);
      StringUtil.repeatSymbol(result, ' ', spaceCount);
    } else {
      StringUtil.repeatSymbol(result, ' ', count);
    }
  }

  private static ASTNode findValueToken(ASTNode node) {
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      if (child.getElementType() == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN) return child;
      child = child.getTreeNext();
    }
    return null;
  }

  private static boolean isImportAttribute(XmlAttribute attribute) {
    return IMPORT_ATT.equals(attribute.getName());
  }

  protected void updateResultRange(int oldTextLength, int newTextLength) {
    myPostProcessor.updateResultRange(oldTextLength, newTextLength);
  }

  protected boolean checkElementContainsRange(PsiElement element) {
    return myPostProcessor.isElementPartlyInRange(element);
  }

  protected boolean checkRangeContainsElement(PsiElement element) {
    return myPostProcessor.isElementFullyInRange(element);
  }

  public PsiElement process(PsiElement formatted) {
    LOG.assertTrue(formatted.isValid());
    formatted.accept(this);
    return formatted;
  }

  public TextRange processText(PsiFile source, TextRange rangeToReformat) {
    myPostProcessor.setResultTextRange(rangeToReformat);
    source.accept(this);
    return myPostProcessor.getResultTextRange();
  }
}
