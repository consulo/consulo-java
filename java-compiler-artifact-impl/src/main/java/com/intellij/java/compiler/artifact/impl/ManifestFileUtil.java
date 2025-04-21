/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.java.compiler.artifact.impl;

import com.intellij.java.compiler.artifact.impl.ui.ManifestFileConfiguration;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import com.intellij.java.language.util.ClassFilter;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.application.Application;
import consulo.application.WriteAction;
import consulo.application.util.function.Computable;
import consulo.compiler.artifact.ArtifactType;
import consulo.compiler.artifact.ArtifactUtil;
import consulo.compiler.artifact.PackagingElementPath;
import consulo.compiler.artifact.PackagingElementProcessor;
import consulo.compiler.artifact.element.*;
import consulo.compiler.artifact.ui.ArtifactEditorContext;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.IdeaFileChooser;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.OrderEnumerator;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.util.io.FileUtil;
import consulo.util.io.PathUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * @author nik
 */
public class ManifestFileUtil {
    private static final Logger LOGGER = Logger.getInstance(ManifestFileUtil.class);

    public static final String MANIFEST_PATH = JarFile.MANIFEST_NAME;
    public static final String MANIFEST_FILE_NAME = PathUtil.getFileName(MANIFEST_PATH);
    public static final String MANIFEST_DIR_NAME = PathUtil.getParentPath(MANIFEST_PATH);

    private ManifestFileUtil() {
    }

    @Nullable
    public static VirtualFile findManifestFile(
        @Nonnull CompositePackagingElement<?> root,
        PackagingElementResolvingContext context,
        ArtifactType artifactType
    ) {
        return ArtifactUtil.findSourceFileByOutputPath(root, MANIFEST_PATH, context, artifactType);
    }

    @Nullable
    public static VirtualFile suggestManifestFileDirectory(
        @Nonnull CompositePackagingElement<?> root,
        PackagingElementResolvingContext context,
        ArtifactType artifactType
    ) {
        final VirtualFile metaInfDir = ArtifactUtil.findSourceFileByOutputPath(root, MANIFEST_DIR_NAME, context, artifactType);
        if (metaInfDir != null) {
            return metaInfDir;
        }

        final Ref<VirtualFile> sourceDir = Ref.create(null);
        final Ref<VirtualFile> sourceFile = Ref.create(null);
        ArtifactUtil.processElementsWithSubstitutions(
            root.getChildren(),
            context,
            artifactType,
            PackagingElementPath.EMPTY,
            new PackagingElementProcessor<>() {
                @Override
                public boolean process(@Nonnull PackagingElement<?> element, @Nonnull PackagingElementPath path) {
                    if (element instanceof FileCopyPackagingElement fileCopyPackagingElement) {
                        final VirtualFile file = fileCopyPackagingElement.findFile();
                        if (file != null) {
                            sourceFile.set(file);
                        }
                    }
                    else if (element instanceof DirectoryCopyPackagingElement directoryCopyPackagingElement) {
                        final VirtualFile file = directoryCopyPackagingElement.findFile();
                        if (file != null) {
                            sourceDir.set(file);
                            return false;
                        }
                    }
                    return true;
                }
            }
        );

        if (!sourceDir.isNull()) {
            return sourceDir.get();
        }


        final Project project = context.getProject();
        return suggestBaseDir(project, sourceFile.get());
    }

    @Nullable
    public static VirtualFile suggestManifestFileDirectory(@Nonnull Project project, @Nullable Module module) {
        OrderEnumerator enumerator = module != null ? OrderEnumerator.orderEntries(module) : OrderEnumerator.orderEntries(project);
        final VirtualFile[] files = enumerator.withoutDepModules().withoutLibraries().withoutSdk().productionOnly().sources().getRoots();
        if (files.length > 0) {
            return files[0];
        }
        return suggestBaseDir(project, null);
    }


