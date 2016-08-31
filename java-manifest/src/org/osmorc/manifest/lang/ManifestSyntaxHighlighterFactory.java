package org.osmorc.manifest.lang;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import consulo.fileTypes.LanguageVersionableSyntaxHighlighterFactory;
import consulo.lang.LanguageVersion;

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

	@NotNull
	@Override
	public SyntaxHighlighter getSyntaxHighlighter(@NotNull LanguageVersion languageVersion)
	{
		return new ManifestSyntaxHighlighter((ManifestLanguageVersion) languageVersion);
	}
}
