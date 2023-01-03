package consulo.java.language.impl;

import consulo.annotation.DeprecationInfo;
import consulo.java.language.impl.icon.JavaPsiImplIconGroup;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;

@Deprecated
@DeprecationInfo("Use JavaPsiImplIconGroup")
public interface JavaIcons {
  interface FileTypes {
    Image Java = JavaPsiImplIconGroup.filetypesJava();
    Image JavaClass = PlatformIconGroup.filetypesBinary();
    Image JavaOutsideSource = JavaPsiImplIconGroup.filetypesJavaoutsidesource();
  }

  interface Gutter {
    Image EventMethod = JavaPsiImplIconGroup.gutterEventmethod();
    Image ExtAnnotation = JavaPsiImplIconGroup.gutterExtannotation();
  }

  interface Nodes {
    Image JavaModule = JavaPsiImplIconGroup.nodesJavamodule();
    Image NativeLibrariesFolder = JavaPsiImplIconGroup.nodesNativelibrariesfolder();
  }

  Image Java = JavaPsiImplIconGroup.java();
}