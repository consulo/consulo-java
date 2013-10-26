package org.osmorc.manifest.lang;

import com.intellij.psi.tree.TokenSet;

/**
 * @author VISTALL
 * @since 20:16/24.06.13
 */
public interface ManifestTokenSets
{
	TokenSet COMMENTS = TokenSet.create(ManifestTokenType.LINE_COMMENT);
}
