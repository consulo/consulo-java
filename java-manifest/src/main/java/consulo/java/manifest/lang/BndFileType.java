package consulo.java.manifest.lang;

import consulo.language.file.LanguageFileType;
import consulo.java.manifest.ManifestIcons;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;
import org.osmorc.manifest.lang.ManifestFileType;
import org.osmorc.manifest.lang.ManifestLanguage;


/**
 * @author VISTALL
 * @since 20:11/24.06.13
 */
public class BndFileType extends ManifestFileType
{
	public static LanguageFileType INSTANCE = new BndFileType();

	public BndFileType()
	{
		super(ManifestLanguage.INSTANCE);
	}

	@Override
	public String getId()
	{
		return "BND";
	}

	@Override
	public LocalizeValue getDescription()
	{
		return LocalizeValue.localizeTODO("Bnd files");
	}

	@Override
	public String getDefaultExtension()
	{
		return "bnd";
	}

	@Override
	@Nullable
	public Image getIcon()
	{
		return ManifestIcons.BndFileType;
	}
}
