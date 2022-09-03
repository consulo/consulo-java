package consulo.java.manifest.lang;

import consulo.language.file.LanguageFileType;
import consulo.java.manifest.ManifestIcons;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import org.jetbrains.annotations.NonNls;
import org.osmorc.manifest.lang.ManifestFileType;
import org.osmorc.manifest.lang.ManifestLanguage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
	@Nonnull
	@NonNls
	public String getId()
	{
		return "BND";
	}

	@Override
	@Nonnull
	public LocalizeValue getDescription()
	{
		return LocalizeValue.localizeTODO("Bnd files");
	}

	@Override
	@Nonnull
	@NonNls
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
