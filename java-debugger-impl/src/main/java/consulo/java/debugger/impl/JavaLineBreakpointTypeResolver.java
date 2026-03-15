package consulo.java.debugger.impl;

import com.intellij.java.language.impl.JavaFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.virtualFileSystem.fileType.FileType;

/**
 * @author VISTALL
 * @since 5/7/2016
 */
@ExtensionImpl
public class JavaLineBreakpointTypeResolver extends BaseJavaLineBreakpointTypeResolver {
  @Override
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }
}
