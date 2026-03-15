package consulo.java.manifest.lang;


import org.osmorc.manifest.lang.ManifestLanguage;
import org.osmorc.manifest.lang.ManifestLexer;
import org.osmorc.manifest.lang.ManifestParser;
import consulo.language.parser.PsiParser;
import consulo.language.lexer.Lexer;
import consulo.language.ast.TokenSet;
import consulo.language.version.LanguageVersion;
import consulo.language.version.LanguageVersionWithParsing;

/**
 * @author VISTALL
 * @since 20:07/24.06.13
 */
public abstract class ManifestLanguageVersion extends LanguageVersion implements LanguageVersionWithParsing
{
	public static final ManifestLanguageVersion Manifest = new ManifestLanguageVersion("Manifest")
	{
		@Override
		public Lexer createLexer()
		{
			return new ManifestLexer();
		}

		@Override
		public TokenSet getCommentTokens()
		{
			return TokenSet.EMPTY;
		}
	};

	public static final ManifestLanguageVersion Bnd = new ManifestLanguageVersion("Bnd")
	{
		@Override
		public Lexer createLexer()
		{
			return new BndLexer();
		}

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

	protected ManifestLanguageVersion(String id)
	{
		super(id, id,  ManifestLanguage.INSTANCE);
	}

	@Override
	public PsiParser createParser()
	{
		return new ManifestParser();
	}

	@Override
	public TokenSet getStringLiteralElements()
	{
		return TokenSet.EMPTY;
	}

	@Override
	public TokenSet getWhitespaceTokens()
	{
		return TokenSet.EMPTY;
	}
}
