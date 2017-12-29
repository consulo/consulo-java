package consulo.java.manifest;

import javax.swing.Icon;

import com.intellij.openapi.util.IconLoader;

/**
 * @author VISTALL
 * @since 12:44/27.04.13
 */
public interface ManifestIcons {
  Icon ManifestFileType = IconLoader.findIcon("/icons/manifest.png");
  Icon BndFileType = ManifestFileType; //TODO [VISTALL] unique icon
}
