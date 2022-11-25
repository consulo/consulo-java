package consulo.java.impl.library;

import com.intellij.java.language.impl.JavaClassFileType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.ui.FileTypeBasedRootFilter;

public class JavaJarRootFilter extends FileTypeBasedRootFilter {
  public JavaJarRootFilter() {
    super(BinariesOrderRootType.getInstance(), true, JavaClassFileType.INSTANCE, "jar directory");
  }
}
