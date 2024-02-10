package consulo.java.properties.impl.psi;

import com.intellij.java.language.impl.psi.CommonReferenceProviderTypes;
import com.intellij.java.language.impl.psi.JavaClassPsiReferenceProvider;
import com.intellij.java.language.patterns.PsiJavaPatterns;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.*;
import consulo.language.util.ProcessingContext;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 25/04/2023
 */
@ExtensionImpl
public class PropertiesJavaReferenceContributor extends PsiReferenceContributor
{
	@Override
	public void registerReferenceProviders(@Nonnull PsiReferenceRegistrar registrar)
	{
		registrar.registerReferenceProvider(PsiJavaPatterns.psiElement(PropertyValueImpl.class), new PsiReferenceProvider()
		{
			@Nonnull
			@Override
			public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull ProcessingContext context)
			{
				String text = element.getText();
				String[] words = text.split("\\s");
				if(words.length != 1)
					return PsiReference.EMPTY_ARRAY;
				JavaClassPsiReferenceProvider provider = CommonReferenceProviderTypes.getInstance().getSoftClassReferenceProvider();
				return provider.getReferencesByString(words[0], element, 0);
			}
		});
	}

	@Nonnull
	@Override
	public Language getLanguage()
	{
		return PropertiesLanguage.INSTANCE;
	}
}
