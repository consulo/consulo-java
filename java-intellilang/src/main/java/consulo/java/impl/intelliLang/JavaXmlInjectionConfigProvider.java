package consulo.java.impl.intelliLang;

import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.psi.injection.InjectionConfigProvider;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 18/12/2022
 */
@ExtensionImpl
public class JavaXmlInjectionConfigProvider implements InjectionConfigProvider {
  @Nonnull
  @Override
  public String getConfigFilePath() {
    return "/consulo/java/impl/intelliLang/xmlInjections-java.xml";
  }
}
