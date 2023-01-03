package com.intellij.java.impl.ide.actions;

import consulo.annotation.component.ExtensionImpl;
import consulo.fileTemplate.FileTemplateContributor;
import consulo.fileTemplate.FileTemplateRegistrator;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 14/12/2022
 */
@ExtensionImpl
public class JavaFileTemplateContributor implements FileTemplateContributor {
  @Override
  public void register(@Nonnull FileTemplateRegistrator registrator) {
    registrator.registerInternalTemplate("Java Class");
    registrator.registerInternalTemplate("Java Interface");
    registrator.registerInternalTemplate("Java Enum");
    registrator.registerInternalTemplate("Java Record");
    registrator.registerInternalTemplate("Java AnnotationType", "@interface");
    registrator.registerInternalTemplate("package-info");
    registrator.registerInternalTemplate("module-info");
  }
}
