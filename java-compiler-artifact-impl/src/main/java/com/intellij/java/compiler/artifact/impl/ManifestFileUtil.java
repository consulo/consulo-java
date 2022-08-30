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

import com.intellij.CommonBundle;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.deployment.DeploymentUtil;
import consulo.logging.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PackagingElementPath;
import com.intellij.packaging.impl.artifacts.PackagingElementProcessor;
import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.impl.elements.DirectoryCopyPackagingElement;
import com.intellij.packaging.impl.elements.FileCopyPackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.java.compiler.artifact.impl.ui.ManifestFileConfiguration;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.util.PathUtil;

import javax.annotation.Nonnull;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
public class ManifestFileUtil
{
	private static final Logger LOGGER = Logger.getInstance(ManifestFileUtil.class);

	public static final String MANIFEST_PATH = JarFile.MANIFEST_NAME;
	public static final String MANIFEST_FILE_NAME = PathUtil.getFileName(MANIFEST_PATH);
	public static final String MANIFEST_DIR_NAME = PathUtil.getParentPath(MANIFEST_PATH);

	private ManifestFileUtil()
	{
	}

	@javax.annotation.Nullable
	public static VirtualFile findManifestFile(@Nonnull CompositePackagingElement<?> root, PackagingElementResolvingContext context, ArtifactType artifactType)
	{
		return ArtifactUtil.findSourceFileByOutputPath(root, MANIFEST_PATH, context, artifactType);
	}

	@javax.annotation.Nullable
	public static VirtualFile suggestManifestFileDirectory(@Nonnull CompositePackagingElement<?> root, PackagingElementResolvingContext context, ArtifactType artifactType)
	{
		final VirtualFile metaInfDir = ArtifactUtil.findSourceFileByOutputPath(root, MANIFEST_DIR_NAME, context, artifactType);
		if(metaInfDir != null)
		{
			return metaInfDir;
		}

		final Ref<VirtualFile> sourceDir = Ref.create(null);
		final Ref<VirtualFile> sourceFile = Ref.create(null);
		ArtifactUtil.processElementsWithSubstitutions(root.getChildren(), context, artifactType, PackagingElementPath.EMPTY, new PackagingElementProcessor<PackagingElement<?>>()
		{
			@Override
			public boolean process(@Nonnull PackagingElement<?> element, @Nonnull PackagingElementPath path)
			{
				if(element instanceof FileCopyPackagingElement)
				{
					final VirtualFile file = ((FileCopyPackagingElement) element).findFile();
					if(file != null)
					{
						sourceFile.set(file);
					}
				}
				else if(element instanceof DirectoryCopyPackagingElement)
				{
					final VirtualFile file = ((DirectoryCopyPackagingElement) element).findFile();
					if(file != null)
					{
						sourceDir.set(file);
						return false;
					}
				}
				return true;
			}
		});

		if(!sourceDir.isNull())
		{
			return sourceDir.get();
		}


		final Project project = context.getProject();
		return suggestBaseDir(project, sourceFile.get());
	}

	@javax.annotation.Nullable
	public static VirtualFile suggestManifestFileDirectory(@Nonnull Project project, @javax.annotation.Nullable Module module)
	{
		OrderEnumerator enumerator = module != null ? OrderEnumerator.orderEntries(module) : OrderEnumerator.orderEntries(project);
		final VirtualFile[] files = enumerator.withoutDepModules().withoutLibraries().withoutSdk().productionOnly().sources().getRoots();
		if(files.length > 0)
		{
			return files[0];
		}
		return suggestBaseDir(project, null);
	}


	@javax.annotation.Nullable
	private static VirtualFile suggestBaseDir(@Nonnull Project project, final @javax.annotation.Nullable VirtualFile file)
	{
		final VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentRoots();
		if(file == null && contentRoots.length > 0)
		{
			return contentRoots[0];
		}

		if(file != null)
		{
			for(VirtualFile contentRoot : contentRoots)
			{
				if(VfsUtil.isAncestor(contentRoot, file, false))
				{
					return contentRoot;
				}
			}
		}

		return project.getBaseDir();
	}

