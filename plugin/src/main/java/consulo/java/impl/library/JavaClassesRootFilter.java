package consulo.java.impl.library;

import com.intellij.java.language.impl.JavaClassFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.ui.FileTypeBasedRootFilter;
import consulo.localize.LocalizeValue;

@ExtensionImpl
public class JavaClassesRootFilter extends FileTypeBasedRootFilter {
  public JavaClassesRootFilter() {
    super(BinariesOrderRootType.ID, false, JavaClassFileType.INSTANCE, LocalizeValue.localizeTODO("java classes"));
  }
}
