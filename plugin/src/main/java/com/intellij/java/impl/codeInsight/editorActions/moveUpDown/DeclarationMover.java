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
package com.intellij.java.impl.codeInsight.editorActions.moveUpDown;

import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.LogicalPosition;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.moveUpDown.LineMover;
import consulo.language.editor.moveUpDown.LineRange;
import consulo.language.impl.ast.Factory;
import consulo.language.impl.ast.TreeElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl(id = "javaDeclaration", order = "before xml")
public class DeclarationMover extends LineMover {
  private static final Logger LOG = Logger.getInstance(DeclarationMover.class);
  private PsiEnumConstant myEnumToInsertSemicolonAfter;

  @Override
  public void beforeMove(@Nonnull final Editor editor, @Nonnull final MoveInfo info, final boolean down) {
    super.beforeMove(editor, info, down);

    if (myEnumToInsertSemicolonAfter != null) {
      TreeElement semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, null, myEnumToInsertSemicolonAfter.getManager());

      try {
        PsiElement inserted = myEnumToInsertSemicolonAfter.getParent().addAfter(semicolon.getPsi(), myEnumToInsertSemicolonAfter);
        inserted = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(inserted);
        final LogicalPosition position = editor.offsetToLogicalPosition(inserted.getTextRange().getEndOffset());

        info.toMove2 = new LineRange(position.line + 1, position.line + 1);
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      } finally {
        myEnumToInsertSemicolonAfter = null;
      }
    }
  }

  @Override
  public boolean checkAvailable(@Nonnull final Editor editor, @Nonnull final PsiFile file, @Nonnull final MoveInfo info, final boolean down) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }

    boolean available = super.checkAvailable(editor, file, info, down);
    if (!available) return false;

    LineRange oldRange = info.toMove;
    final Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, oldRange);
    if (psiRange == null) return false;

    final PsiMember firstMember = PsiTreeUtil.getParentOfType(psiRange.getFirst(), PsiMember.class, false);
    final PsiMember lastMember = PsiTreeUtil.getParentOfType(psiRange.getSecond(), PsiMember.class, false);
    if (firstMember == null || lastMember == null) return false;

    LineRange range;
    if (firstMember == lastMember) {
      range = memberRange(firstMember, editor, oldRange);
      if (range == null) return false;
      range.firstElement = range.lastElement = firstMember;
    } else {
      final PsiElement parent = PsiTreeUtil.findCommonParent(firstMember, lastMember);
      if (parent == null) return false;

      final Pair<PsiElement, PsiElement> combinedRange = getElementRange(parent, firstMember, lastMember);
      if (combinedRange == null) return false;
      final LineRange lineRange1 = memberRange(combinedRange.getFirst(), editor, oldRange);
      if (lineRange1 == null) return false;
      final LineRange lineRange2 = memberRange(combinedRange.getSecond(), editor, oldRange);
      if (lineRange2 == null) return false;
      range = new LineRange(lineRange1.startLine, lineRange2.endLine);
      range.firstElement = combinedRange.getFirst();
      range.lastElement = combinedRange.getSecond();
    }
    Document document = editor.getDocument();

    PsiElement sibling = down ? range.lastElement.getNextSibling() : range.firstElement.getPrevSibling();
    if (sibling == null) return false;
    sibling = firstNonWhiteElement(sibling, down);
    final boolean areWeMovingClass = range.firstElement instanceof PsiClass;
    info.toMove = range;
    try {
      LineRange intraClassRange = moveInsideOutsideClassPosition(editor, sibling, down, areWeMovingClass);
      if (intraClassRange == null) {
        info.toMove2 = new LineRange(sibling, sibling, document);
        if (down && sibling.getNextSibling() == null) return false;
      } else {
        info.toMove2 = intraClassRange;
      }
    } catch (IllegalMoveException e) {
      info.toMove2 = null;
    }
    return true;
  }

  private static LineRange memberRange(@Nonnull PsiElement member, Editor editor, LineRange lineRange) {
    final TextRange textRange = member.getTextRange();
    if (editor.getDocument().getTextLength() < textRange.getEndOffset()) return null;
    final int startLine = editor.offsetToLogicalPosition(textRange.getStartOffset()).line;
    final int endLine = editor.offsetToLogicalPosition(textRange.getEndOffset()).line + 1;
    if (!isInsideDeclaration(member, startLine, endLine, lineRange, editor)) return null;

    return new LineRange(startLine, endLine);
  }

  private static boolean isInsideDeclaration(@Nonnull final PsiElement member,
                                             final int startLine,
                                             final int endLine,
                                             final LineRange lineRange,
                                             final Editor editor) {
    // if we positioned on member start or end we'll be able to move it
    if (startLine == lineRange.startLine || startLine == lineRange.endLine || endLine == lineRange.startLine ||
        endLine == lineRange.endLine) {
      return true;
    }
    List<PsiElement> memberSuspects = new ArrayList<PsiElement>();
    PsiModifierList modifierList = member instanceof PsiMember ? ((PsiMember) member).getModifierList() : null;
    if (modifierList != null) memberSuspects.add(modifierList);
    if (member instanceof PsiClass) {
      final PsiClass aClass = (PsiClass) member;
      if (aClass instanceof PsiAnonymousClass) return false; // move new expression instead of anon class
      PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
      if (nameIdentifier != null) memberSuspects.add(nameIdentifier);
    }
    if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) member;
      PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier != null) memberSuspects.add(nameIdentifier);
      PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      if (returnTypeElement != null) memberSuspects.add(returnTypeElement);
    }
    if (member instanceof PsiField) {
      final PsiField field = (PsiField) member;
      PsiIdentifier nameIdentifier = field.getNameIdentifier();
      memberSuspects.add(nameIdentifier);
      PsiTypeElement typeElement = field.getTypeElement();
      if (typeElement != null) memberSuspects.add(typeElement);
    }
    TextRange lineTextRange = new TextRange(editor.getDocument().getLineStartOffset(lineRange.startLine), editor.getDocument().getLineEndOffset(lineRange.endLine));
    for (PsiElement suspect : memberSuspects) {
      TextRange textRange = suspect.getTextRange();
      if (textRange != null && lineTextRange.intersects(textRange)) return true;
    }
    return false;
  }

  private static class IllegalMoveException extends Exception {
  }

  // null means we are not crossing class border
  // throws IllegalMoveException when corresponding movement has no sense
  @Nullable
  private LineRange moveInsideOutsideClassPosition(Editor editor, PsiElement sibling, final boolean isDown, boolean areWeMovingClass) throws IllegalMoveException {
    if (sibling == null) throw new IllegalMoveException();
    if (sibling instanceof PsiJavaToken &&
        ((PsiJavaToken) sibling).getTokenType() == (isDown ? JavaTokenType.RBRACE : JavaTokenType.LBRACE) &&
        sibling.getParent() instanceof PsiClass) {
      // moving outside class
      final PsiClass aClass = (PsiClass) sibling.getParent();
      final PsiElement parent = aClass.getParent();
      if (!areWeMovingClass && !(parent instanceof PsiClass)) throw new IllegalMoveException();
      if (aClass instanceof PsiAnonymousClass) throw new IllegalMoveException();
      PsiElement start = isDown ? sibling : aClass.getModifierList();
      return new LineRange(start, sibling, editor.getDocument());
      //return isDown ? nextLineOffset(editor, aClass.getTextRange().getEndOffset()) : aClass.getTextRange().getStartOffset();
    }
    // trying to move up inside enum constant list, move outside of enum class instead
    if (!isDown
        && sibling.getParent() instanceof PsiClass
        && (sibling instanceof PsiJavaToken && ((PsiJavaToken) sibling).getTokenType() == JavaTokenType.SEMICOLON || sibling instanceof PsiErrorElement)
        && firstNonWhiteElement(sibling.getPrevSibling(), false) instanceof PsiEnumConstant) {
      PsiClass aClass = (PsiClass) sibling.getParent();
      Document document = editor.getDocument();
      int startLine = document.getLineNumber(aClass.getTextRange().getStartOffset());
      int endLine = document.getLineNumber(sibling.getTextRange().getEndOffset()) + 1;
      return new LineRange(startLine, endLine);
    }
    if (sibling instanceof PsiClass) {
      // moving inside class
      PsiClass aClass = (PsiClass) sibling;
      if (aClass instanceof PsiAnonymousClass) throw new IllegalMoveException();
      if (isDown) {
        PsiElement child = aClass.getFirstChild();
        if (child == null) throw new IllegalMoveException();
        return new LineRange(child, aClass.isEnum() ? afterEnumConstantsPosition(aClass) : aClass.getLBrace(),
            editor.getDocument());
      } else {
        PsiElement rBrace = aClass.getRBrace();
        if (rBrace == null) throw new IllegalMoveException();
        return new LineRange(rBrace, rBrace, editor.getDocument());
      }
    }
    /*if (sibling instanceof JspClassLevelDeclarationStatement) {
      // there should be another scriptlet/decl to move
      if (firstNonWhiteElement(isDown ? sibling.getNextSibling() : sibling.getPrevSibling(), isDown) == null) throw new IllegalMoveException();
    }  */
    return null;
  }

  private PsiElement afterEnumConstantsPosition(final PsiClass aClass) {
    PsiField[] fields = aClass.getFields();
    for (int i = fields.length - 1; i >= 0; i--) {
      PsiField field = fields[i];
      if (field instanceof PsiEnumConstant) {
        PsiElement anchor = firstNonWhiteElement(field.getNextSibling(), true);
        if (!(anchor instanceof PsiJavaToken && ((PsiJavaToken) anchor).getTokenType() == JavaTokenType.SEMICOLON)) {
          anchor = field;
          myEnumToInsertSemicolonAfter = (PsiEnumConstant) field;
        }
        return anchor;
      }
    }
    // no enum constants at all ?
    return aClass.getLBrace();
  }
}
