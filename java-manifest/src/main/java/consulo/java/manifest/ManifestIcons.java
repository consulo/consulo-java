package consulo.java.manifest;

import consulo.annotation.DeprecationInfo;
import consulo.java.language.impl.icon.JavaPsiImplIconGroup;
import consulo.ui.image.Image;

/**
 * @author VISTALL
 * @since 12:44/27.04.13
 */
@Deprecated
@DeprecationInfo("Use JavaPsiImplIconGroup")
public interface ManifestIcons {
  Image ManifestFileType = JavaPsiImplIconGroup.filetypesManifest();
  Image BndFileType = JavaPsiImplIconGroup.filetypesBnd();
}
