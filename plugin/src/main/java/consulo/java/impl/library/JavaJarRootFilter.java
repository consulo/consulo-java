package consulo.java.impl.library;

import com.intellij.java.language.impl.JavaClassFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.ui.FileTypeBasedRootFilter;

@ExtensionImpl
public class JavaJarRootFilter extends FileTypeBasedRootFilter {
  public JavaJarRootFilter() {
    super(BinariesOrderRootType.getInstance(), true, JavaClassFileType.INSTANCE, "jar directory");
  }
}