    @Nullable
    private static VirtualFile suggestBaseDir(@Nonnull Project project, final @Nullable VirtualFile file) {
        final VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentRoots();
        if (file == null && contentRoots.length > 0) {
            return contentRoots[0];
        }

        if (file != null) {
            for (VirtualFile contentRoot : contentRoots) {
                if (VirtualFileUtil.isAncestor(contentRoot, file, false)) {
                    return contentRoot;
                }
            }
        }

        return project.getBaseDir();
    }

    public static Manifest readManifest(@Nonnull VirtualFile manifestFile) {
        try (InputStream inputStream = manifestFile.getInputStream()) {
            return new Manifest(inputStream);
        }
        catch (IOException ignored) {
            return new Manifest();
        }
    }

    public static void updateManifest(
        @Nonnull VirtualFile file,
        final @Nullable String mainClass,
        final @Nullable List<String> classpath,
        final boolean replaceValues
    ) {
        final Manifest manifest = readManifest(file);
        final Attributes mainAttributes = manifest.getMainAttributes();

        if (mainClass != null) {
            mainAttributes.put(Attributes.Name.MAIN_CLASS, mainClass);
        }
        else if (replaceValues) {
            mainAttributes.remove(Attributes.Name.MAIN_CLASS);
        }

        if (classpath != null && !classpath.isEmpty()) {
            List<String> updatedClasspath;
            if (replaceValues) {
                updatedClasspath = classpath;
            }
            else {
                updatedClasspath = new ArrayList<>();
                final String oldClasspath = (String)mainAttributes.get(Attributes.Name.CLASS_PATH);
                if (!StringUtil.isEmpty(oldClasspath)) {
                    updatedClasspath.addAll(StringUtil.split(oldClasspath, " "));
                }
                for (String path : classpath) {
                    if (!updatedClasspath.contains(path)) {
                        updatedClasspath.add(path);
                    }
                }
            }
            mainAttributes.put(Attributes.Name.CLASS_PATH, StringUtil.join(updatedClasspath, " "));
        }
        else if (replaceValues) {
            mainAttributes.remove(Attributes.Name.CLASS_PATH);
        }

        ManifestBuilder.setVersionAttribute(mainAttributes);

        try (OutputStream outputStream = file.getOutputStream(ManifestFileUtil.class)) {
            manifest.write(outputStream);
        }
        catch (IOException e) {
            LOGGER.info(e);
        }
    }

    @Nonnull
    public static ManifestFileConfiguration createManifestFileConfiguration(@Nonnull VirtualFile manifestFile) {
        final String path = manifestFile.getPath();
        Manifest manifest = readManifest(manifestFile);
        String mainClass = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        final String classpathText = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        final List<String> classpath = new ArrayList<>();
        if (classpathText != null) {
            classpath.addAll(StringUtil.split(classpathText, " "));
        }
        return new ManifestFileConfiguration(path, classpath, mainClass, manifestFile.isWritable());
    }

    public static List<String> getClasspathForElements(
        List<? extends PackagingElement<?>> elements,
        PackagingElementResolvingContext context,
        final ArtifactType artifactType
    ) {
        final List<String> classpath = new ArrayList<>();
        final PackagingElementProcessor<PackagingElement<?>> processor = new PackagingElementProcessor<>() {
            @Override
            public boolean process(@Nonnull PackagingElement<?> element, @Nonnull PackagingElementPath path) {
                if (element instanceof FileCopyPackagingElement fileCopyPackagingElement) {
                    final String fileName = fileCopyPackagingElement.getOutputFileName();
                    classpath.add(ArtifactUtil.appendToPath(path.getPathString(), fileName));
                }
                else if (element instanceof DirectoryCopyPackagingElement) {
                    classpath.add(path.getPathString());
                }
                else if (element instanceof ArchivePackagingElement archivePackagingElement) {
                    final String archiveName = archivePackagingElement.getName();
                    classpath.add(ArtifactUtil.appendToPath(path.getPathString(), archiveName));
                }
                return true;
            }
        };
        for (PackagingElement<?> element : elements) {
            ArtifactUtil.processPackagingElements(element, null, processor, context, true, artifactType);
        }
        return classpath;
    }

