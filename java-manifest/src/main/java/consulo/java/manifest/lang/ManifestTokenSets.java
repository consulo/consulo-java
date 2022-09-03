package consulo.java.manifest.lang;

import org.osmorc.manifest.lang.ManifestTokenType;
import consulo.language.ast.TokenSet;

/**
 * @author VISTALL
 * @since 20:16/24.06.13
 */
public interface ManifestTokenSets
{
	TokenSet COMMENTS = TokenSet.create(ManifestTokenType.LINE_COMMENT);
}
