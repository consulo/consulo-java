package consulo.java.impl.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.impl.icon.JavaPsiImplIconGroup;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 09/12/2022
 */
@ExtensionImpl
public class JavaModuleExtensionProvider implements ModuleExtensionProvider<JavaModuleExtensionImpl> {
  @Nonnull
  @Override
  public String getId() {
    return "java";
  }

  @Nonnull
  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("Java");
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return JavaPsiImplIconGroup.java();
  }

  @Nonnull
  @Override
  public ModuleExtension<JavaModuleExtensionImpl> createImmutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new JavaModuleExtensionImpl(getId(), moduleRootLayer);
  }

  @Nonnull
  @Override
  public MutableModuleExtension<JavaModuleExtensionImpl> createMutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new JavaMutableModuleExtensionImpl(getId(), moduleRootLayer);
  }
}
