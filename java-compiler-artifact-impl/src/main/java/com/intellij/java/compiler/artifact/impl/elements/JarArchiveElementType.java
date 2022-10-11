/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.compiler.artifact.impl.elements;

import consulo.application.AllIcons;
import consulo.compiler.CompilerBundle;
import consulo.compiler.artifact.Artifact;
import consulo.compiler.artifact.ArtifactUtil;
import consulo.compiler.artifact.element.*;
import consulo.compiler.artifact.ui.ArtifactEditorContext;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.ui.image.Image;
import consulo.util.io.FileUtil;
import consulo.util.io.PathUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author nik
 */
public class JarArchiveElementType extends CompositePackagingElementType<JarArchivePackagingElement> {
  @Nonnull
  public static JarArchiveElementType getInstance() {
    return getInstance(JarArchiveElementType.class);
  }

  public JarArchiveElementType() {
    super("jar-archive", CompilerBundle.message("element.type.name.jar.archive"));
  }

  @Override
  public boolean isAvailableForAdd(@Nonnull ArtifactEditorContext context, @Nonnull Artifact artifact) {
    return ModuleUtilCore.hasModuleExtension(context.getModulesProvider(), JavaModuleExtension.class);
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return AllIcons.Nodes.PpJar;
  }

  @Nonnull
  @Override
  public JarArchivePackagingElement createEmpty(@Nonnull Project project) {
    return new JarArchivePackagingElement();
  }

	/*@Override
  public PackagingElementPropertiesPanel createElementPropertiesPanel(@NotNull JarArchivePackagingElement element,
			@NotNull ArtifactEditorContext context)
	{
		return new JarArchiveElementPropertiesPanel(element, context);
	}    */

  @Override
  public CompositePackagingElement<?> createComposite(CompositePackagingElement<?> parent, @Nullable String baseName,
                                                      @Nonnull ArtifactEditorContext context) {
    final String initialValue = ArtifactUtil.suggestFileName(parent, baseName != null ? baseName : "archive", ".jar");
    String path = Messages.showInputDialog(context.getProject(), "Enter archive name: ", "New Archive", null, initialValue,
        new FilePathValidator());
    if (path == null) {
      return null;
    }
    path = FileUtil.toSystemIndependentName(path);
    final String parentPath = PathUtil.getParentPath(path);
    final String fileName = PathUtil.getFileName(path);
    final PackagingElement<?> element = new JarArchivePackagingElement(fileName);
    return (CompositePackagingElement<?>) PackagingElementFactory.getInstance(context.getProject()).createParentDirectories(parentPath, element);
  }
}
