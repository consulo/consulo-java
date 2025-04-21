/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.java.compiler.artifact.impl.ManifestFileUtil;
import consulo.application.util.function.CommonProcessors;
import consulo.application.util.function.ThrowableComputable;
import consulo.compiler.artifact.ArtifactTemplate;
import consulo.compiler.artifact.ArtifactUtil;
import consulo.compiler.artifact.PlainArtifactType;
import consulo.compiler.artifact.element.*;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.Library;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.ModulesProvider;
import consulo.module.content.layer.OrderEnumerator;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFilePathUtil;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class JarFromModulesTemplate extends ArtifactTemplate {
    private static final Logger LOG = Logger.getInstance(JarFromModulesTemplate.class);

    private PackagingElementResolvingContext myContext;

    public JarFromModulesTemplate(PackagingElementResolvingContext context) {
        myContext = context;
    }

    @Override
    @RequiredUIAccess
    public NewArtifactConfiguration createArtifact() {
        JarArtifactFromModulesDialog dialog = new JarArtifactFromModulesDialog(myContext);
        dialog.show();
        if (!dialog.isOK()) {
            return null;
        }

        return doCreateArtifact(
            dialog.getSelectedModules(),
            dialog.getMainClassName(),
            dialog.getDirectoryForManifest(),
            dialog.isExtractLibrariesToJar(),
            dialog.isIncludeTests()
        );
    }

    @Nullable
    @RequiredUIAccess
    public NewArtifactConfiguration doCreateArtifact(
        Module[] modules,
        String mainClassName,
        String directoryForManifest,
        boolean extractLibrariesToJar,
        boolean includeTests
    ) {
        VirtualFile manifestFile = null;
        Project project = myContext.getProject();
        if (mainClassName != null && !mainClassName.isEmpty() || !extractLibrariesToJar) {
            VirtualFile directory;
            try {
                directory = project.getApplication().runWriteAction(
                    (ThrowableComputable<VirtualFile, IOException>)() -> VirtualFileUtil.createDirectoryIfMissing(directoryForManifest)
                );
            }
            catch (IOException e) {
                LOG.info(e);
                Messages.showErrorDialog(
                    project,
                    "Cannot create directory '" + directoryForManifest + "': " + e.getMessage(),
                    CommonLocalize.titleError().get()
                );
                return null;
            }
            if (directory == null) {
                return null;
            }

            manifestFile = ManifestFileUtil.createManifestFile(directory, project);
            if (manifestFile == null) {
                return null;
            }
            ManifestFileUtil.updateManifest(manifestFile, mainClassName, null, true);
        }

        String name = modules.length == 1 ? modules[0].getName() : project.getName();

        PackagingElementFactory factory = PackagingElementFactory.getInstance(myContext.getProject());
        CompositePackagingElement<?> archive = factory.createZipArchive(ArtifactUtil.suggestArtifactFileName(name) + ".jar");

        OrderEnumerator orderEnumerator = ProjectRootManager.getInstance(project).orderEntries(Arrays.asList(modules));

        Set<Library> libraries = new HashSet<>();
        if (!includeTests) {
            orderEnumerator = orderEnumerator.productionOnly();
        }
        ModulesProvider modulesProvider = myContext.getModulesProvider();
        OrderEnumerator enumerator = orderEnumerator.using(modulesProvider).withoutSdk().runtimeOnly().recursively();
        enumerator.forEachLibrary(new CommonProcessors.CollectProcessor<>(libraries));
        enumerator.forEachModule(module -> {
            if (ProductionModuleOutputElementType.getInstance().isSuitableModule(modulesProvider, module)) {
                archive.addOrFindChild(factory.createModuleOutput(module));
            }
            if (includeTests && TestModuleOutputElementType.getInstance().isSuitableModule(modulesProvider, module)) {
                archive.addOrFindChild(factory.createTestModuleOutput(module));
            }
            return true;
        });

        JarArtifactType jarArtifactType = JarArtifactType.getInstance();
        if (manifestFile != null && !manifestFile.equals(ManifestFileUtil.findManifestFile(archive, myContext, jarArtifactType))) {
            archive.addFirstChild(factory.createFileCopyWithParentDirectories(manifestFile.getPath(), ManifestFileUtil.MANIFEST_DIR_NAME));
        }

        String artifactName = name + ":jar";
        if (extractLibrariesToJar) {
            addExtractedLibrariesToJar(archive, factory, libraries);
            return new NewArtifactConfiguration(archive, artifactName, jarArtifactType);
        }
        else {
            ArtifactRootElement<?> root = factory.createArtifactRootElement();
            List<String> classpath = new ArrayList<>();
            root.addOrFindChild(archive);
            addLibraries(libraries, root, archive, classpath);
            ManifestFileUtil.updateManifest(manifestFile, mainClassName, classpath, true);
            return new NewArtifactConfiguration(root, artifactName, PlainArtifactType.getInstance());
        }
    }

    private void addLibraries(
        Set<Library> libraries,
        ArtifactRootElement<?> root,
        CompositePackagingElement<?> archive,
        List<String> classpath
    ) {
        PackagingElementFactory factory = PackagingElementFactory.getInstance(myContext.getProject());
        for (Library library : libraries) {
            if (LibraryPackagingElement.getKindForLibrary(library).containsDirectoriesWithClasses()) {
                for (VirtualFile classesRoot : library.getFiles(BinariesOrderRootType.getInstance())) {
                    if (classesRoot.isInLocalFileSystem()) {
                        archive.addOrFindChild(factory.createDirectoryCopyWithParentDirectories(classesRoot.getPath(), "/"));
                    }
                    else {
                        PackagingElement<?> child =
                            factory.createFileCopyWithParentDirectories(VirtualFilePathUtil.getLocalFile(classesRoot).getPath(), "/");
                        root.addOrFindChild(child);
                        classpath.addAll(ManifestFileUtil.getClasspathForElements(
                            Collections.singletonList(child),
                            myContext,
                            PlainArtifactType.getInstance()
                        ));
                    }
                }

            }
            else {
                List<? extends PackagingElement<?>> children = factory.createLibraryElements(library);
                classpath.addAll(ManifestFileUtil.getClasspathForElements(children, myContext, PlainArtifactType.getInstance()));
                root.addOrFindChildren(children);
            }
        }
    }

    private static void addExtractedLibrariesToJar(
        CompositePackagingElement<?> archive,
        PackagingElementFactory factory,
        Set<Library> libraries
    ) {
        for (Library library : libraries) {
            if (LibraryPackagingElement.getKindForLibrary(library).containsJarFiles()) {
                for (VirtualFile classesRoot : library.getFiles(BinariesOrderRootType.getInstance())) {
                    if (classesRoot.isInLocalFileSystem()) {
                        archive.addOrFindChild(factory.createDirectoryCopyWithParentDirectories(classesRoot.getPath(), "/"));
                    }
                    else {
                        archive.addOrFindChild(factory.createExtractedDirectory(classesRoot));
                    }
                }

            }
            else {
                archive.addOrFindChildren(factory.createLibraryElements(library));
            }
        }
    }

    @Override
    public LocalizeValue getPresentableName() {
        return LocalizeValue.localizeTODO("From modules with dependencies...");
    }
}
