package consulo.java.impl.library;

import com.intellij.java.language.impl.JavaClassFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.ui.FileTypeBasedRootFilter;

@ExtensionImpl
public class JavaClassesRootFilter extends FileTypeBasedRootFilter {
  public JavaClassesRootFilter() {
    super(BinariesOrderRootType.getInstance(), false, JavaClassFileType.INSTANCE, "java classes");
  }
}