	public static Manifest readManifest(@Nonnull VirtualFile manifestFile)
	{
		try
		{
			final InputStream inputStream = manifestFile.getInputStream();
			final Manifest manifest;
			try
			{
				manifest = new Manifest(inputStream);
			}
			finally
			{
				inputStream.close();
			}
			return manifest;
		}
		catch(IOException ignored)
		{
			return new Manifest();
		}
	}

	public static void updateManifest(@Nonnull VirtualFile file,
									  final @javax.annotation.Nullable String mainClass,
									  final @javax.annotation.Nullable List<String> classpath,
									  final boolean replaceValues)
	{
		final Manifest manifest = readManifest(file);
		final Attributes mainAttributes = manifest.getMainAttributes();

		if(mainClass != null)
		{
			mainAttributes.put(Attributes.Name.MAIN_CLASS, mainClass);
		}
		else if(replaceValues)
		{
			mainAttributes.remove(Attributes.Name.MAIN_CLASS);
		}

		if(classpath != null && !classpath.isEmpty())
		{
			List<String> updatedClasspath;
			if(replaceValues)
			{
				updatedClasspath = classpath;
			}
			else
			{
				updatedClasspath = new ArrayList<String>();
				final String oldClasspath = (String) mainAttributes.get(Attributes.Name.CLASS_PATH);
				if(!StringUtil.isEmpty(oldClasspath))
				{
					updatedClasspath.addAll(StringUtil.split(oldClasspath, " "));
				}
				for(String path : classpath)
				{
					if(!updatedClasspath.contains(path))
					{
						updatedClasspath.add(path);
					}
				}
			}
			mainAttributes.put(Attributes.Name.CLASS_PATH, StringUtil.join(updatedClasspath, " "));
		}
		else if(replaceValues)
		{
			mainAttributes.remove(Attributes.Name.CLASS_PATH);
		}

		ManifestBuilder.setVersionAttribute(mainAttributes);

		try
		{
			final OutputStream outputStream = file.getOutputStream(ManifestFileUtil.class);
			try
			{
				manifest.write(outputStream);
			}
			finally
			{
				outputStream.close();
			}
		}
		catch(IOException e)
		{
			LOGGER.info(e);
		}
	}

	@Nonnull
	public static ManifestFileConfiguration createManifestFileConfiguration(@Nonnull VirtualFile manifestFile)
	{
		final String path = manifestFile.getPath();
		Manifest manifest = readManifest(manifestFile);
		String mainClass = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
		final String classpathText = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
		final List<String> classpath = new ArrayList<String>();
		if(classpathText != null)
		{
			classpath.addAll(StringUtil.split(classpathText, " "));
		}
		return new ManifestFileConfiguration(path, classpath, mainClass, manifestFile.isWritable());
	}

	public static List<String> getClasspathForElements(List<? extends PackagingElement<?>> elements, PackagingElementResolvingContext context, final ArtifactType artifactType)
	{
		final List<String> classpath = new ArrayList<String>();
		final PackagingElementProcessor<PackagingElement<?>> processor = new PackagingElementProcessor<PackagingElement<?>>()
		{
			@Override
			public boolean process(@Nonnull PackagingElement<?> element, @Nonnull PackagingElementPath path)
			{
				if(element instanceof FileCopyPackagingElement)
				{
					final String fileName = ((FileCopyPackagingElement) element).getOutputFileName();
					classpath.add(DeploymentUtil.appendToPath(path.getPathString(), fileName));
				}
				else if(element instanceof DirectoryCopyPackagingElement)
				{
					classpath.add(path.getPathString());
				}
				else if(element instanceof ArchivePackagingElement)
				{
					final String archiveName = ((ArchivePackagingElement) element).getName();
					classpath.add(DeploymentUtil.appendToPath(path.getPathString(), archiveName));
				}
				return true;
			}
		};
		for(PackagingElement<?> element : elements)
		{
			ArtifactUtil.processPackagingElements(element, null, processor, context, true, artifactType);
		}
		return classpath;
	}

