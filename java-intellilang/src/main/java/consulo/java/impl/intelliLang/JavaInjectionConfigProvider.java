package consulo.java.impl.intelliLang;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.inject.advanced.InjectionConfigProvider;
import jakarta.annotation.Nonnull;

import java.io.InputStream;

/**
 * @author VISTALL
 * @since 18/12/2022
 */
@ExtensionImpl
public class JavaInjectionConfigProvider implements InjectionConfigProvider {
    @Nonnull
    @Override
    public InputStream openConfigFileStream() throws Exception {
        return getClass().getResourceAsStream("/consulo/java/impl/intelliLang/javaInjections.xml");
    }
}
