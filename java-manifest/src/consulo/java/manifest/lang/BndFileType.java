package consulo.java.manifest.lang;

import javax.swing.Icon;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

	@NotNull
	@NonNls
	public String getName()
	{
		return "BND";
	}

	@NotNull
	public String getDescription()
	{
		return "Bnd files";
	}

	@NotNull
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