	@javax.annotation.Nullable
	public static VirtualFile showDialogAndCreateManifest(final ArtifactEditorContext context, final CompositePackagingElement<?> element)
	{
		FileChooserDescriptor descriptor = createDescriptorForManifestDirectory();
		final VirtualFile directory = suggestManifestFileDirectory(element, context, context.getArtifactType());
		final VirtualFile file = FileChooser.chooseFile(descriptor, context.getProject(), directory);
		if(file == null)
		{
			return null;
		}

		return createManifestFile(file, context.getProject());
	}

	@javax.annotation.Nullable
	public static VirtualFile createManifestFile(final @Nonnull VirtualFile directory, final @Nonnull Project project)
	{
		ApplicationManager.getApplication().assertIsDispatchThread();
		final Ref<IOException> exc = Ref.create(null);
		final VirtualFile file = WriteAction.compute(() -> {
			VirtualFile dir = directory;
			try
			{
				if(!dir.getName().equals(MANIFEST_DIR_NAME))
				{
					dir = VfsUtil.createDirectoryIfMissing(dir, MANIFEST_DIR_NAME);
				}
				final VirtualFile f = dir.createChildData(ManifestFileUtil.class, MANIFEST_FILE_NAME);
				try (OutputStream output = f.getOutputStream(ManifestFileUtil.class))
				{
					final Manifest manifest = new Manifest();
					ManifestBuilder.setVersionAttribute(manifest.getMainAttributes());
					manifest.write(output);
				}
				return f;
			}
			catch(IOException e)
			{
				exc.set(e);
				return null;
			}
		});

		final IOException exception = exc.get();
		if(exception != null)
		{
			LOGGER.info(exception);
			Messages.showErrorDialog(project, exception.getMessage(), CommonBundle.getErrorTitle());
			return null;
		}
		return file;
	}

	public static FileChooserDescriptor createDescriptorForManifestDirectory()
	{
		FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
		descriptor.setTitle("Select Directory for META-INF/MANIFEST.MF file");
		return descriptor;
	}

	public static void addManifestFileToLayout(final @Nonnull String path, final @Nonnull ArtifactEditorContext context, final @Nonnull CompositePackagingElement<?> element)
	{
		context.editLayout(context.getArtifact(), new Runnable()
		{
			public void run()
			{
				final VirtualFile file = findManifestFile(element, context, context.getArtifactType());
				if(file == null || !FileUtil.pathsEqual(file.getPath(), path))
				{
					PackagingElementFactory.getInstance(context.getProject()).addFileCopy(element, MANIFEST_DIR_NAME, path, MANIFEST_FILE_NAME);
				}
			}
		});
	}

	@javax.annotation.Nullable
	public static PsiClass selectMainClass(Project project, final @javax.annotation.Nullable String initialClassName)
	{
		final TreeClassChooserFactory chooserFactory = TreeClassChooserFactory.getInstance(project);
		final GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
		final PsiClass aClass = initialClassName != null ? JavaPsiFacade.getInstance(project).findClass(initialClassName, searchScope) : null;
		final TreeClassChooser chooser = chooserFactory.createWithInnerClassesScopeChooser("Select Main Class", searchScope, new MainClassFilter(), aClass);
		chooser.showDialog();
		return chooser.getSelected();
	}

	public static void setupMainClassField(final Project project, final TextFieldWithBrowseButton field)
	{
		field.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				final PsiClass selected = selectMainClass(project, field.getText());
				if(selected != null)
				{
					field.setText(selected.getQualifiedName());
				}
			}
		});
	}

	private static class MainClassFilter implements ClassFilter
	{
		@Override
		public boolean isAccepted(final PsiClass aClass)
		{
			return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>()
			{
				@Override
				public Boolean compute()
				{
					return PsiMethodUtil.MAIN_CLASS.value(aClass) && PsiMethodUtil.hasMainMethod(aClass);
				}
			});
		}
	}
}
