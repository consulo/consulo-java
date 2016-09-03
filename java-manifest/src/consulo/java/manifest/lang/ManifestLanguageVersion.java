package consulo.java.manifest.lang;

import org.jetbrains.annotations.NotNull;
import org.osmorc.manifest.lang.ManifestLanguage;
import org.osmorc.manifest.lang.ManifestLexer;
import org.osmorc.manifest.lang.ManifestParser;
import com.intellij.lang.Language;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.TokenSet;
import consulo.lang.LanguageVersionWithParsing;

/**
 * @author VISTALL
 * @since 20:07/24.06.13
 */
public enum ManifestLanguageVersion implements LanguageVersionWithParsing
{
	Manifest{
		@NotNull
		@Override
		public Lexer createLexer()
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
		public Lexer createLexer()
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

	public static final ManifestLanguageVersion[] VALUES = values();

	@NotNull
	@Override
	public PsiParser createParser()
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
