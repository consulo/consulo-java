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

import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.ast.TokenType;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.impl.ast.ASTFactory;
import consulo.language.impl.ast.Factory;
import consulo.language.impl.ast.LeafElement;
import consulo.language.impl.ast.SharedImplUtil;
import consulo.language.impl.psi.IndentHelper;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.parser.ParserDefinition;
import consulo.language.psi.OuterLanguageElement;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.util.CharTable;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.xml.psi.xml.XmlTokenType;

public class ShiftIndentInsideHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.Helper");

  private final CodeStyleSettings mySettings;
  private final FileType myFileType;
  private final IndentHelper myIndentHelper;
  private final Project myProject;

  public ShiftIndentInsideHelper(FileType fileType, Project project) {
    myProject = project;
    mySettings = CodeStyleSettingsManager.getSettings(project);
    myFileType = fileType;
    myIndentHelper = IndentHelper.getInstance();
  }

  private static int getStartOffset(ASTNode root, ASTNode child) {
    if (child == root) return 0;
    ASTNode parent = child.getTreeParent();
    int offset = 0;
    for (ASTNode child1 = parent.getFirstChildNode(); child1 != child; child1 = child1.getTreeNext()) {
      offset += child1.getTextLength();
    }
    return getStartOffset(root, parent) + offset;
  }

  public ASTNode shiftIndentInside(ASTNode element, int indentShift) {
    if (indentShift == 0) return element;
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(element);
    String text = element.getText();
    for (int offset = 0; offset < text.length(); offset++) {
      char c = text.charAt(offset);
      if (c == '\n' || c == '\r') {
        int offset1;
        for (offset1 = offset + 1; offset1 < text.length(); offset1++) {
          c = text.charAt(offset1);
          if (c != ' ' && c != '\t') break;
        }
        if (c == '\n' || c == '\r') continue;
        String space = text.substring(offset + 1, offset1);
        int indent = myIndentHelper.getIndent(myProject, myFileType, space, true);
        int newIndent = indent + indentShift;
        newIndent = Math.max(newIndent, 0);
        String newSpace = myIndentHelper.fillIndent(myProject, myFileType, newIndent);

        ASTNode leaf = element.findLeafElementAt(offset);
        if (!mayShiftIndentInside(leaf)) {
          LOG.error("Error",
              leaf.getElementType().toString(),
              "Type: " + leaf.getElementType() + " text: " + leaf.getText()
          );
        }

        if (offset1 < text.length()) {
          ASTNode next = element.findLeafElementAt(offset1);
          if ((next.getElementType() == JavaTokenType.END_OF_LINE_COMMENT
              || next.getElementType() == JavaTokenType.C_STYLE_COMMENT
              //|| next.getElementType() == JspTokenType.JSP_COMMENT
          ) &&
              next != element) {
            if (mySettings.KEEP_FIRST_COLUMN_COMMENT) {
              int commentIndent = myIndentHelper.getIndent(myProject, myFileType, next, true);
              if (commentIndent == 0) continue;
            }
          } else if (next.getElementType() == XmlTokenType.XML_DATA_CHARACTERS) {
            continue;
          }
        }

        int leafOffset = getStartOffset(element, leaf);
        if (leaf.getElementType() == JavaDocTokenType.DOC_COMMENT_DATA && leafOffset + leaf.getTextLength() == offset + 1) {
          ASTNode next = element.findLeafElementAt(offset + 1);
          if (next.getElementType() == TokenType.WHITE_SPACE) {
            leaf = next;
            leafOffset = getStartOffset(element, leaf);
          } else {
            if (newSpace.length() > 0) {
              LeafElement newLeaf = ASTFactory.whitespace(newSpace);
              next.getTreeParent().addChild(newLeaf, next);
            }
            text = text.substring(0, offset + 1) + newSpace + text.substring(offset1);
            continue;
          }
        }

        int startOffset = offset + 1 - leafOffset;
        int endOffset = offset1 - leafOffset;
        if (!LOG.assertTrue(0 <= startOffset && startOffset <= endOffset && endOffset <= leaf.getTextLength())) {
          continue;
        }
        String leafText = leaf.getText();
        String newLeafText = leafText.substring(0, startOffset) + newSpace + leafText.substring(endOffset);
        if (newLeafText.length() > 0) {
          LeafElement newLeaf = Factory.createSingleLeafElement(leaf.getElementType(), newLeafText, charTableByTree, SharedImplUtil.getManagerByTree(leaf));
          if (leaf.getTreeParent() != null) {
            leaf.getTreeParent().replaceChild(leaf, newLeaf);
          }
          if (leaf == element) {
            element = newLeaf;
          }
        } else {
          ASTNode parent = leaf.getTreeParent();
          if (parent != null) {
            parent.removeChild(leaf);
          }
        }
        text = text.substring(0, offset + 1) + newSpace + text.substring(offset1);
      }
    }
    return element;
  }

  public static boolean mayShiftIndentInside(final ASTNode leaf) {
    return (isComment(leaf) && !checkJspTexts(leaf))
        || leaf.getElementType() == TokenType.WHITE_SPACE
        || leaf.getElementType() == XmlTokenType.XML_DATA_CHARACTERS
        //|| leaf.getElementType() == JspTokenType.JAVA_CODE
        //  || leaf.getElementType() == JspElementType.JSP_SCRIPTLET
        || leaf.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;
  }

  private static boolean checkJspTexts(final ASTNode leaf) {
    ASTNode child = leaf.getFirstChildNode();
    while (child != null) {
      if (child instanceof OuterLanguageElement) return true;
      child = child.getTreeNext();
    }
    return false;
  }

  private static boolean isComment(final ASTNode node) {
    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(node);
    if (psiElement instanceof PsiComment) return true;
    final ParserDefinition parserDefinition = ParserDefinition.forLanguage(psiElement.getLanguage());
    if (parserDefinition == null) return false;
    final TokenSet commentTokens = parserDefinition.getCommentTokens(psiElement.getLanguageVersion());
    return commentTokens.contains(node.getElementType());
  }

  public FileType getFileType() {
    return myFileType;
  }
}
