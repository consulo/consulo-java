/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.LambdaHighlightingUtil;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.TailTypes;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.java.impl.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.impl.psi.filters.position.StartElementFilter;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.Document;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.ast.IElementType;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionStyleUtil;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.*;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.*;
import consulo.language.psi.filter.AndFilter;
import consulo.language.psi.filter.ClassFilter;
import consulo.language.psi.filter.FilterPositionUtil;
import consulo.language.psi.filter.ScopeFilter;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Conditions;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import static com.intellij.java.language.patterns.PsiJavaPatterns.*;
import static consulo.language.pattern.StandardPatterns.not;
import static consulo.language.psi.SyntaxTraverser.psiApi;
import static consulo.util.lang.function.Conditions.notInstanceOf;

public class JavaKeywordCompletion {
  public static final ElementPattern<PsiElement> AFTER_DOT = psiElement().afterLeaf(".");

  static final ElementPattern<PsiElement> VARIABLE_AFTER_FINAL =
    psiElement().afterLeaf(PsiKeyword.FINAL).inside(PsiDeclarationStatement.class);

  private static final ElementPattern<PsiElement> INSIDE_PARAMETER_LIST =
    psiElement().withParent(psiElement(PsiJavaCodeReferenceElement.class).insideStarting(psiElement().withTreeParent
                                                                                                       (psiElement(PsiParameterList.class).andNot(
                                                                                                         psiElement(
                                                                                                           PsiAnnotationParameterList.class)))));

  private static final AndFilter START_OF_CODE_FRAGMENT =
    new AndFilter(new ScopeFilter(new AndFilter(new ClassFilter(JavaCodeFragment.class),
                                                new ClassFilter(PsiExpressionCodeFragment.class,
                                                                false),
                                                new ClassFilter(PsiJavaCodeReferenceCodeFragment.class, false),
                                                new ClassFilter(PsiTypeCodeFragment.class, false))), new StartElementFilter());
  private static final ElementPattern<PsiElement> INSIDE_RECORD_HEADER =
    psiElement().withParent(
      psiElement(PsiJavaCodeReferenceElement.class).insideStarting(
        or(
          psiElement().withTreeParent(
            psiElement(PsiRecordComponent.class)),
          psiElement().withTreeParent(
            psiElement(PsiRecordHeader.class)
          )
        )
      ));

  static final ElementPattern<PsiElement> START_SWITCH =
    psiElement().afterLeaf(psiElement().withText("{").withParents(PsiCodeBlock.class, PsiSwitchStatement.class));

  private static final ElementPattern<PsiElement> SUPER_OR_THIS_PATTERN =
    and(JavaSmartCompletionContributor.INSIDE_EXPRESSION,
        not(psiElement().afterLeaf(PsiKeyword.CASE)),
        not(psiElement()
              .afterLeaf(psiElement().withText(".")
                                     .afterLeaf(PsiKeyword.THIS,
                                                PsiKeyword.SUPER))),
        not(psiElement().inside(PsiAnnotation.class)),
        not(START_SWITCH),
        not
          (JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN));

  static final Set<String> PRIMITIVE_TYPES =
    Set.of(PsiKeyword.SHORT, PsiKeyword.BOOLEAN, PsiKeyword.DOUBLE, PsiKeyword.LONG, PsiKeyword.INT, PsiKeyword.FLOAT, PsiKeyword
      .CHAR, PsiKeyword.BYTE);

  static final ElementPattern<PsiElement> START_FOR = psiElement().afterLeaf(psiElement().withText("(").afterLeaf("for"))
                                                                  .withParents(PsiJavaCodeReferenceElement.class, PsiExpressionStatement
                                                                    .class, PsiForStatement.class);
  private static final ElementPattern<PsiElement> CLASS_REFERENCE =
    psiElement().withParent(psiReferenceExpression().referencing(psiClass().andNot(psiElement(PsiTypeParameter.class))));

