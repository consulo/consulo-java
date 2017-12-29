package consulo.java.manifest.lang;

import org.jetbrains.annotations.NotNull;
import org.osmorc.manifest.lang.ManifestLanguage;
import org.osmorc.manifest.lang.ManifestLexer;
import org.osmorc.manifest.lang.ManifestParser;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.TokenSet;
import consulo.lang.LanguageVersion;
import consulo.lang.LanguageVersionWithParsing;

/**
 * @author VISTALL
 * @since 20:07/24.06.13
 */
public abstract class ManifestLanguageVersion extends LanguageVersion implements LanguageVersionWithParsing
{
	public static final ManifestLanguageVersion Manifest = new ManifestLanguageVersion("Manifest")
	{
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
	};

	public static final ManifestLanguageVersion Bnd = new ManifestLanguageVersion("Bnd")
	{
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

	public static final ManifestLanguageVersion[] VALUES = new ManifestLanguageVersion[]{
			Manifest,
			Bnd
	};

	protected ManifestLanguageVersion(@NotNull String id)
	{
		super(id, id,  ManifestLanguage.INSTANCE);
	}

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
}
