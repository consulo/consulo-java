package consulo.java.impl.library;

import com.intellij.java.language.impl.JavaClassFileType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.ui.FileTypeBasedRootFilter;

public class JavaClassesRootFilter extends FileTypeBasedRootFilter {
  public JavaClassesRootFilter() {
    super(BinariesOrderRootType.getInstance(), false, JavaClassFileType.INSTANCE, "java classes");
  }
}
