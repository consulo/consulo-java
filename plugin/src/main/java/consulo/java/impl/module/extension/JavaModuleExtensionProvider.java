package consulo.java.impl.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.impl.icon.JavaPsiImplIconGroup;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.ui.image.Image;


/**
 * @author VISTALL
 * @since 09/12/2022
 */
@ExtensionImpl
public class JavaModuleExtensionProvider implements ModuleExtensionProvider<JavaModuleExtensionImpl> {
  @Override
  public String getId() {
    return "java";
  }

  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("Java");
  }

  @Override
  public Image getIcon() {
    return JavaPsiImplIconGroup.java();
  }

  @Override
  public ModuleExtension<JavaModuleExtensionImpl> createImmutableExtension(ModuleRootLayer moduleRootLayer) {
    return new JavaModuleExtensionImpl(getId(), moduleRootLayer);
  }

  @Override
  public MutableModuleExtension<JavaModuleExtensionImpl> createMutableExtension(ModuleRootLayer moduleRootLayer) {
    return new JavaMutableModuleExtensionImpl(getId(), moduleRootLayer);
  }
}
