package consulo.java.debugger.impl;

import com.intellij.java.language.impl.JavaFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 5/7/2016
 */
@ExtensionImpl
public class JavaLineBreakpointTypeResolver extends BaseJavaLineBreakpointTypeResolver {
  @Nonnull
  @Override
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }
}
