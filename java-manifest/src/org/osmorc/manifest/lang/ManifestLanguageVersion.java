package org.osmorc.manifest.lang;

import org.consulo.annotations.Immutable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageVersionWithParsing;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.TokenSet;

/**
 * @author VISTALL
 * @since 20:07/24.06.13
 */
public enum ManifestLanguageVersion implements LanguageVersionWithParsing
{
	Manifest{
		@NotNull
		@Override
		public Lexer createLexer(@Nullable Project project)
		{
			return new ManifestLexer();
		}

		@NotNull
		@Override
		public TokenSet getCommentTokens()
		{
			return TokenSet.EMPTY;
		}
	},
	Bnd {
		@NotNull
		@Override
		public Lexer createLexer(@Nullable Project project)
		{
			return new BndLexer();
		}

		@NotNull
		@Override
		public TokenSet getCommentTokens()
		{
			return ManifestTokenSets.COMMENTS;
		}
	};

	@Immutable
	public static final ManifestLanguageVersion[] VALUES = values();

	@NotNull
	@Override
	public PsiParser createParser(@Nullable Project project)
	{
		return new ManifestParser();
	}

	@NotNull
	@Override
	public TokenSet getStringLiteralElements()
	{
		return TokenSet.EMPTY;
	}

	@NotNull
	@Override
	public TokenSet getWhitespaceTokens()
	{
		return TokenSet.EMPTY;
	}

	@NotNull
	@Override
	public String getName()
	{
		return name();
	}

	@Override
	public Language getLanguage()
	{
		return ManifestLanguage.INSTANCE;
	}
}
