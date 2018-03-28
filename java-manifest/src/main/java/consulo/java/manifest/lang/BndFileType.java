package consulo.java.manifest.lang;

import javax.annotation.Nonnull;
import javax.swing.Icon;

import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import org.osmorc.manifest.lang.ManifestFileType;
import org.osmorc.manifest.lang.ManifestLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import consulo.java.manifest.ManifestIcons;

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

	@Nonnull
	@NonNls
	public String getName()
	{
		return "BND";
	}

	@Nonnull
	public String getDescription()
	{
		return "Bnd files";
	}

	@Nonnull
	@NonNls
	public String getDefaultExtension()
	{
		return "bnd";
	}

	@Nullable
	public Icon getIcon()
	{
		return ManifestIcons.BndFileType;
	}
}
