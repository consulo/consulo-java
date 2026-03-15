package consulo.java.manifest.lang;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.highlight.LanguageVersionableSyntaxHighlighterFactory;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.version.LanguageVersion;
import org.osmorc.manifest.lang.ManifestLanguage;
import org.osmorc.manifest.lang.ManifestSyntaxHighlighter;


/**
 * @author VISTALL
 * @since 14:55/12.05.13
 */
@ExtensionImpl
public class ManifestSyntaxHighlighterFactory extends LanguageVersionableSyntaxHighlighterFactory {
  @Override
  public Language getLanguage() {
    return ManifestLanguage.INSTANCE;
  }

  @Override
  public SyntaxHighlighter getSyntaxHighlighter(LanguageVersion languageVersion) {
    return new ManifestSyntaxHighlighter((ManifestLanguageVersion) languageVersion);
  }
}