  private static final ElementPattern<PsiElement> EXPR_KEYWORDS =
    and(psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(not(or(psiElement(PsiTypeCastExpression.class),
                                                                                           psiElement(PsiSwitchLabelStatement.class),
                                                                                           psiElement(PsiExpressionStatement.class),
                                                                                           psiElement(PsiPrefixExpression.class))))),
        not(psiElement().afterLeaf(".")));

  static boolean isEndOfBlock(@Nonnull PsiElement element) {
    PsiElement prev = prevSignificantLeaf(element);
    if (prev == null) {
      PsiFile file = element.getContainingFile();
      return !(file instanceof PsiCodeFragment) || isStatementCodeFragment(file);
    }

    if (psiElement().inside(psiAnnotation()).accepts(prev)) return false;

    if (prev instanceof OuterLanguageElement) return true;
    if (psiElement().withText(string().oneOf("{", "}", ";", ":", "else")).accepts(prev)) return true;
    if (prev.textMatches(")")) {
      PsiElement parent = prev.getParent();
      if (parent instanceof PsiParameterList) {
        return PsiTreeUtil.getParentOfType(PsiTreeUtil.prevVisibleLeaf(element), PsiDocComment.class) != null;
      }

      return !(parent instanceof PsiExpressionList || parent instanceof PsiTypeCastExpression
        || parent instanceof PsiRecordHeader);
    }

    return false;
  }

  private static boolean isStatementCodeFragment(PsiFile file) {
    return file instanceof JavaCodeFragment &&
      !(file instanceof PsiExpressionCodeFragment ||
        file instanceof PsiJavaCodeReferenceCodeFragment ||
        file instanceof PsiTypeCodeFragment);
  }

  private final CompletionParameters myParameters;
  private final JavaCompletionSession mySession;
  private final PsiElement myPosition;
  private final String myPrefix;
  private final List<LookupElement> myResults = new ArrayList<>();
  @Nullable
  private PsiElement myPrevLeaf;

  JavaKeywordCompletion(CompletionParameters parameters, JavaCompletionSession session) {
    myParameters = parameters;
    mySession = session;
    myPrefix = session.getMatcher().getPrefix();
    myPosition = parameters.getPosition();
    myPrevLeaf = PsiTreeUtil.prevVisibleLeaf(myPosition);

    addKeywords();
    addEnumCases();
  }

  private void addKeyword(LookupElement element) {
    if (element.getLookupString().startsWith(myPrefix)) {
      myResults.add(element);
    }
  }

  private static PsiElement prevSignificantLeaf(PsiElement position) {
    return FilterPositionUtil.searchNonSpaceNonCommentBack(position);
  }

  List<LookupElement> getResults() {
    return myResults;
  }

  public static boolean isInsideParameterList(PsiElement position) {
    PsiElement prev = PsiTreeUtil.prevVisibleLeaf(position);
    PsiModifierList modifierList = PsiTreeUtil.getParentOfType(prev, PsiModifierList.class);
    if (modifierList != null) {
      if (PsiTreeUtil.isAncestor(modifierList, position, false)) {
        return false;
      }
      PsiElement parent = modifierList.getParent();
      return parent instanceof PsiParameterList || parent instanceof PsiParameter && parent.getParent() instanceof PsiParameterList;
    }
    return INSIDE_PARAMETER_LIST.accepts(position);
  }

  private static TailType getReturnTail(PsiElement position) {
    PsiElement scope = position;
    while (true) {
      if (scope instanceof PsiFile || scope instanceof PsiClassInitializer) {
        return TailType.NONE;
      }

      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        if (method.isConstructor() || PsiType.VOID.equals(method.getReturnType())) {
          return TailType.SEMICOLON;
        }

        return TailType.HUMBLE_SPACE_BEFORE_WORD;
      }
      if (scope instanceof PsiLambdaExpression) {
        final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(((PsiLambdaExpression)scope));
        if (PsiType.VOID.equals(returnType)) {
          return TailType.SEMICOLON;
        }
        return TailType.HUMBLE_SPACE_BEFORE_WORD;
      }
      scope = scope.getParent();
    }
  }

  private void addStatementKeywords() {
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.SWITCH), TailTypes.SWITCH_LPARENTH));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.WHILE), TailTypes.WHILE_LPARENTH));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.DO), TailTypes.DO_LBRACE));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.FOR), TailTypes.FOR_LPARENTH));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.IF), TailTypes.IF_LPARENTH));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.TRY), TailTypes.TRY_LBRACE));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.THROW), TailType.INSERT_SPACE));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.NEW), TailType.INSERT_SPACE));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.SYNCHRONIZED), TailTypes.SYNCHRONIZED_LPARENTH));

    if (PsiUtil.getLanguageLevel(myPosition).isAtLeast(LanguageLevel.JDK_1_4)) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.ASSERT), TailType.INSERT_SPACE));
    }

    TailType returnTail = getReturnTail(myPosition);
    LookupElement ret = createKeyword(PsiKeyword.RETURN);
    if (returnTail != TailType.NONE) {
      ret = new OverridableSpace(ret, returnTail);
    }
    addKeyword(ret);

    if (psiElement().withText(";").withSuperParent(2, PsiIfStatement.class).accepts(myPrevLeaf) || psiElement().withText("}")
                                                                                                               .withSuperParent(3,
                                                                                                                                PsiIfStatement.class)
                                                                                                               .accepts(myPrevLeaf)) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.ELSE), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }

    if (psiElement().withText("}")
                    .withParent(psiElement(PsiCodeBlock.class).withParent(or(psiElement(PsiTryStatement.class),
                                                                             psiElement(PsiCatchSection.class))))
                    .accepts(myPrevLeaf)) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.CATCH), TailTypes.CATCH_LPARENTH));
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.FINALLY), TailTypes.FINALLY_LBRACE));
    }

  }

  void addKeywords() {
    if (PsiTreeUtil.getNonStrictParentOfType(myPosition, PsiLiteralExpression.class, PsiComment.class) != null) {
      return;
    }

    addFinal();

    boolean statementPosition = isStatementPosition(myPosition);
    if (statementPosition) {
      addCaseDefault();
      if (START_SWITCH.accepts(myPosition)) {
        return;
      }

      addBreakContinue();
      addStatementKeywords();
    }

    addThisSuper();

    addExpressionKeywords(statementPosition);

    addFileHeaderKeywords();

    addInstanceof();

    addClassKeywords();

    addMethodHeaderKeywords();

    addPrimitiveTypes(this::addKeyword, myPosition, mySession);

    addVar();

    addClassLiteral();

    addUnfinishedMethodTypeParameters();

    addExtendsImplements();
  }

  private void addVar() {
    if (isVarAllowed()) {
      addKeyword(createKeyword(PsiKeyword.VAR));
    }
  }

  @RequiredReadAction
  private boolean isVarAllowed() {
    if (PsiUtil.isAvailable(JavaFeature.VAR_LAMBDA_PARAMETER, myPosition) && isLambdaParameterType()) {
      return true;
    }

    if (!PsiUtil.isAvailable(JavaFeature.LVTI, myPosition)) return false;

    if (isAtCatchOrResourceVariableStart(myPosition) && PsiTreeUtil.getParentOfType(myPosition, PsiCatchSection.class) == null) {
      return true;
    }

    return isVariableTypePosition(myPosition) &&
      PsiTreeUtil.getParentOfType(myPosition, PsiCodeBlock.class, true, PsiMember.class, PsiLambdaExpression.class) != null;
  }

  private static boolean isVariableTypePosition(PsiElement position) {
    PsiElement parent = position.getParent();
    if (parent instanceof PsiJavaCodeReferenceElement && parent.getParent() instanceof PsiTypeElement &&
      parent.getParent().getParent() instanceof PsiDeclarationStatement) {
      return true;
    }
    return START_FOR.accepts(position) ||
      isInsideParameterList(position) ||
      INSIDE_RECORD_HEADER.accepts(position) ||
      VARIABLE_AFTER_FINAL.accepts(position) ||
      isStatementPosition(position);
  }

  private boolean isLambdaParameterType() {
    PsiElement position = myParameters.getOriginalPosition();
    PsiParameterList paramList = PsiTreeUtil.getParentOfType(position, PsiParameterList.class);
    if (paramList != null && paramList.getParent() instanceof PsiLambdaExpression) {
      PsiParameter param = PsiTreeUtil.getParentOfType(position, PsiParameter.class);
      PsiTypeElement type = param == null ? null : param.getTypeElement();
      return type == null || PsiTreeUtil.isAncestor(type, position, false);
    }
    return false;
  }

  static boolean addWildcardExtendsSuper(CompletionResultSet result, PsiElement position) {
    if (JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position)) {
      for (String keyword : List.of(PsiKeyword.EXTENDS, PsiKeyword.SUPER)) {
        if (keyword.startsWith(result.getPrefixMatcher().getPrefix())) {
          result.addElement(new OverridableSpace(BasicExpressionCompletionContributor.createKeywordLookupItem(position, keyword),
                                                 TailType.HUMBLE_SPACE_BEFORE_WORD));
        }
      }
      return true;
    }
    return false;
  }

  private void addMethodHeaderKeywords() {
    if (psiElement().withText(")").withParents(PsiParameterList.class, PsiMethod.class).accepts(myPrevLeaf)) {
      assert myPrevLeaf != null;
      if (myPrevLeaf.getParent().getParent() instanceof PsiAnnotationMethod) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.DEFAULT), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
      else {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.THROWS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }
  }

  private void addCaseDefault() {
    if (getSwitchFromLabelPosition(myPosition) != null) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.CASE), TailType.INSERT_SPACE));
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.DEFAULT), TailType.CASE_COLON));
    }
  }

  private static PsiSwitchStatement getSwitchFromLabelPosition(PsiElement position) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(position, PsiStatement.class, false, PsiMember.class);
    if (statement == null || statement.getTextRange().getStartOffset() != position.getTextRange().getStartOffset()) {
      return null;
    }

    if (!(statement instanceof PsiSwitchLabelStatement) && statement.getParent() instanceof PsiCodeBlock) {
      return ObjectUtil.tryCast(statement.getParent().getParent(), PsiSwitchStatement.class);
    }
    return null;
  }

  void addEnumCases() {
    PsiSwitchStatement switchStatement = getSwitchFromLabelPosition(myPosition);
    PsiExpression expression = switchStatement == null ? null : switchStatement.getExpression();
    PsiClass switchType = expression == null ? null : PsiUtil.resolveClassInClassTypeOnly(expression.getType());
    if (switchType == null || !switchType.isEnum()) {
      return;
    }

    Set<PsiField> used = ReferenceExpressionCompletionContributor.findConstantsUsedInSwitch(switchStatement);
    for (PsiField field : switchType.getAllFields()) {
      String name = field.getName();
      if (!(field instanceof PsiEnumConstant) || used.contains(CompletionUtilCore.getOriginalOrSelf(field)) || name == null) {
        continue;
      }
      String prefix = "case ";
      String suffix = name + ":";
      LookupElementBuilder caseConst =
        LookupElementBuilder.create(field, prefix + suffix).bold().withPresentableText(prefix).withTailText(suffix).withLookupString(name);
      myResults.add(new JavaCompletionContributor.IndentingDecorator(caseConst));
    }
  }

  private void addFinal() {
    PsiStatement statement = PsiTreeUtil.getParentOfType(myPosition, PsiExpressionStatement.class);
    if (statement == null) {
      statement = PsiTreeUtil.getParentOfType(myPosition, PsiDeclarationStatement.class);
    }
    if (statement != null && statement.getTextRange().getStartOffset() == myPosition.getTextRange().getStartOffset()) {
      if (!psiElement().withSuperParent(2, PsiSwitchStatement.class).afterLeaf("{").accepts(statement)) {
        PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(myPrevLeaf, PsiTryStatement.class);
        if (tryStatement == null || tryStatement.getCatchSections().length > 0 || tryStatement.getFinallyBlock() != null || tryStatement.getResourceList() != null) {
          addKeyword(new OverridableSpace(createKeyword(PsiKeyword.FINAL), TailType.HUMBLE_SPACE_BEFORE_WORD));
          return;
        }
      }
    }

    if ((isInsideParameterList(myPosition) || isAtResourceVariableStart(myPosition) || isAtCatchVariableStart(myPosition)) && !psiElement().afterLeaf(
      PsiKeyword.FINAL).accepts(myPosition) &&
      !AFTER_DOT.accepts(myPosition)) {
      addKeyword(TailTypeDecorator.withTail(createKeyword(PsiKeyword.FINAL), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }

  }

  private void addThisSuper() {
    if (SUPER_OR_THIS_PATTERN.accepts(myPosition)) {
      final boolean afterDot = AFTER_DOT.accepts(myPosition);
      final boolean insideQualifierClass = isInsideQualifierClass();
      final boolean insideInheritorClass = PsiUtil.isLanguageLevel8OrHigher(myPosition) && isInsideInheritorClass();
      if (!afterDot || insideQualifierClass || insideInheritorClass) {
        if (!afterDot || insideQualifierClass) {
          addKeyword(createKeyword(PsiKeyword.THIS));
        }

        final LookupElement superItem = createKeyword(PsiKeyword.SUPER);
        if (psiElement().afterLeaf(psiElement().withText("{").withSuperParent(2, psiMethod().constructor(true))).accepts(myPosition)) {
          final PsiMethod method = PsiTreeUtil.getParentOfType(myPosition, PsiMethod.class, false, PsiClass.class);
          assert method != null;
          final boolean hasParams = superConstructorHasParameters(method);
          addKeyword(LookupElementDecorator.withInsertHandler(superItem, new ParenthesesInsertHandler<LookupElement>() {
            @Override
            protected boolean placeCaretInsideParentheses(InsertionContext context, LookupElement item) {
              return hasParams;
            }

            @Override
            public void handleInsert(InsertionContext context, LookupElement item) {
              super.handleInsert(context, item);
              TailType.insertChar(context.getEditor(), context.getTailOffset(), ';');
            }
          }));
          return;
        }

        addKeyword(superItem);
      }
    }
  }

  private void addExpressionKeywords(boolean statementPosition) {
    if (psiElement(JavaTokenType.DOUBLE_COLON).accepts(myPrevLeaf)) {
      PsiMethodReferenceExpression parent = PsiTreeUtil.getParentOfType(myPosition, PsiMethodReferenceExpression.class);
      TailType tail = parent != null && !LambdaHighlightingUtil.insertSemicolon(parent.getParent()) ? TailType.SEMICOLON : TailType.NONE;
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.NEW), tail));
      return;
    }

    if (isExpressionPosition(myPosition)) {
      if (PsiTreeUtil.getParentOfType(myPosition, PsiAnnotation.class) == null) {
        if (!statementPosition) {
          addKeyword(TailTypeDecorator.withTail(createKeyword(PsiKeyword.NEW), TailType.INSERT_SPACE));
        }
        addKeyword(createKeyword(PsiKeyword.NULL));
      }
      if (mayExpectBoolean(myParameters)) {
        addKeyword(createKeyword(PsiKeyword.TRUE));
        addKeyword(createKeyword(PsiKeyword.FALSE));
      }
    }
  }

  private void addFileHeaderKeywords() {
    PsiFile file = myPosition.getContainingFile();
    assert file != null;

    if (!(file instanceof PsiExpressionCodeFragment) &&
      !(file instanceof PsiJavaCodeReferenceCodeFragment) &&
      !(file instanceof PsiTypeCodeFragment)) {
      if (myPrevLeaf == null) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.PACKAGE), JavaTailTypes.humbleSpaceBeforeWordType()));
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.IMPORT), JavaTailTypes.humbleSpaceBeforeWordType()));
      }
      else if (psiElement().inside(psiAnnotation().withParents(PsiModifierList.class, PsiFile.class)).accepts(myPrevLeaf)
        && PsiJavaPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.PACKAGE), JavaTailTypes.humbleSpaceBeforeWordType()));
      }
      else if (isEndOfBlock(myPosition) && PsiTreeUtil.getParentOfType(myPosition, PsiMember.class) == null) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.IMPORT), JavaTailTypes.humbleSpaceBeforeWordType()));
      }
    }

    if (PsiUtil.isAvailable(JavaFeature.STATIC_IMPORTS, file) && myPrevLeaf != null && myPrevLeaf.textMatches(PsiKeyword.IMPORT)) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.STATIC), JavaTailTypes.humbleSpaceBeforeWordType()));
    }
  }

  private void addInstanceof() {
    if (isInstanceofPlace(myPosition)) {
      addKeyword(LookupElementDecorator.withInsertHandler(createKeyword(PsiKeyword.INSTANCEOF), (context, item) -> {
        TailType tailType = TailType.HUMBLE_SPACE_BEFORE_WORD;
        if (tailType.isApplicable(context)) {
          tailType.processTail(context.getEditor(), context.getTailOffset());
        }

        if ('!' == context.getCompletionChar()) {
          context.setAddCompletionChar(false);
          context.commitDocument();
          PsiInstanceOfExpression expr = PsiTreeUtil.findElementOfClassAtOffset(
            context.getFile(),
            context.getStartOffset(),
            PsiInstanceOfExpression.class,
            false);
          if (expr != null) {
            String space =
              CompletionStyleUtil.getCodeStyleSettings(context).SPACE_WITHIN_PARENTHESES ? " " : "";
            context.getDocument()
                   .insertString(expr.getTextRange().getStartOffset(), "!(" + space);
            context.getDocument().insertString(context.getTailOffset(), space + ")");
          }
        }
      }));
    }
  }

  @RequiredReadAction
  private void addClassKeywords() {
    if (isSuitableForClass(myPosition)) {
      for (String s : ModifierChooser.getKeywords(myPosition)) {
        addKeyword(new OverridableSpace(createKeyword(s), JavaTailTypes.humbleSpaceBeforeWordType()));
      }

      if (psiElement().insideStarting(psiElement(PsiLocalVariable.class, PsiExpressionStatement.class)).accepts(myPosition)) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.CLASS), JavaTailTypes.humbleSpaceBeforeWordType()));
        addKeyword(new OverridableSpace(LookupElementBuilder.create("abstract class").bold(), JavaTailTypes.humbleSpaceBeforeWordType()));
        if (PsiUtil.isAvailable(JavaFeature.RECORDS, myPosition)) {
          addKeyword(new OverridableSpace(createKeyword(PsiKeyword.RECORD), JavaTailTypes.humbleSpaceBeforeWordType()));
        }
        if (PsiUtil.isAvailable(JavaFeature.LOCAL_ENUMS, myPosition)) {
          addKeyword(new OverridableSpace(createKeyword(PsiKeyword.ENUM), JavaTailTypes.humbleSpaceBeforeWordType()));
        }
        if (PsiUtil.isAvailable(JavaFeature.LOCAL_INTERFACES, myPosition)) {
          addKeyword(new OverridableSpace(createKeyword(PsiKeyword.INTERFACE), JavaTailTypes.humbleSpaceBeforeWordType()));
        }
      }
      if (PsiTreeUtil.getParentOfType(myPosition, PsiExpression.class, true, PsiMember.class) == null &&
        PsiTreeUtil.getParentOfType(myPosition, PsiCodeBlock.class, true, PsiMember.class) == null) {
        List<String> keywords = new ArrayList<>();
        keywords.add(PsiKeyword.CLASS);
        keywords.add(PsiKeyword.INTERFACE);
        if (PsiUtil.isAvailable(JavaFeature.RECORDS, myPosition)) {
          keywords.add(PsiKeyword.RECORD);
        }
        if (PsiUtil.isAvailable(JavaFeature.ENUMS, myPosition)) {
          keywords.add(PsiKeyword.ENUM);
        }
        String className = recommendClassName();
        for (String keyword : keywords) {
          if (className == null) {
            addKeyword(new OverridableSpace(createKeyword(keyword), JavaTailTypes.humbleSpaceBeforeWordType()));
          }
          else {
            addKeyword(createTypeDeclaration(keyword, className));
          }
        }
      }
    }

    if (psiElement().withText("@").andNot(psiElement().inside(PsiParameterList.class)).andNot(psiElement().inside(psiNameValuePair()))
                    .accepts(myPrevLeaf)) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.INTERFACE), JavaTailTypes.humbleSpaceBeforeWordType()));
    }
  }

  @Nonnull
  @RequiredReadAction
  private LookupElement createTypeDeclaration(String keyword, String className) {
    LookupElement element;
    PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(PsiTreeUtil.nextLeaf(myPosition));
    IElementType nextToken;
    if (nextElement instanceof PsiJavaToken) {
      nextToken = ((PsiJavaToken)nextElement).getTokenType();
    }
    else {
      if (nextElement instanceof PsiParameterList l && l.getFirstChild() instanceof PsiJavaToken t) {
        nextToken = t.getTokenType();
      }
      else if (nextElement instanceof PsiCodeBlock b && b.getFirstChild() instanceof PsiJavaToken t) {
        nextToken = t.getTokenType();
      }
      else {
        nextToken = null;
      }
    }
    element = LookupElementBuilder.create(keyword + " " + className).withPresentableText(keyword).bold()
                                  .withTailText(" " + className, false)
                                  .withIcon(CreateClassKind.valueOf(keyword.toUpperCase(Locale.ROOT)).getKindIcon())
                                  .withInsertHandler((context, item) -> {
                                    Document document = context.getDocument();
                                    int offset = context.getTailOffset();
                                    String suffix = " ";
                                    if (keyword.equals(PsiKeyword.RECORD)) {
                                      if (JavaTokenType.LPARENTH.equals(nextToken)) {
                                        suffix = "";
                                      }
                                      else if (JavaTokenType.LBRACE.equals(nextToken)) {
                                        suffix = "() ";
                                      }
                                      else {
                                        suffix = "() {\n}";
                                      }
                                    }
                                    else if (!JavaTokenType.LBRACE.equals(nextToken)) {
                                      suffix = " {\n}";
                                    }
                                    if (offset < document.getTextLength() && document.getCharsSequence().charAt(offset) == ' ') {
                                      suffix = suffix.trim();
                                    }
                                    document.insertString(offset, suffix);
                                    context.getEditor().getCaretModel().moveToOffset(offset + 1);
                                  });
    return element;
  }

  @Nullable
  @RequiredReadAction
  private String recommendClassName() {
    if (myPrevLeaf == null) return null;
    if (!myPrevLeaf.textMatches(PsiKeyword.PUBLIC) || !(myPrevLeaf.getParent() instanceof PsiModifierList)) return null;

    if (nextIsIdentifier(myPosition)) return null;

    PsiJavaFile file = getFileForDeclaration(myPrevLeaf);
    if (file == null) return null;
    String name = file.getName();
    if (!StringUtil.endsWithIgnoreCase(name, JavaFileType.DOT_DEFAULT_EXTENSION)) return null;
    String candidate = name.substring(0, name.length() - JavaFileType.DOT_DEFAULT_EXTENSION.length());
    if (StringUtil.isJavaIdentifier(candidate) && !ContainerUtil.exists(file.getClasses(), c -> candidate.equals(c.getName()))) {
      return candidate;
    }
    return null;
  }

  private static boolean nextIsIdentifier(@Nonnull PsiElement position) {
    PsiElement nextLeaf = PsiTreeUtil.nextLeaf(position);
    if (nextLeaf == null) return false;
    PsiElement parent = nextLeaf.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) return false;
    PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiTypeElement)) return false;
    return PsiTreeUtil.skipWhitespacesAndCommentsForward(grandParent) instanceof PsiIdentifier;
  }

  @Nullable
  private static PsiJavaFile getFileForDeclaration(@Nonnull PsiElement elementBeforeName) {
    PsiElement parent = elementBeforeName.getParent();
    if (parent == null) return null;
    PsiElement grandParent = parent.getParent();
    if (grandParent == null) return null;
    if (grandParent instanceof PsiJavaFile f) {
      return f;
    }
    PsiElement grandGrandParent = grandParent.getParent();
    if (grandGrandParent == null) return null;
    return ObjectUtil.tryCast(grandGrandParent.getParent(), PsiJavaFile.class);
  }

  private void addClassLiteral() {
    if (isAfterTypeDot(myPosition)) {
      addKeyword(createKeyword(PsiKeyword.CLASS));
    }
  }

  private void addExtendsImplements() {
    if (myPrevLeaf == null ||
      !(myPrevLeaf instanceof PsiIdentifier || myPrevLeaf.textMatches(">") || myPrevLeaf.textMatches(")"))) {
      return;
    }

    PsiClass psiClass = null;
    PsiElement prevParent = myPrevLeaf.getParent();
    if (myPrevLeaf instanceof PsiIdentifier && prevParent instanceof PsiClass) {
      psiClass = (PsiClass)prevParent;
    }
    else {
      PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(myPrevLeaf, PsiReferenceList.class);
      if (referenceList != null && referenceList.getParent() instanceof PsiClass) {
        psiClass = (PsiClass)referenceList.getParent();
      }
      else if ((prevParent instanceof PsiTypeParameterList || prevParent instanceof PsiRecordHeader)
        && prevParent.getParent() instanceof PsiClass) {
        psiClass = (PsiClass)prevParent.getParent();
      }
    }

    if (psiClass != null) {
      if (!psiClass.isEnum() && !psiClass.isRecord()) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.EXTENDS), JavaTailTypes.humbleSpaceBeforeWordType()));
        if (PsiUtil.isAvailable(JavaFeature.SEALED_CLASSES, psiClass)) {
          PsiModifierList modifiers = psiClass.getModifierList();
          if (myParameters.getInvocationCount() > 1 ||
            (modifiers != null &&
              !modifiers.hasExplicitModifier(PsiModifier.FINAL) &&
              !modifiers.hasExplicitModifier(PsiModifier.NON_SEALED))) {
            InsertHandler<LookupElement> handler = (context, item) -> {
              PsiClass aClass = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiClass.class, false);
              if (aClass != null) {
                PsiModifierList modifierList = aClass.getModifierList();
                if (modifierList != null) {
                  modifierList.setModifierProperty(PsiModifier.SEALED, true);
                }
              }
            };
            LookupElement element =
              new OverridableSpace(LookupElementDecorator.withInsertHandler(createKeyword(PsiKeyword.PERMITS), handler),
                                   JavaTailTypes.humbleSpaceBeforeWordType());
            addKeyword(element);
          }
        }
      }
      if (!psiClass.isInterface()) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.IMPLEMENTS), JavaTailTypes.humbleSpaceBeforeWordType()));
      }
    }
  }

  private static boolean mayExpectBoolean(CompletionParameters parameters) {
    for (ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
      PsiType type = info.getType();
      if (type instanceof PsiClassType || PsiType.BOOLEAN.equals(type)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isExpressionPosition(PsiElement position) {
    return EXPR_KEYWORDS.accepts(position) || psiElement().insideStarting(psiElement(PsiClassObjectAccessExpression.class))
                                                          .accepts(position);
  }

  public static boolean isInstanceofPlace(PsiElement position) {
    PsiElement prev = PsiTreeUtil.prevVisibleLeaf(position);
    if (prev == null) {
      return false;
    }

    PsiElement expr = PsiTreeUtil.getParentOfType(prev, PsiExpression.class);
    if (expr != null && expr.getTextRange().getEndOffset() == prev.getTextRange().getEndOffset()) {
      return true;
    }

    if (position instanceof PsiIdentifier && position.getParent() instanceof PsiLocalVariable) {
      PsiType type = ((PsiLocalVariable)position.getParent()).getType();
      if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == null) {
        return true;
      }
    }

    return false;
  }

  public static boolean isSuitableForClass(PsiElement position) {
    if (psiElement().afterLeaf("@").accepts(position) ||
      PsiTreeUtil.getNonStrictParentOfType(position, PsiLiteralExpression.class, PsiComment.class, PsiExpressionCodeFragment.class) !=
        null) {
      return false;
    }

    PsiElement prev = prevSignificantLeaf(position);
    if (prev == null) {
      return true;
    }
    if (psiElement().withoutText(".").inside(
      psiElement(PsiModifierList.class).withParent(
        not(psiElement(PsiParameter.class)).andNot(psiElement(PsiParameterList.class)))).accepts(prev) &&
      (!psiElement().inside(PsiAnnotationParameterList.class).accepts(prev) || prev.textMatches(")"))) {
      return true;
    }

    if (psiElement().withParents(PsiErrorElement.class, PsiFile.class).accepts(position)) {
      return true;
    }

    return isEndOfBlock(position);
  }

  private void addUnfinishedMethodTypeParameters() {
    final ProcessingContext context = new ProcessingContext();
    if (psiElement().inside(psiElement(PsiTypeElement.class).afterLeaf(psiElement().withText(">")
                                                                                   .withParent(psiElement(PsiTypeParameterList.class).withParent(
                                                                                     PsiErrorElement.class).save
                                                                                                                                       ("typeParameterList"))))
                    .accepts(myPosition, context)) {
      final PsiTypeParameterList list = (PsiTypeParameterList)context.get("typeParameterList");
      PsiElement current = list.getParent().getParent();
      if (current instanceof PsiField) {
        current = current.getParent();
      }
      if (current instanceof PsiClass) {
        for (PsiTypeParameter typeParameter : list.getTypeParameters()) {
          addKeyword(new JavaPsiClassReferenceElement(typeParameter));
        }
      }
    }
  }

  static boolean isAfterPrimitiveOrArrayType(PsiElement element) {
    return psiElement().withParent(psiReferenceExpression().withFirstChild(psiElement(PsiClassObjectAccessExpression.class).withLastChild(
      not(psiElement().withText(PsiKeyword.CLASS))))).accepts
                         (element);
  }

  static boolean isAfterTypeDot(PsiElement position) {
    if (isInsideParameterList(position) || position.getContainingFile() instanceof PsiJavaCodeReferenceCodeFragment) {
      return false;
    }

    return psiElement().afterLeaf(psiElement().withText(".").afterLeaf(CLASS_REFERENCE)).accepts(position) || isAfterPrimitiveOrArrayType(
      position);
  }

  static void addPrimitiveTypes(Consumer<? super LookupElement> result, PsiElement position, JavaCompletionSession session) {
    if (AFTER_DOT.accepts(position) ||
      psiElement().inside(psiAnnotation()).accepts(position) && !expectsClassLiteral(position)) {
      return;
    }

    boolean afterNew = JavaSmartCompletionContributor.AFTER_NEW.accepts(position) &&
      !psiElement().afterLeaf(psiElement().afterLeaf(".")).accepts(position);
    if (afterNew) {
      Set<PsiType> expected = ContainerUtil.map2Set(JavaSmartCompletionContributor.getExpectedTypes(position, false),
                                                    ExpectedTypeInfo::getDefaultType);
      boolean addAll = expected.isEmpty() || ContainerUtil.exists(expected, t ->
        t.equalsToText(JavaClassNames.JAVA_LANG_OBJECT) || t.equalsToText(JavaClassNames.JAVA_IO_SERIALIZABLE));
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(position.getProject());
      for (String primitiveType : PRIMITIVE_TYPES) {
        PsiType array = factory.createTypeFromText(primitiveType + "[]", null);
        if (addAll || expected.contains(array)) {
          result.accept(PsiTypeLookupItem.createLookupItem(array, null));
        }
      }
      return;
    }

    boolean inCast = psiElement()
      .afterLeaf(psiElement().withText("(").withParent(psiElement(PsiParenthesizedExpression.class, PsiTypeCastExpression.class)))
      .accepts(position);

    boolean typeFragment = position.getContainingFile() instanceof PsiTypeCodeFragment && PsiTreeUtil.prevVisibleLeaf(position) == null;
    boolean declaration = isDeclarationStart(position);
    boolean expressionPosition = isExpressionPosition(position);
    boolean inGenerics = PsiTreeUtil.getParentOfType(position, PsiReferenceParameterList.class) != null;
    if (isVariableTypePosition(position) ||
      inGenerics ||
      inCast ||
      declaration ||
      typeFragment ||
      expressionPosition) {
      for (String primitiveType : PRIMITIVE_TYPES) {
        if (!session.isKeywordAlreadyProcessed(primitiveType)) {
          result.accept(BasicExpressionCompletionContributor.createKeywordLookupItem(position, primitiveType));
        }
      }
      if (expressionPosition && !session.isKeywordAlreadyProcessed(PsiKeyword.VOID)) {
        result.accept(BasicExpressionCompletionContributor.createKeywordLookupItem(position, PsiKeyword.VOID));
      }
    }
    if (declaration) {
      LookupElement item = BasicExpressionCompletionContributor.createKeywordLookupItem(position, PsiKeyword.VOID);
      result.accept(new OverridableSpace(item, JavaTailTypes.humbleSpaceBeforeWordType()));
    }
    else if (typeFragment && ((PsiTypeCodeFragment)position.getContainingFile()).isVoidValid()) {
      result.accept(BasicExpressionCompletionContributor.createKeywordLookupItem(position, PsiKeyword.VOID));
    }
  }

  private static boolean expectsClassLiteral(PsiElement position) {
    return ContainerUtil.find(JavaSmartCompletionContributor.getExpectedTypes(position, false),
                              info -> InheritanceUtil.isInheritor(info.getType(), JavaClassNames.JAVA_LANG_CLASS)) != null;
  }

  private static boolean isAtResourceVariableStart(PsiElement position) {
    return psiElement().insideStarting(psiElement(PsiTypeElement.class).withParent(PsiResourceList.class)).accepts(position);
  }

  private static boolean isAtCatchVariableStart(PsiElement position) {
    return psiElement().insideStarting(psiElement(PsiTypeElement.class).withParent(PsiCatchSection.class)).accepts(position);
  }

  static boolean isDeclarationStart(@Nonnull PsiElement position) {
    if (psiElement().afterLeaf("@", ".").accepts(position)) return false;

    PsiElement parent = position.getParent();
    if (parent instanceof PsiJavaCodeReferenceElement && parent.getParent() instanceof PsiTypeElement) {
      PsiElement typeHolder = psiApi().parents(parent.getParent()).skipWhile(Conditions.instanceOf(PsiTypeElement.class)).first();
      return typeHolder instanceof PsiMember || typeHolder instanceof PsiClassLevelDeclarationStatement;
    }

    return false;
  }

  private static boolean isAtCatchOrResourceVariableStart(PsiElement position) {
    PsiElement type = PsiTreeUtil.getParentOfType(position, PsiTypeElement.class);
    if (type != null && type.getTextRange().getStartOffset() == position.getTextRange().getStartOffset()) {
      PsiElement parent = type.getParent();
      if (parent instanceof PsiVariable) {
        parent = parent.getParent();
      }
      return parent instanceof PsiCatchSection || parent instanceof PsiResourceList;
    }
    return psiElement().insideStarting(psiElement(PsiResourceExpression.class)).accepts(position);
  }

  private void addBreakContinue() {
    PsiLoopStatement loop =
      PsiTreeUtil.getParentOfType(myPosition, PsiLoopStatement.class, true, PsiLambdaExpression.class, PsiMember.class);

    LookupElement br = createKeyword(PsiKeyword.BREAK);
    LookupElement cont = createKeyword(PsiKeyword.CONTINUE);
    TailType tailType;
    if (psiElement().insideSequence(true,
                                    psiElement(PsiLabeledStatement.class),
                                    or(psiElement(PsiFile.class), psiElement(PsiMethod.class), psiElement(PsiClassInitializer.class)))
                    .accepts
                      (myPosition)) {
      tailType = TailType.HUMBLE_SPACE_BEFORE_WORD;
    }
    else {
      tailType = TailType.SEMICOLON;
    }
    br = TailTypeDecorator.withTail(br, tailType);
    cont = TailTypeDecorator.withTail(cont, tailType);

    if (loop != null && PsiTreeUtil.isAncestor(loop.getBody(), myPosition, false)) {
      addKeyword(br);
      addKeyword(cont);
    }
    if (psiElement().inside(PsiSwitchStatement.class).accepts(myPosition)) {
      addKeyword(br);
    }

    for (PsiLabeledStatement labeled : psiApi().parents(myPosition)
                                               .takeWhile(notInstanceOf(PsiMember.class))
                                               .filter(PsiLabeledStatement.class)) {
      addKeyword(TailTypeDecorator.withTail(LookupElementBuilder.create("break " + labeled.getName()).bold(), TailType.SEMICOLON));
    }
  }

  private static boolean isStatementPosition(PsiElement position) {
    if (psiElement()
      .withSuperParent(2, PsiConditionalExpression.class)
      .andNot(psiElement().insideStarting(psiElement(PsiConditionalExpression.class)))
      .accepts(position)) {
      return false;
    }

    if (isEndOfBlock(position) &&
      PsiTreeUtil.getParentOfType(position, PsiCodeBlock.class, true, PsiMember.class) != null) {
      return !isForLoopMachinery(position);
    }

    if (psiElement().withParents(PsiReferenceExpression.class, PsiExpressionStatement.class, PsiIfStatement.class).andNot(
      psiElement().afterLeaf(".")).accepts(position)) {
      PsiElement stmt = position.getParent().getParent();
      PsiIfStatement ifStatement = (PsiIfStatement)stmt.getParent();
      return ifStatement.getElseBranch() == stmt || ifStatement.getThenBranch() == stmt;
    }

    return false;
  }

  private static boolean isForLoopMachinery(PsiElement position) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(position, PsiStatement.class);
    if (statement == null) return false;

    return statement instanceof PsiForStatement ||
      statement.getParent() instanceof PsiForStatement && statement != ((PsiForStatement)statement.getParent()).getBody();
  }

  private LookupElement createKeyword(String keyword) {
    return BasicExpressionCompletionContributor.createKeywordLookupItem(myPosition, keyword);
  }

  private boolean isInsideQualifierClass() {
    if (myPosition.getParent() instanceof PsiJavaCodeReferenceElement) {
      final PsiElement qualifier = ((PsiJavaCodeReferenceElement)myPosition.getParent()).getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiElement qualifierClass = ((PsiJavaCodeReferenceElement)qualifier).resolve();
        if (qualifierClass instanceof PsiClass) {
          PsiElement parent = myPosition;
          final PsiManager psiManager = myPosition.getManager();
          while ((parent = PsiTreeUtil.getParentOfType(parent, PsiClass.class, true)) != null) {
            if (psiManager.areElementsEquivalent(parent, qualifierClass)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private boolean isInsideInheritorClass() {
    if (myPosition.getParent() instanceof PsiJavaCodeReferenceElement) {
      final PsiElement qualifier = ((PsiJavaCodeReferenceElement)myPosition.getParent()).getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiElement qualifierClass = ((PsiJavaCodeReferenceElement)qualifier).resolve();
        if (qualifierClass instanceof PsiClass && ((PsiClass)qualifierClass).isInterface()) {
          PsiElement parent = myPosition;
          while ((parent = PsiTreeUtil.getParentOfType(parent, PsiClass.class, true)) != null) {
            if (PsiUtil.getEnclosingStaticElement(myPosition,
                                                  (PsiClass)parent) == null && ((PsiClass)parent).isInheritor((PsiClass)qualifierClass,
                                                                                                              true)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean superConstructorHasParameters(PsiMethod method) {
    final PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) {
      return false;
    }

    final PsiClass superClass = psiClass.getSuperClass();
    if (superClass != null) {
      for (final PsiMethod psiMethod : superClass.getConstructors()) {
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(method.getProject()).getResolveHelper();
        if (resolveHelper.isAccessible(psiMethod, method, null) && psiMethod.getParameterList().getParameters().length > 0) {
          return true;
        }
      }
    }
    return false;
  }

  public static class OverridableSpace extends TailTypeDecorator<LookupElement> {
    private final TailType myTail;

    public OverridableSpace(LookupElement keyword, TailType tail) {
      super(keyword);
      myTail = tail;
    }

    @Override
    protected TailType computeTailType(InsertionContext context) {
      return context.shouldAddCompletionChar() ? TailType.NONE : myTail;
    }
  }
}
