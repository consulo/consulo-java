package consulo.java.manifest.lang;

import javax.annotation.Nonnull;

import consulo.language.editor.highlight.LanguageVersionableSyntaxHighlighterFactory;
import org.osmorc.manifest.lang.ManifestLanguage;
import org.osmorc.manifest.lang.ManifestSyntaxHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.version.LanguageVersion;

/**
 * @author VISTALL
 * @since 14:55/12.05.13
 */
public class ManifestSyntaxHighlighterFactory extends LanguageVersionableSyntaxHighlighterFactory
{
	public ManifestSyntaxHighlighterFactory()
	{
		super(ManifestLanguage.INSTANCE);
	}

	@Nonnull
	@Override
	public SyntaxHighlighter getSyntaxHighlighter(@Nonnull LanguageVersion languageVersion)
	{
		return new ManifestSyntaxHighlighter((ManifestLanguageVersion) languageVersion);
	}
}
