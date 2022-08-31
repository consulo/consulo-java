// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.parser;

import com.intellij.AbstractBundle;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.function.Predicate;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.java.language.impl.parser.JavaParserUtil.*;

public class FileParser
{
	protected static final TokenSet IMPORT_LIST_STOPPER_SET = TokenSet.orSet(
			ElementType.MODIFIER_BIT_SET,
			TokenSet.create(JavaTokenType.CLASS_KEYWORD, JavaTokenType.INTERFACE_KEYWORD, JavaTokenType.ENUM_KEYWORD, JavaTokenType.AT));

	private final JavaParser myParser;

	public FileParser(@Nonnull JavaParser javaParser)
	{
		myParser = javaParser;
	}

	public void parse(@Nonnull PsiBuilder builder)
	{
		parseFile(builder, FileParser::stopImportListParsing, JavaErrorBundle.getInstance(), "expected.class.or.interface");
	}

	public void parseFile(@Nonnull PsiBuilder builder,
						  @Nonnull Predicate<? super PsiBuilder> importListStopper,
						  @Nonnull AbstractBundle bundle,
						  @Nonnull String errorMessageKey)
	{
		parsePackageStatement(builder);

		Pair<PsiBuilder.Marker, Boolean> impListInfo = parseImportList(builder, importListStopper);  // (importList, isEmpty)
		Boolean firstDeclarationOk = null;
		PsiBuilder.Marker firstDeclaration = null;

		PsiBuilder.Marker invalidElements = null;
		while(!builder.eof())
		{
			if(builder.getTokenType() == JavaTokenType.SEMICOLON)
			{
				builder.advanceLexer();
				continue;
			}

			PsiBuilder.Marker declaration = myParser.getModuleParser().parse(builder);
			if(declaration == null)
			{
				declaration = parseInitial(builder);
			}
			if(declaration != null)
			{
				if(invalidElements != null)
				{
					invalidElements.errorBefore(error(bundle, errorMessageKey), declaration);
					invalidElements = null;
				}
				if(firstDeclarationOk == null)
				{
					firstDeclarationOk = exprType(declaration) != JavaElementType.MODIFIER_LIST;
					if(firstDeclarationOk)
					{
						firstDeclaration = declaration;
					}
				}
				continue;
			}

			if(invalidElements == null)
			{
				invalidElements = builder.mark();
			}
			builder.advanceLexer();
			if(firstDeclarationOk == null)
			{
				firstDeclarationOk = false;
			}
		}

		if(invalidElements != null)
		{
			invalidElements.error(error(bundle, errorMessageKey));
		}

		if(impListInfo.second && firstDeclarationOk == Boolean.TRUE)
		{
			impListInfo.first.setCustomEdgeTokenBinders(PRECEDING_COMMENT_BINDER, null);  // pass comments behind fake import list
			firstDeclaration.setCustomEdgeTokenBinders(SPECIAL_PRECEDING_COMMENT_BINDER, null);
		}
	}

	private static boolean stopImportListParsing(PsiBuilder b)
	{
		IElementType type = b.getTokenType();
		if(IMPORT_LIST_STOPPER_SET.contains(type) || DeclarationParser.isRecordToken(b, type))
		{
			return true;
		}
		if(type == JavaTokenType.IDENTIFIER)
		{
			String text = b.getTokenText();
			if(PsiKeyword.OPEN.equals(text) || PsiKeyword.MODULE.equals(text))
			{
				return true;
			}
		}
		return false;
	}

	@Nullable
	protected PsiBuilder.Marker parseInitial(PsiBuilder builder)
	{
		return myParser.getDeclarationParser().parse(builder, DeclarationParser.Context.FILE);
	}

	private void parsePackageStatement(PsiBuilder builder)
	{
		PsiBuilder.Marker statement = builder.mark();

		if(!expect(builder, JavaTokenType.PACKAGE_KEYWORD))
		{
			PsiBuilder.Marker modList = builder.mark();
			myParser.getDeclarationParser().parseAnnotations(builder);
			done(modList, JavaElementType.MODIFIER_LIST);
			if(!expect(builder, JavaTokenType.PACKAGE_KEYWORD))
			{
				statement.rollbackTo();
				return;
			}
		}

		PsiBuilder.Marker ref = myParser.getReferenceParser().parseJavaCodeReference(builder, true, false, false, false);
		if(ref == null)
		{
			statement.error(JavaErrorBundle.message("expected.class.or.interface"));
			return;
		}

		semicolon(builder);

		done(statement, JavaElementType.PACKAGE_STATEMENT);
	}

	@Nonnull
	protected Pair<PsiBuilder.Marker, Boolean> parseImportList(PsiBuilder builder, Predicate<? super PsiBuilder> stopper)
	{
		PsiBuilder.Marker list = builder.mark();

		boolean isEmpty = true;
		PsiBuilder.Marker invalidElements = null;
		while(!builder.eof())
		{
			if(stopper.test(builder))
			{
				break;
			}
			else if(builder.getTokenType() == JavaTokenType.SEMICOLON)
			{
				builder.advanceLexer();
				continue;
			}

			PsiBuilder.Marker statement = parseImportStatement(builder);
			if(statement != null)
			{
				isEmpty = false;
				if(invalidElements != null)
				{
					invalidElements.errorBefore(JavaErrorBundle.message("unexpected.token"), statement);
					invalidElements = null;
				}
				continue;
			}

			if(invalidElements == null)
			{
				invalidElements = builder.mark();
			}
			builder.advanceLexer();
		}

		if(invalidElements != null)
		{
			invalidElements.rollbackTo();
		}

		if(isEmpty)
		{
			PsiBuilder.Marker precede = list.precede();
			list.rollbackTo();
			list = precede;
		}

		done(list, JavaElementType.IMPORT_LIST);
		return Pair.create(list, isEmpty);
	}

	@Nullable
	protected PsiBuilder.Marker parseImportStatement(PsiBuilder builder)
	{
		if(builder.getTokenType() != JavaTokenType.IMPORT_KEYWORD)
		{
			return null;
		}

		PsiBuilder.Marker statement = builder.mark();
		builder.advanceLexer();

		boolean isStatic = expect(builder, JavaTokenType.STATIC_KEYWORD);
		IElementType type = isStatic ? JavaElementType.IMPORT_STATIC_STATEMENT : JavaElementType.IMPORT_STATEMENT;

		boolean isOk = myParser.getReferenceParser().parseImportCodeReference(builder, isStatic);
		if(isOk)
		{
			semicolon(builder);
		}

		done(statement, type);
		return statement;
	}

	@Nonnull
	private static String error(@Nonnull AbstractBundle bundle, @Nonnull String errorMessageKey)
	{
		return bundle.getMessage(errorMessageKey);
	}
}