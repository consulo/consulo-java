package consulo.java.impl.library;

import com.intellij.java.language.impl.JavaFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.library.ui.FileTypeBasedRootFilter;

/**
 * @author VISTALL
 * @since 01.02.14
 */
@ExtensionImpl
public class JavaSourceDirectoryRootFilter extends FileTypeBasedRootFilter {
  public JavaSourceDirectoryRootFilter() {
    super(SourcesOrderRootType.getInstance(), true, JavaFileType.INSTANCE, "java source archive directory");
  }
}
