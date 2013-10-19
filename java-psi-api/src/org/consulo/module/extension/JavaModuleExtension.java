package org.consulo.module.extension;

import org.jetbrains.annotations.NotNull;
import com.intellij.pom.java.LanguageLevel;

/**
 * @author VISTALL
 * @since 19.10.13.
 */
public interface JavaModuleExtension<T extends ModuleExtensionWithSdk<T>> extends ModuleExtensionWithSdk<T>
{
	@NotNull
	LanguageLevel getLanguageLevel();
}
