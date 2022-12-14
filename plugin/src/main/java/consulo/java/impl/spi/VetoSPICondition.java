package consulo.java.impl.spi;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.virtualFileSystem.VirtualFile;

/**
 * @author VISTALL
 * @since 10/12/2022
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface VetoSPICondition {
  boolean isVetoed(VirtualFile file);
}
