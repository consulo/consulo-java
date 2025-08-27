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
package com.intellij.java.language.impl.psi.impl.source.tree;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.lexer.JavaLexer;
import com.intellij.java.language.impl.parser.JavaParser;
import com.intellij.java.language.impl.parser.JavaParserUtil;
import com.intellij.java.language.impl.parser.ReferenceParser;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.source.*;
import com.intellij.java.language.impl.psi.impl.source.tree.java.*;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.tree.java.IJavaElementType;
import consulo.language.Language;
import consulo.language.ast.*;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.lexer.Lexer;
import consulo.language.parser.PsiBuilder;
import consulo.language.util.FlyweightCapableTreeStructure;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Supplier;

public interface JavaElementType {
  class JavaCompositeElementType extends IJavaElementType implements ICompositeElementType {
    private final Supplier<? extends ASTNode> myFactory;

    private JavaCompositeElementType(final String debugName, final Supplier<? extends ASTNode> factory) {
      this(debugName, factory, false);
    }

    private JavaCompositeElementType(final String debugName, final Supplier<? extends ASTNode> factory, final boolean leftBound) {
      super(debugName, leftBound);
      myFactory = factory;
    }

    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return myFactory.get();
    }
  }

  IElementType CLASS = JavaStubElementTypes.CLASS;
  IElementType ANONYMOUS_CLASS = JavaStubElementTypes.ANONYMOUS_CLASS;
  IElementType IMPLICIT_CLASS = JavaStubElementTypes.IMPLICIT_CLASS;
  IElementType ENUM_CONSTANT_INITIALIZER = JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER;
  IElementType TYPE_PARAMETER_LIST = JavaStubElementTypes.TYPE_PARAMETER_LIST;
  IElementType TYPE_PARAMETER = JavaStubElementTypes.TYPE_PARAMETER;
  IElementType IMPORT_LIST = JavaStubElementTypes.IMPORT_LIST;
  IElementType IMPORT_STATEMENT = JavaStubElementTypes.IMPORT_STATEMENT;
  IElementType IMPORT_STATIC_STATEMENT = JavaStubElementTypes.IMPORT_STATIC_STATEMENT;
  IElementType MODIFIER_LIST = JavaStubElementTypes.MODIFIER_LIST;
  IElementType ANNOTATION = JavaStubElementTypes.ANNOTATION;
  IElementType NAME_VALUE_PAIR = JavaStubElementTypes.NAME_VALUE_PAIR;
  IElementType LITERAL_EXPRESSION = JavaStubElementTypes.LITERAL_EXPRESSION;
  IElementType ANNOTATION_PARAMETER_LIST = JavaStubElementTypes.ANNOTATION_PARAMETER_LIST;
  IElementType EXTENDS_LIST = JavaStubElementTypes.EXTENDS_LIST;
  IElementType IMPLEMENTS_LIST = JavaStubElementTypes.IMPLEMENTS_LIST;
  IElementType FIELD = JavaStubElementTypes.FIELD;
  IElementType ENUM_CONSTANT = JavaStubElementTypes.ENUM_CONSTANT;
  IElementType METHOD = JavaStubElementTypes.METHOD;
  IElementType ANNOTATION_METHOD = JavaStubElementTypes.ANNOTATION_METHOD;
  IElementType CLASS_INITIALIZER = JavaStubElementTypes.CLASS_INITIALIZER;
  IElementType PARAMETER = JavaStubElementTypes.PARAMETER;
  IElementType PARAMETER_LIST = JavaStubElementTypes.PARAMETER_LIST;
  IElementType EXTENDS_BOUND_LIST = JavaStubElementTypes.EXTENDS_BOUND_LIST;
  IElementType THROWS_LIST = JavaStubElementTypes.THROWS_LIST;
  IElementType LAMBDA_EXPRESSION = JavaStubElementTypes.LAMBDA_EXPRESSION;
  IElementType METHOD_REF_EXPRESSION = JavaStubElementTypes.METHOD_REFERENCE;
  IElementType MODULE = JavaStubElementTypes.MODULE;
  IElementType REQUIRES_STATEMENT = JavaStubElementTypes.REQUIRES_STATEMENT;
  IElementType EXPORTS_STATEMENT = JavaStubElementTypes.EXPORTS_STATEMENT;
  IElementType OPENS_STATEMENT = JavaStubElementTypes.OPENS_STATEMENT;
  IElementType USES_STATEMENT = JavaStubElementTypes.USES_STATEMENT;
  IElementType PROVIDES_STATEMENT = JavaStubElementTypes.PROVIDES_STATEMENT;
  IElementType PROVIDES_WITH_LIST = JavaStubElementTypes.PROVIDES_WITH_LIST;
  IElementType RECORD_COMPONENT = JavaStubElementTypes.RECORD_COMPONENT;
  IElementType RECORD_HEADER = JavaStubElementTypes.RECORD_HEADER;
  IElementType PERMITS_LIST = JavaStubElementTypes.PERMITS_LIST;

  IElementType IMPORT_STATIC_REFERENCE = new JavaCompositeElementType("IMPORT_STATIC_REFERENCE", PsiImportStaticReferenceElementImpl::new);
  IElementType TYPE = new JavaCompositeElementType("TYPE", PsiTypeElementImpl::new);
  IElementType DIAMOND_TYPE = new JavaCompositeElementType("DIAMOND_TYPE", PsiDiamondTypeElementImpl::new);
  IElementType REFERENCE_PARAMETER_LIST =
    new JavaCompositeElementType("REFERENCE_PARAMETER_LIST", PsiReferenceParameterListImpl::new, true);
  IElementType JAVA_CODE_REFERENCE = new JavaCompositeElementType("JAVA_CODE_REFERENCE", PsiJavaCodeReferenceElementImpl::new);
  IElementType PACKAGE_STATEMENT = new JavaCompositeElementType("PACKAGE_STATEMENT", PsiPackageStatementImpl::new);
  IElementType LOCAL_VARIABLE = new JavaCompositeElementType("LOCAL_VARIABLE", PsiLocalVariableImpl::new);
  IElementType REFERENCE_EXPRESSION = new JavaCompositeElementType("REFERENCE_EXPRESSION", PsiReferenceExpressionImpl::new);
  IElementType THIS_EXPRESSION = new JavaCompositeElementType("THIS_EXPRESSION", PsiThisExpressionImpl::new);
  IElementType SUPER_EXPRESSION = new JavaCompositeElementType("SUPER_EXPRESSION", PsiSuperExpressionImpl::new);
  IElementType PARENTH_EXPRESSION = new JavaCompositeElementType("PARENTH_EXPRESSION", PsiParenthesizedExpressionImpl::new);
  IElementType METHOD_CALL_EXPRESSION = new JavaCompositeElementType("METHOD_CALL_EXPRESSION", PsiMethodCallExpressionImpl::new);
  IElementType TYPE_CAST_EXPRESSION = new JavaCompositeElementType("TYPE_CAST_EXPRESSION", PsiTypeCastExpressionImpl::new);
  IElementType PREFIX_EXPRESSION = new JavaCompositeElementType("PREFIX_EXPRESSION", PsiPrefixExpressionImpl::new);
  IElementType POSTFIX_EXPRESSION = new JavaCompositeElementType("POSTFIX_EXPRESSION", PsiPostfixExpressionImpl::new);
  IElementType BINARY_EXPRESSION = new JavaCompositeElementType("BINARY_EXPRESSION", PsiBinaryExpressionImpl::new);
  IElementType POLYADIC_EXPRESSION = new JavaCompositeElementType("POLYADIC_EXPRESSION", PsiPolyadicExpressionImpl::new);
  IElementType CONDITIONAL_EXPRESSION = new JavaCompositeElementType("CONDITIONAL_EXPRESSION", PsiConditionalExpressionImpl::new);
  IElementType ASSIGNMENT_EXPRESSION = new JavaCompositeElementType("ASSIGNMENT_EXPRESSION", PsiAssignmentExpressionImpl::new);
  IElementType NEW_EXPRESSION = new JavaCompositeElementType("NEW_EXPRESSION", PsiNewExpressionImpl::new);
  IElementType ARRAY_ACCESS_EXPRESSION = new JavaCompositeElementType("ARRAY_ACCESS_EXPRESSION", PsiArrayAccessExpressionImpl::new);
  IElementType ARRAY_INITIALIZER_EXPRESSION =
    new JavaCompositeElementType("ARRAY_INITIALIZER_EXPRESSION", PsiArrayInitializerExpressionImpl::new);
  IElementType INSTANCE_OF_EXPRESSION = new JavaCompositeElementType("INSTANCE_OF_EXPRESSION", PsiInstanceOfExpressionImpl::new);
  IElementType CLASS_OBJECT_ACCESS_EXPRESSION =
    new JavaCompositeElementType("CLASS_OBJECT_ACCESS_EXPRESSION", PsiClassObjectAccessExpressionImpl::new);
  // TODO [VISTALL] stub just for using inside util
  IElementType TEMPLATE_EXPRESSION = new IElementType("TEMPLATE_EXPRESSION", JavaLanguage.INSTANCE);
  IElementType EMPTY_EXPRESSION = new JavaCompositeElementType("EMPTY_EXPRESSION", PsiEmptyExpressionImpl::new, true);
  IElementType EXPRESSION_LIST = new JavaCompositeElementType("EXPRESSION_LIST", PsiExpressionListImpl::new, true);
  IElementType EMPTY_STATEMENT = new JavaCompositeElementType("EMPTY_STATEMENT", PsiEmptyStatementImpl::new);
  IElementType BLOCK_STATEMENT = new JavaCompositeElementType("BLOCK_STATEMENT", PsiBlockStatementImpl::new);
  IElementType EXPRESSION_STATEMENT = new JavaCompositeElementType("EXPRESSION_STATEMENT", PsiExpressionStatementImpl::new);
  IElementType EXPRESSION_LIST_STATEMENT = new JavaCompositeElementType("EXPRESSION_LIST_STATEMENT", PsiExpressionListStatementImpl::new);
  IElementType DECLARATION_STATEMENT = new JavaCompositeElementType("DECLARATION_STATEMENT", PsiDeclarationStatementImpl::new);
  IElementType IF_STATEMENT = new JavaCompositeElementType("IF_STATEMENT", PsiIfStatementImpl::new);
  IElementType WHILE_STATEMENT = new JavaCompositeElementType("WHILE_STATEMENT", PsiWhileStatementImpl::new);
  IElementType FOR_STATEMENT = new JavaCompositeElementType("FOR_STATEMENT", PsiForStatementImpl::new);
  IElementType FOREACH_STATEMENT = new JavaCompositeElementType("FOREACH_STATEMENT", PsiForeachStatementImpl::new);
  IElementType DO_WHILE_STATEMENT = new JavaCompositeElementType("DO_WHILE_STATEMENT", PsiDoWhileStatementImpl::new);
  IElementType SWITCH_STATEMENT = new JavaCompositeElementType("SWITCH_STATEMENT", PsiSwitchStatementImpl::new);
  IElementType SWITCH_EXPRESSION = new JavaCompositeElementType("SWITCH_EXPRESSION", PsiSwitchExpressionImpl::new);
  IElementType SWITCH_LABEL_STATEMENT = new JavaCompositeElementType("SWITCH_LABEL_STATEMENT", PsiSwitchLabelStatementImpl::new);
  IElementType SWITCH_LABELED_RULE = new JavaCompositeElementType("SWITCH_LABELED_RULE", PsiSwitchLabeledRuleStatementImpl::new);
  IElementType YIELD_STATEMENT = new JavaCompositeElementType("YIELD_STATEMENT", PsiYieldStatementImpl::new);
  IElementType BREAK_STATEMENT = new JavaCompositeElementType("BREAK_STATEMENT", PsiBreakStatementImpl::new);
  IElementType CONTINUE_STATEMENT = new JavaCompositeElementType("CONTINUE_STATEMENT", PsiContinueStatementImpl::new);
  IElementType RETURN_STATEMENT = new JavaCompositeElementType("RETURN_STATEMENT", PsiReturnStatementImpl::new);
  IElementType THROW_STATEMENT = new JavaCompositeElementType("THROW_STATEMENT", PsiThrowStatementImpl::new);
  IElementType SYNCHRONIZED_STATEMENT = new JavaCompositeElementType("SYNCHRONIZED_STATEMENT", PsiSynchronizedStatementImpl::new);
  IElementType TRY_STATEMENT = new JavaCompositeElementType("TRY_STATEMENT", PsiTryStatementImpl::new);
  IElementType RESOURCE_LIST = new JavaCompositeElementType("RESOURCE_LIST", PsiResourceListImpl::new);
  IElementType RESOURCE_VARIABLE = new JavaCompositeElementType("RESOURCE_VARIABLE", PsiResourceVariableImpl::new);
  IElementType RESOURCE_EXPRESSION = new JavaCompositeElementType("RESOURCE_EXPRESSION", PsiResourceExpressionImpl::new);
  IElementType CATCH_SECTION = new JavaCompositeElementType("CATCH_SECTION", PsiCatchSectionImpl::new);
  IElementType LABELED_STATEMENT = new JavaCompositeElementType("LABELED_STATEMENT", PsiLabeledStatementImpl::new);
  IElementType ASSERT_STATEMENT = new JavaCompositeElementType("ASSERT_STATEMENT", PsiAssertStatementImpl::new);
  IElementType ANNOTATION_ARRAY_INITIALIZER =
    new JavaCompositeElementType("ANNOTATION_ARRAY_INITIALIZER", PsiArrayInitializerMemberValueImpl::new);
  IElementType RECEIVER_PARAMETER = new JavaCompositeElementType("RECEIVER", PsiReceiverParameterImpl::new);
  IElementType MODULE_REFERENCE = new JavaCompositeElementType("MODULE_REFERENCE", PsiJavaModuleReferenceElementImpl::new);
  IElementType TYPE_TEST_PATTERN = new JavaCompositeElementType("TYPE_TEST_PATTERN", PsiTypeTestPatternImpl::new);
  IElementType DECONSTRUCTION_PATTERN = new JavaCompositeElementType("DECONSTRUCTION_PATTERN", PsiDeconstructionPatternImpl::new);
  IElementType PATTERN_VARIABLE = new JavaCompositeElementType("PATTERN_VARIABLE", PsiPatternVariableImpl::new);
  IElementType DECONSTRUCTION_LIST =
    new JavaCompositeElementType("DECONSTRUCTION_LIST", PsiDeconstructionListImpl::new);
  IElementType DECONSTRUCTION_PATTERN_VARIABLE =
    new JavaCompositeElementType("DECONSTRUCTION_PATTERN_VARIABLE", PsiDeconstructionPatternVariableImpl::new);
  IElementType PARENTHESIZED_PATTERN =
    new JavaCompositeElementType("PARENTHESIZED_PATTERN", PsiParenthesizedPatternImpl::new);
  IElementType DEFAULT_CASE_LABEL_ELEMENT =
    new JavaCompositeElementType("DEFAULT_CASE_LABEL_ELEMENT", PsiDefaultLabelElementImpl::new);
  IElementType CASE_LABEL_ELEMENT_LIST =
    new JavaCompositeElementType("CASE_LABEL_ELEMENT_LIST", PsiCaseLabelElementListImpl::new);
  IElementType UNNAMED_PATTERN =
    new JavaCompositeElementType("UNNAMED_PATTERN", PsiUnnamedPatternImpl::new);
  IElementType FOREACH_PATTERN_STATEMENT =
    new JavaCompositeElementType("FOREACH_PATTERN_STATEMENT", PsiForeachPatternStatementImpl::new);

  class ICodeBlockElementType extends IErrorCounterReparseableElementType implements ICompositeElementType, ILightLazyParseableElementType {
    private ICodeBlockElementType() {
      super("CODE_BLOCK", JavaLanguage.INSTANCE);
    }

    @Override
    public ASTNode createNode(final CharSequence text) {
      return new PsiCodeBlockImpl(text);
    }

    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new PsiCodeBlockImpl(null);
    }

    @Override
    public ASTNode parseContents(@Nonnull final ASTNode chameleon) {
      final PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
      JavaParser.INSTANCE.getStatementParser().parseCodeBlockDeep(builder, true);
      return builder.getTreeBuilt().getFirstChildNode();
    }

    @Override
    public FlyweightCapableTreeStructure<LighterASTNode> parseContents(final LighterLazyParseableNode chameleon) {
      final PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
      JavaParser.INSTANCE.getStatementParser().parseCodeBlockDeep(builder, true);
      return builder.getLightTree();
    }

    @Override
    public int getErrorsCount(final CharSequence seq, Language fileLanguage, final Project project) {
      Lexer lexer = new JavaLexer(LanguageLevel.HIGHEST);
      return hasProperBraceBalance(seq, lexer, JavaTokenType.LBRACE, JavaTokenType.RBRACE) ? NO_ERRORS : FATAL_ERROR;
    }

    /**
     * Checks if `text` looks like a proper block.
     * In particular it
     * (1) checks brace balance
     * (2) verifies that the block's closing brace is the last token
     *
     * @param text       - text to check
     * @param lexer      - lexer to use
     * @param leftBrace  - left brace element type
     * @param rightBrace - right brace element type
     * @return true if `text` passes the checks
     */
    public static boolean hasProperBraceBalance(@Nonnull CharSequence text,
                                                @Nonnull Lexer lexer,
                                                @Nonnull IElementType leftBrace,
                                                @Nonnull IElementType rightBrace) {
      lexer.start(text);

      if (lexer.getTokenType() != leftBrace) {
        return false;
      }

      lexer.advance();
      int balance = 1;

      while (true) {
        IElementType type = lexer.getTokenType();

        if (type == null) {
          //eof: checking balance
          return balance == 0;
        }

        if (balance == 0) {
          //the last brace is not the last token
          return false;
        }

        if (type == leftBrace) {
          balance++;
        }
        else if (type == rightBrace) {
          balance--;
        }

        lexer.advance();
      }
    }
  }

  ILazyParseableElementType CODE_BLOCK = new ICodeBlockElementType();

  IElementType STATEMENTS = new ICodeFragmentElementType("STATEMENTS", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser = JavaParser.INSTANCE.getStatementParser()::parseStatements;

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }
  };

  IElementType EXPRESSION_TEXT = new ICodeFragmentElementType("EXPRESSION_TEXT", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser = JavaParser.INSTANCE.getExpressionParser()::parse;

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }
  };

  IElementType REFERENCE_TEXT = new ICodeFragmentElementType("REFERENCE_TEXT", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser =
      builder -> JavaParser.INSTANCE.getReferenceParser().parseJavaCodeReference(builder, false, true, false, false);

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }
  };

  IElementType TYPE_WITH_DISJUNCTIONS_TEXT = new TypeTextElementType("TYPE_WITH_DISJUNCTIONS_TEXT", ReferenceParser.DISJUNCTIONS);
  IElementType TYPE_WITH_CONJUNCTIONS_TEXT = new TypeTextElementType("TYPE_WITH_CONJUNCTIONS_TEXT", ReferenceParser.CONJUNCTIONS);

  class TypeTextElementType extends ICodeFragmentElementType {
    private final int myFlags;

    public TypeTextElementType(String debugName, int flags) {
      super(debugName, JavaLanguage.INSTANCE);
      myFlags = flags;
    }

    private final JavaParserUtil.ParserWrapper myParser = new JavaParserUtil.ParserWrapper() {
      @Override
      public void parse(final PsiBuilder builder) {
        int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.ELLIPSIS | ReferenceParser.WILDCARD | myFlags;
        JavaParser.INSTANCE.getReferenceParser().parseType(builder, flags);
      }
    };

    @Nullable
    @Override
    public ASTNode parseContents(@Nonnull final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }
  }

  class JavaDummyElementType extends ILazyParseableElementType implements ICompositeElementType {
    private JavaDummyElementType() {
      super("DUMMY_ELEMENT", JavaLanguage.INSTANCE);
    }

    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new CompositePsiElement(this) {
      };
    }

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      assert chameleon instanceof JavaDummyElement : chameleon;
      final JavaDummyElement dummyElement = (JavaDummyElement)chameleon;
      return JavaParserUtil.parseFragment(chameleon, dummyElement.getParser(), dummyElement.consumeAll(), dummyElement.getLanguageLevel());
    }
  }

  IElementType DUMMY_ELEMENT = new JavaDummyElementType();
}