    @Nullable
    public static VirtualFile showDialogAndCreateManifest(final ArtifactEditorContext context, final CompositePackagingElement<?> element) {
        FileChooserDescriptor descriptor = createDescriptorForManifestDirectory();
        final VirtualFile directory = suggestManifestFileDirectory(element, context, context.getArtifactType());
        final VirtualFile file = IdeaFileChooser.chooseFile(descriptor, context.getProject(), directory);
        if (file == null) {
            return null;
        }

        return createManifestFile(file, context.getProject());
    }

    @Nullable
    public static VirtualFile createManifestFile(final @Nonnull VirtualFile directory, final @Nonnull Project project) {
        project.getApplication().assertIsDispatchThread();
        final Ref<IOException> exc = Ref.create(null);
        final VirtualFile file = WriteAction.compute(() -> {
            VirtualFile dir = directory;
            try {
                if (!dir.getName().equals(MANIFEST_DIR_NAME)) {
                    dir = VirtualFileUtil.createDirectoryIfMissing(dir, MANIFEST_DIR_NAME);
                }
                final VirtualFile f = dir.createChildData(ManifestFileUtil.class, MANIFEST_FILE_NAME);
                try (OutputStream output = f.getOutputStream(ManifestFileUtil.class)) {
                    final Manifest manifest = new Manifest();
                    ManifestBuilder.setVersionAttribute(manifest.getMainAttributes());
                    manifest.write(output);
                }
                return f;
            }
            catch (IOException e) {
                exc.set(e);
                return null;
            }
        });

        final IOException exception = exc.get();
        if (exception != null) {
            LOGGER.info(exception);
            Messages.showErrorDialog(project, exception.getMessage(), CommonLocalize.titleError().get());
            return null;
        }
        return file;
    }

    public static FileChooserDescriptor createDescriptorForManifestDirectory() {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setTitle("Select Directory for META-INF/MANIFEST.MF file");
        return descriptor;
    }

    public static void addManifestFileToLayout(
        final @Nonnull String path,
        final @Nonnull ArtifactEditorContext context,
        final @Nonnull CompositePackagingElement<?> element
    ) {
        context.editLayout(context.getArtifact(), () -> {
            final VirtualFile file = findManifestFile(element, context, context.getArtifactType());
            if (file == null || !FileUtil.pathsEqual(file.getPath(), path)) {
                PackagingElementFactory.getInstance(context.getProject()).addFileCopy(element, MANIFEST_DIR_NAME, path, MANIFEST_FILE_NAME);
            }
        });
    }

    @Nullable
    public static PsiClass selectMainClass(Project project, final @Nullable String initialClassName) {
        final TreeClassChooserFactory chooserFactory = TreeClassChooserFactory.getInstance(project);
        final GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
        final PsiClass aClass =
            initialClassName != null ? JavaPsiFacade.getInstance(project).findClass(initialClassName, searchScope) : null;
        final TreeClassChooser chooser =
            chooserFactory.createWithInnerClassesScopeChooser("Select Main Class", searchScope, new MainClassFilter(), aClass);
        chooser.showDialog();
        return chooser.getSelected();
    }

    public static void setupMainClassField(final Project project, final TextFieldWithBrowseButton field) {
        field.addActionListener(e -> {
            final PsiClass selected = selectMainClass(project, field.getText());
            if (selected != null) {
                field.setText(selected.getQualifiedName());
            }
        });
    }

    private static class MainClassFilter implements ClassFilter {
        @Override
        public boolean isAccepted(final PsiClass aClass) {
            return Application.get()
                .runReadAction((Computable<Boolean>)() -> PsiMethodUtil.MAIN_CLASS.test(aClass) && PsiMethodUtil.hasMainMethod(aClass));
        }
    }
}
