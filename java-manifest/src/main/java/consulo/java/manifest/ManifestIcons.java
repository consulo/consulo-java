package consulo.java.manifest;

import com.intellij.openapi.util.IconLoader;
import consulo.ui.image.Image;

/**
 * @author VISTALL
 * @since 12:44/27.04.13
 */
public interface ManifestIcons
{
	Image ManifestFileType = IconLoader.findIcon("/icons/manifest.png");
	Image BndFileType = ManifestFileType; //TODO [VISTALL] unique icon
}
