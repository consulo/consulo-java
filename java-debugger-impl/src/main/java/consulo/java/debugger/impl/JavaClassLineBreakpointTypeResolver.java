package consulo.java.debugger.impl;

import com.intellij.java.language.impl.JavaClassFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 10/12/2022
 */
@ExtensionImpl
public class JavaClassLineBreakpointTypeResolver extends BaseJavaLineBreakpointTypeResolver {
  @Nonnull
  @Override
  public FileType getFileType() {
    return JavaClassFileType.INSTANCE;
  }
}
