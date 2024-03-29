/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.java.impl.codeInsight.ExpectedTypesProvider;
import com.intellij.java.impl.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.impl.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.codeInsight.completion.CodeCompletionHandlerBase;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.OffsetKey;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.Lookup;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.filter.FilterPositionUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;

import static com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings.*;

/**
 * @author peter
 */
class JavaClassNameInsertHandler implements InsertHandler<JavaPsiClassReferenceElement> {
  static final InsertHandler<JavaPsiClassReferenceElement> JAVA_CLASS_INSERT_HANDLER = new JavaClassNameInsertHandler();

  @Override
  public void handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
    int offset = context.getTailOffset() - 1;
    final PsiFile file = context.getFile();
    if (PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiImportStatementBase.class, false) != null) {
      final PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiJavaCodeReferenceElement.class, false);
      final String qname = item.getQualifiedName();
      if (qname != null && (ref == null || !qname.equals(ref.getCanonicalText()))) {
        AllClassesGetter.INSERT_FQN.handleInsert(context, item);
      }
      return;
    }

    PsiElement position = file.findElementAt(offset);
    PsiJavaCodeReferenceElement ref = position != null && position.getParent() instanceof PsiJavaCodeReferenceElement ? (PsiJavaCodeReferenceElement) position.getParent() : null;
    PsiClass psiClass = item.getObject();
    final Project project = context.getProject();

    final Editor editor = context.getEditor();
    final char c = context.getCompletionChar();
    if (c == '#') {
      context.setLaterRunnable(() -> new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor));
    } else if (c == '.' && PsiTreeUtil.getParentOfType(position, PsiParameterList.class) == null) {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }

    String qname = psiClass.getQualifiedName();
    if (qname != null && PsiTreeUtil.getParentOfType(position, PsiDocComment.class, false) != null && (ref == null || !ref.isQualified()) && shouldInsertFqnInJavadoc(item, file, project)) {
      context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), qname);
      return;
    }

    if (ref != null && PsiTreeUtil.getParentOfType(position, PsiDocTag.class) != null && ref.isReferenceTo(psiClass)) {
      return;
    }

    OffsetKey refEnd = context.trackOffset(context.getTailOffset(), false);

    boolean fillTypeArgs = context.getCompletionChar() == '<';
    if (fillTypeArgs) {
      context.setAddCompletionChar(false);
    }

    if (ref == null || !ref.isQualified()) {
      PsiTypeLookupItem.addImportForItem(context, psiClass);
    }
    if (!context.getOffsetMap().containsOffset(refEnd)) {
      return;
    }

    context.setTailOffset(context.getOffset(refEnd));

    context.commitDocument();
    if (item.getUserData(JavaChainLookupElement.CHAIN_QUALIFIER) == null && shouldInsertParentheses(file.findElementAt(context.getTailOffset() - 1))) {
      if (ConstructorInsertHandler.insertParentheses(context, item, psiClass, false)) {
        fillTypeArgs |= psiClass.hasTypeParameters() && PsiUtil.getLanguageLevel(file).isAtLeast(LanguageLevel.JDK_1_5);
      }
    } else if (insertingAnnotation(context, item)) {
      if (shouldHaveAnnotationParameters(psiClass)) {
        JavaCompletionUtil.insertParentheses(context, item, false, true);
      }
      if (context.getCompletionChar() == Lookup.NORMAL_SELECT_CHAR || context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
        CharSequence text = context.getDocument().getCharsSequence();
        int tail = context.getTailOffset();
        if (text.length() > tail && Character.isLetter(text.charAt(tail))) {
          context.getDocument().insertString(tail, " ");
        }
      }
    }

    if (fillTypeArgs && context.getCompletionChar() != '(') {
      JavaCompletionUtil.promptTypeArgs(context, context.getOffset(refEnd));
    }
  }

  private static boolean shouldInsertFqnInJavadoc(@Nonnull JavaPsiClassReferenceElement item, @Nonnull PsiFile file, @Nonnull Project project) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    switch (javaSettings.CLASS_NAMES_IN_JAVADOC) {
      case FULLY_QUALIFY_NAMES_ALWAYS:
        return true;
      case SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT:
        return false;
      case FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED:
        if (file instanceof PsiJavaFile) {
          PsiJavaFile javaFile = ((PsiJavaFile) file);
          return item.getQualifiedName() != null && !ImportHelper.isAlreadyImported(javaFile, item.getQualifiedName());
        }
      default:
        return false;
    }
  }

  private static boolean shouldInsertParentheses(PsiElement position) {
    final PsiJavaCodeReferenceElement ref = PsiTreeUtil.getParentOfType(position, PsiJavaCodeReferenceElement.class);
    if (ref == null) {
      return false;
    }

    final PsiReferenceParameterList parameterList = ref.getParameterList();
    if (parameterList != null && parameterList.getTextLength() > 0) {
      return false;
    }

    final PsiElement prevElement = FilterPositionUtil.searchNonSpaceNonCommentBack(ref);
    if (prevElement != null && prevElement.getParent() instanceof PsiNewExpression) {
      return !isArrayTypeExpected((PsiExpression) prevElement.getParent());
    }

    return false;
  }

  static boolean isArrayTypeExpected(PsiExpression expr) {
    return ContainerUtil.exists(ExpectedTypesProvider.getExpectedTypes(expr, true), info -> info.getType() instanceof PsiArrayType);
  }

  private static boolean insertingAnnotation(InsertionContext context, LookupElement item) {
    final Object obj = item.getObject();
    if (!(obj instanceof PsiClass) || !((PsiClass) obj).isAnnotationType()) {
      return false;
    }

    PsiElement leaf = context.getFile().findElementAt(context.getStartOffset());
    PsiAnnotation anno = PsiTreeUtil.getParentOfType(leaf, PsiAnnotation.class);
    return anno != null && PsiTreeUtil.isAncestor(anno.getNameReferenceElement(), leaf, false);
  }

  static boolean shouldHaveAnnotationParameters(PsiClass annoClass) {
    for (PsiMethod m : annoClass.getMethods()) {
      if (!PsiUtil.isAnnotationMethod(m)) {
        continue;
      }
      if (((PsiAnnotationMethod) m).getDefaultValue() == null) {
        return true;
      }
    }
    return false;
  }
}
