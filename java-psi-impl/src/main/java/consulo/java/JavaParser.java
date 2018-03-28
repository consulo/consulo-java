package consulo.java;

import javax.annotation.Nonnull;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import consulo.lang.LanguageVersion;

/**
 * @author VISTALL
 * @since 19:50/30.05.13
 */
public class JavaParser implements PsiParser
{
	@Nonnull
	@Override
	public ASTNode parse(@Nonnull IElementType root, @Nonnull PsiBuilder builder, @Nonnull LanguageVersion languageVersion)
	{
		final PsiBuilder.Marker marker = builder.mark();
		com.intellij.lang.java.parser.JavaParser.INSTANCE.getFileParser().parse(builder);
		marker.done(root);
		return builder.getTreeBuilt();
	}
}
