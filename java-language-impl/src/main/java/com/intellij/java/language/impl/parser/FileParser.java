// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.parser;

import consulo.component.util.localize.AbstractBundle;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import consulo.language.parser.PsiBuilder;
import consulo.util.lang.Pair;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

import static consulo.language.parser.PsiBuilderUtil.expect;
import static com.intellij.java.language.impl.parser.JavaParserUtil.*;

public class FileParser
{
	protected static final TokenSet IMPORT_LIST_STOPPER_SET = TokenSet.orSet(
			ElementType.MODIFIER_BIT_SET,
			TokenSet.create(JavaTokenType.CLASS_KEYWORD, JavaTokenType.INTERFACE_KEYWORD, JavaTokenType.ENUM_KEYWORD, JavaTokenType.AT));

	private final JavaParser myParser;

	public FileParser(JavaParser javaParser)
	{
		myParser = javaParser;
	}

	public void parse(PsiBuilder builder)
	{
		parseFile(builder, FileParser::stopImportListParsing, JavaErrorBundle.getInstance(), "expected.class.or.interface");
	}

	public void parseFile(PsiBuilder builder,
						  Predicate<? super PsiBuilder> importListStopper,
						  AbstractBundle bundle,
						  String errorMessageKey)
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

	protected PsiBuilder.@Nullable Marker parseInitial(PsiBuilder builder)
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

	protected PsiBuilder.@Nullable Marker parseImportStatement(PsiBuilder builder)
	{
		if(builder.getTokenType() != JavaTokenType.IMPORT_KEYWORD)
		{
			return null;
		}

		PsiBuilder.Marker statement = builder.mark();
		builder.advanceLexer();

		String identifierText = builder.getTokenText();
		IElementType type = getImportType(builder);
		boolean isStatic = type == JavaElementType.IMPORT_STATIC_STATEMENT;
		boolean isModule = type == JavaElementType.IMPORT_MODULE_STATEMENT;
		boolean isOk = isModule
				? ModuleParser.parseName(builder) != null
				: myParser.getReferenceParser().parseImportCodeReference(builder, isStatic);

		//if it is `module` we should expect either `;` or `identifier`
		if(isOk && !isModule &&
				!isStatic && builder.getTokenType() != JavaTokenType.SEMICOLON &&
				PsiKeyword.MODULE.equals(identifierText))
		{
			JavaParserUtil.error(builder, JavaErrorBundle.message("expected.identifier.or.semicolon"));
		}
		else if(isOk)
		{
			semicolon(builder);
		}

		done(statement, type);
		return statement;
	}

	private static IElementType getImportType(PsiBuilder builder)
	{
		IElementType type = builder.getTokenType();
		if(type == JavaTokenType.STATIC_KEYWORD)
		{
			builder.advanceLexer();
			return JavaElementType.IMPORT_STATIC_STATEMENT;
		}
		if(type == JavaTokenType.IDENTIFIER &&
				PsiKeyword.MODULE.equals(builder.getTokenText()) && builder.lookAhead(1) == JavaTokenType.IDENTIFIER)
		{
			builder.remapCurrentToken(JavaTokenType.MODULE_KEYWORD);
			builder.advanceLexer();
			return JavaElementType.IMPORT_MODULE_STATEMENT;
		}
		return JavaElementType.IMPORT_STATEMENT;
	}

	private static String error(AbstractBundle bundle, String errorMessageKey)
	{
		return bundle.getMessage(errorMessageKey);
	}
}