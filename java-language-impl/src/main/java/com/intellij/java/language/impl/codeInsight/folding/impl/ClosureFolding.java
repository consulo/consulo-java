/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.impl.codeInsight.folding.impl;

import com.intellij.java.language.impl.codeInsight.daemon.impl.analysis.HighlightUtilBase;
import com.intellij.java.language.impl.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.dumb.IndexNotReadyException;
import consulo.codeEditor.FoldingGroup;
import consulo.document.Document;
import consulo.language.editor.folding.NamedFoldingDescriptor;
import consulo.language.psi.PsiElement;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
class ClosureFolding {
  @Nonnull
  private final PsiAnonymousClass myAnonymousClass;
  @Nonnull
  private final PsiNewExpression myNewExpression;
  @Nullable
  private final PsiClass myBaseClass;
  @Nonnull
  private final JavaFoldingBuilderBase myBuilder;
  @Nonnull
  private final PsiMethod myMethod;
  @Nonnull
  final PsiCodeBlock methodBody;
  private final boolean myQuick;

  private ClosureFolding(@Nonnull PsiAnonymousClass anonymousClass,
                         @Nonnull PsiNewExpression newExpression,
                         boolean quick,
                         @Nullable PsiClass baseClass,
                         @Nonnull JavaFoldingBuilderBase builder,
                         @Nonnull PsiMethod method,
                         @Nonnull PsiCodeBlock methodBody) {
    myAnonymousClass = anonymousClass;
    myNewExpression = newExpression;
    myQuick = quick;
    myBaseClass = baseClass;
    myBuilder = builder;
    myMethod = method;
    this.methodBody = methodBody;
  }

  @Nullable
  List<NamedFoldingDescriptor> process(Document document) {
    PsiJavaToken lbrace = methodBody.getLBrace();
    PsiJavaToken rbrace = methodBody.getRBrace();
    PsiElement classRBrace = myAnonymousClass.getRBrace();
    if (lbrace == null || rbrace == null || classRBrace == null) {
      return null;
    }

    CharSequence seq = document.getCharsSequence();
    int rangeStart = lbrace.getTextRange().getEndOffset();
    int rangeEnd = getContentRangeEnd(document, rbrace, classRBrace);

    String contents = getClosureContents(rangeStart, rangeEnd, seq);
    if (contents == null) {
      return null;
    }

    String header = getFoldingHeader();
    if (showSingleLineFolding(document, contents, header)) {
      return createDescriptors(classRBrace, trimStartSpaces(seq, rangeStart), trimTailSpaces(seq, rangeEnd), header + " ", " }");
    }

    return createDescriptors(classRBrace, rangeStart, rangeEnd, header, "}");
  }

  private static int trimStartSpaces(CharSequence seq, int rangeStart) {
    return CharArrayUtil.shiftForward(seq, rangeStart, " \n\t");
  }

  private static int trimTailSpaces(CharSequence seq, int rangeEnd) {
    return CharArrayUtil.shiftBackward(seq, rangeEnd - 1, " \n\t") + 1;
  }

  private static int getContentRangeEnd(Document document, PsiJavaToken rbrace, PsiElement classRBrace) {
    CharSequence seq = document.getCharsSequence();
    int rangeEnd = rbrace.getTextRange().getStartOffset();

    int methodEndLine = document.getLineNumber(rangeEnd);
    int methodEndLineStart = document.getLineStartOffset(methodEndLine);
    if ("}".equals(seq.subSequence(methodEndLineStart, document.getLineEndOffset(methodEndLine)).toString().trim())) {
      int classEndStart = classRBrace.getTextRange().getStartOffset();
      int classEndCol = classEndStart - document.getLineStartOffset(document.getLineNumber(classEndStart));
      return classEndCol + methodEndLineStart;
    }
    return rangeEnd;
  }

  private boolean showSingleLineFolding(Document document, String contents, String header) {
    return contents.indexOf('\n') < 0 && myBuilder.fitsRightMargin(myAnonymousClass, document, getClosureStartOffset(), getClosureEndOffset(), header.length() + contents.length() + 5);
  }

  private int getClosureEndOffset() {
    return myNewExpression.getTextRange().getEndOffset();
  }

  private int getClosureStartOffset() {
    return myNewExpression.getTextRange().getStartOffset();
  }

  @Nullable
  private List<NamedFoldingDescriptor> createDescriptors(PsiElement classRBrace, int rangeStart, int rangeEnd, String header, String footer) {
    if (rangeStart >= rangeEnd) {
      return null;
    }

    FoldingGroup group = FoldingGroup.newGroup("lambda");
    List<NamedFoldingDescriptor> foldElements = new ArrayList<>();
    foldElements.add(new NamedFoldingDescriptor(myNewExpression, getClosureStartOffset(), rangeStart, group, header));
    if (rangeEnd + 1 < getClosureEndOffset()) {
      foldElements.add(new NamedFoldingDescriptor(classRBrace, rangeEnd, getClosureEndOffset(), group, footer));
    }
    return foldElements;
  }

