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
package com.intellij.java.compiler.artifact.impl.artifacts;

import com.intellij.java.compiler.artifact.impl.elements.JarArchivePackagingElement;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.compiler.artifact.ArtifactTemplate;
import consulo.compiler.artifact.ArtifactType;
import consulo.compiler.artifact.ArtifactUtil;
import consulo.compiler.artifact.element.CompositePackagingElement;
import consulo.compiler.artifact.element.PackagingElementFactory;
import consulo.compiler.artifact.element.PackagingElementOutputKind;
import consulo.compiler.artifact.element.PackagingElementResolvingContext;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.module.content.layer.ModulesProvider;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
@ExtensionImpl(order = "after zip-artifact")
public class JarArtifactType extends ArtifactType {
    public JarArtifactType() {
        super("jar", "Jar");
    }

    public static JarArtifactType getInstance() {
        return EP_NAME.findExtension(JarArtifactType.class);
    }

    @Nonnull
    @Override
    public Image getIcon() {
        return AllIcons.Nodes.Artifact;
    }

    @Override
    public String getDefaultPathFor(@Nonnull PackagingElementOutputKind kind) {
        return "/";
    }

    @Override
    public boolean isAvailableForAdd(@Nonnull ModulesProvider modulesProvider) {
        return ModuleUtilCore.hasModuleExtension(modulesProvider, JavaModuleExtension.class);
    }

    @Nonnull
    @Override
    public CompositePackagingElement<?> createRootElement(@Nonnull PackagingElementFactory factory, @Nonnull String artifactName) {
        return new JarArchivePackagingElement(ArtifactUtil.suggestArtifactFileName(artifactName) + ".jar");
    }

    @Nonnull
    @Override
    public List<? extends ArtifactTemplate> getNewArtifactTemplates(@Nonnull PackagingElementResolvingContext context) {
        return Collections.singletonList(new JarFromModulesTemplate(context));
    }
}
