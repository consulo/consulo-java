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
package com.intellij.packaging.impl.artifacts;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.ArtifactTemplate;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import consulo.java.module.extension.JavaModuleExtension;
import consulo.java.packaging.impl.elements.JarArchivePackagingElement;
import consulo.ui.image.Image;

/**
 * @author nik
 */
public class JarArtifactType extends ArtifactType
{
	public JarArtifactType()
	{
		super("jar", "Jar");
	}

	public static JarArtifactType getInstance()
	{
		return EP_NAME.findExtension(JarArtifactType.class);
	}

	@Nonnull
	@Override
	public Image getIcon()
	{
		return AllIcons.Nodes.Artifact;
	}

	@Override
	public String getDefaultPathFor(@Nonnull PackagingElementOutputKind kind)
	{
		return "/";
	}

	@Override
	public boolean isAvailableForAdd(@Nonnull ModulesProvider modulesProvider)
	{
		return ModuleUtil.hasModuleExtension(modulesProvider, JavaModuleExtension.class);
	}

	@Nonnull
	@Override
	public CompositePackagingElement<?> createRootElement(@Nonnull PackagingElementFactory factory, @Nonnull String artifactName)
	{
		return new JarArchivePackagingElement(ArtifactUtil.suggestArtifactFileName(artifactName) + ".jar");
	}

	@Nonnull
	@Override
	public List<? extends ArtifactTemplate> getNewArtifactTemplates(@Nonnull PackagingElementResolvingContext context)
	{
		return Collections.singletonList(new JarFromModulesTemplate(context));
	}
}