  @Nullable
  private static String getClosureContents(int rangeStart, int rangeEnd, CharSequence seq) {
    int firstLineStart = CharArrayUtil.shiftForward(seq, rangeStart, " \t");
    if (firstLineStart < seq.length() - 1 && seq.charAt(firstLineStart) == '\n') {
      firstLineStart++;
    }

    int lastLineEnd = CharArrayUtil.shiftBackward(seq, rangeEnd - 1, " \t");
    if (lastLineEnd > 0 && seq.charAt(lastLineEnd) == '\n') {
      lastLineEnd--;
    }
    if (lastLineEnd < firstLineStart) {
      return null;
    }
    return seq.subSequence(firstLineStart, lastLineEnd).toString();
  }

  private String getFoldingHeader() {
    String methodName = shouldShowMethodName() ? myMethod.getName() : "";
    String type = myQuick ? "" : getOptionalLambdaType();
    String params = StringUtil.join(myMethod.getParameterList().getParameters(), psiParameter -> psiParameter.getName(), ", ");
    return type + methodName + "(" + params + ") " + myBuilder.rightArrow() + " {";
  }

  @Nullable
  static ClosureFolding prepare(PsiAnonymousClass anonymousClass, boolean quick, JavaFoldingBuilderBase builder) {
    PsiElement parent = anonymousClass.getParent();
    if (parent instanceof PsiNewExpression && hasNoArguments((PsiNewExpression) parent)) {
      PsiClass baseClass = quick ? null : anonymousClass.getBaseClassType().resolve();
      if (hasOnlyOneLambdaMethod(anonymousClass, !quick) && (quick || seemsLikeLambda(baseClass, anonymousClass))) {
        PsiMethod method = anonymousClass.getMethods()[0];
        PsiCodeBlock body = method.getBody();
        if (body != null) {
          return new ClosureFolding(anonymousClass, (PsiNewExpression) parent, quick, baseClass, builder, method, body);
        }
      }
    }
    return null;
  }

  private static boolean hasNoArguments(PsiNewExpression expression) {
    PsiExpressionList argumentList = expression.getArgumentList();
    return argumentList != null && argumentList.getExpressions().length == 0;
  }

  private static boolean hasOnlyOneLambdaMethod(@Nonnull PsiAnonymousClass anonymousClass, boolean checkResolve) {
    PsiField[] fields = anonymousClass.getFields();
    if (fields.length != 0) {
      if (fields.length == 1 && HighlightUtilBase.SERIAL_VERSION_UID_FIELD_NAME.equals(fields[0].getName()) && fields[0].hasModifierProperty(PsiModifier.STATIC)) {
        //ok
      } else {
        return false;
      }
    }
    if (anonymousClass.getInitializers().length != 0 || anonymousClass.getInnerClasses().length != 0 || anonymousClass.getMethods().length != 1) {
      return false;
    }

    PsiMethod method = anonymousClass.getMethods()[0];
    if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      return false;
    }

    if (checkResolve) {
      for (PsiClassType type : method.getThrowsList().getReferencedTypes()) {
        if (type.resolve() == null) {
          return false;
        }
      }
    }

    return true;
  }

  static boolean seemsLikeLambda(@Nullable PsiClass baseClass, @Nonnull PsiElement context) {
    if (baseClass == null || !PsiUtil.hasDefaultConstructor(baseClass, true)) {
      return false;
    }

    if (PsiUtil.isLanguageLevel8OrHigher(context) && LambdaUtil.isFunctionalClass(baseClass)) {
      return false;
    }

    return true;
  }

  private String getOptionalLambdaType() {
    if (myBuilder.shouldShowExplicitLambdaType(myAnonymousClass, myNewExpression)) {
      String baseClassName = ObjectUtil.assertNotNull(myAnonymousClass.getBaseClassType().resolve()).getName();
      if (baseClassName != null) {
        return "(" + baseClassName + ") ";
      }
    }
    return "";
  }

  private boolean shouldShowMethodName() {
    if (myBaseClass == null || !myBaseClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return true;
    }

    for (PsiMethod method : myBaseClass.getMethods()) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return false;
      }
    }

    try {
      return OverrideImplementExploreUtil.getMethodSignaturesToImplement(myBaseClass).isEmpty();
    } catch (IndexNotReadyException e) {
      return true;
    }
  }

}
