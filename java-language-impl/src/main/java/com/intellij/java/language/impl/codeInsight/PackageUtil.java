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
package com.intellij.java.language.impl.codeInsight;

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.application.util.function.Computable;
import consulo.application.util.query.Query;
import consulo.ide.IdeBundle;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.ide.util.DirectoryChooserUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.DirectoryIndex;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.OrderEnumerator;
import consulo.project.Project;
import consulo.project.ProjectBundle;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PackageUtil
{
	private static final Logger LOG = Logger.getInstance(PackageUtil.class);

	@jakarta.annotation.Nullable
	public static PsiDirectory findPossiblePackageDirectoryInModule(Module module, String packageName)
	{
		PsiDirectory psiDirectory = null;
		if(!"".equals(packageName))
		{
			PsiJavaPackage rootPackage = findLongestExistingPackage(module.getProject(), packageName);
			if(rootPackage != null)
			{
				final PsiDirectory[] psiDirectories = getPackageDirectoriesInModule(rootPackage, module);
				if(psiDirectories.length > 0)
				{
					psiDirectory = psiDirectories[0];
				}
			}
		}
		if(psiDirectory == null)
		{
			if(checkSourceRootsConfigured(module))
			{
				final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
				for(VirtualFile sourceRoot : sourceRoots)
				{
					final PsiDirectory directory = PsiManager.getInstance(module.getProject()).findDirectory(sourceRoot);
					if(directory != null)
					{
						psiDirectory = directory;
						break;
					}
				}
			}
		}
		return psiDirectory;
	}

	/**
	 * @deprecated
	 */
	@Nullable
	public static PsiDirectory findOrCreateDirectoryForPackage(Project project, String packageName, PsiDirectory baseDir, boolean askUserToCreate) throws IncorrectOperationException
	{
		return findOrCreateDirectoryForPackage(project, packageName, baseDir, askUserToCreate, false);
	}

	/**
	 * @deprecated
	 */
	@Nullable
	public static PsiDirectory findOrCreateDirectoryForPackage(Project project,
															   String packageName,
															   PsiDirectory baseDir,
															   boolean askUserToCreate,
															   boolean filterSourceDirsForTestBaseDir) throws IncorrectOperationException
	{

		PsiDirectory psiDirectory = null;

		if(!"".equals(packageName))
		{
			PsiJavaPackage rootPackage = findLongestExistingPackage(project, packageName);
			if(rootPackage != null)
			{
				int beginIndex = rootPackage.getQualifiedName().length() + 1;
				packageName = beginIndex < packageName.length() ? packageName.substring(beginIndex) : "";
				String postfixToShow = packageName.replace('.', File.separatorChar);
				if(packageName.length() > 0)
				{
					postfixToShow = File.separatorChar + postfixToShow;
				}
				PsiDirectory[] directories = rootPackage.getDirectories();
				if(filterSourceDirsForTestBaseDir)
				{
					directories = filterSourceDirectories(baseDir, project, directories);
				}
				psiDirectory = DirectoryChooserUtil.selectDirectory(project, directories, baseDir, postfixToShow);
				if(psiDirectory == null)
				{
					return null;
				}
			}
		}

		if(psiDirectory == null)
		{
			PsiDirectory[] sourceDirectories = getSourceRootDirectories(project);
			psiDirectory = DirectoryChooserUtil.selectDirectory(project, sourceDirectories, baseDir, File.separatorChar + packageName.replace('.', File.separatorChar));
			if(psiDirectory == null)
			{
				return null;
			}
		}

		String restOfName = packageName;
		boolean askedToCreate = false;
		while(restOfName.length() > 0)
		{
			final String name = getLeftPart(restOfName);
			PsiDirectory foundExistingDirectory = psiDirectory.findSubdirectory(name);
			if(foundExistingDirectory == null)
			{
				if(!askedToCreate && askUserToCreate)
				{
					int toCreate = Messages.showYesNoDialog(project, IdeBundle.message("prompt.create.non.existing.package", packageName), IdeBundle.message("title.package.not.found"), Messages
							.getQuestionIcon());
					if(toCreate != 0)
					{
						return null;
					}
					askedToCreate = true;
				}
				psiDirectory = createSubdirectory(psiDirectory, name, project);
			}
			else
			{
				psiDirectory = foundExistingDirectory;
			}
			restOfName = cutLeftPart(restOfName);
		}
		return psiDirectory;
	}

	private static PsiDirectory createSubdirectory(final PsiDirectory oldDirectory, final String name, Project project) throws IncorrectOperationException
	{
		final PsiDirectory[] psiDirectory = new PsiDirectory[1];
		final IncorrectOperationException[] exception = new IncorrectOperationException[1];

		CommandProcessor.getInstance().executeCommand(project, new Runnable()
		{
			public void run()
			{
				psiDirectory[0] = ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>()
				{
					public PsiDirectory compute()
					{
						try
						{
							return oldDirectory.createSubdirectory(name);
						}
						catch(IncorrectOperationException e)
						{
							exception[0] = e;
							return null;
						}
					}
				});
			}
		}, IdeBundle.message("command.create.new.subdirectory"), null);

		if(exception[0] != null)
		{
			throw exception[0];
		}

		return psiDirectory[0];
	}

	@jakarta.annotation.Nullable
	public static PsiDirectory findOrCreateDirectoryForPackage(@Nonnull Module module, String packageName, @Nullable PsiDirectory baseDir, boolean askUserToCreate) throws IncorrectOperationException
	{
		return findOrCreateDirectoryForPackage(module, packageName, baseDir, askUserToCreate, false);
	}

	@jakarta.annotation.Nullable
	public static PsiDirectory findOrCreateDirectoryForPackage(@Nonnull Module module,
															   String packageName,
															   PsiDirectory baseDir,
															   boolean askUserToCreate,
															   boolean filterSourceDirsForBaseTestDirectory) throws IncorrectOperationException
	{
		final Project project = module.getProject();
		PsiDirectory psiDirectory = null;
		if(!packageName.isEmpty())
		{
			PsiJavaPackage rootPackage = findLongestExistingPackage(module, packageName);
			if(rootPackage != null)
			{
				int beginIndex = rootPackage.getQualifiedName().length() + 1;
				packageName = beginIndex < packageName.length() ? packageName.substring(beginIndex) : "";
				String postfixToShow = packageName.replace('.', File.separatorChar);
				if(packageName.length() > 0)
				{
					postfixToShow = File.separatorChar + postfixToShow;
				}
				PsiDirectory[] moduleDirectories = getPackageDirectoriesInModule(rootPackage, module);
				if(filterSourceDirsForBaseTestDirectory)
				{
					moduleDirectories = filterSourceDirectories(baseDir, project, moduleDirectories);
				}
				psiDirectory = DirectoryChooserUtil.selectDirectory(project, moduleDirectories, baseDir, postfixToShow);
				if(psiDirectory == null)
				{
					return null;
				}
			}
		}

		if(psiDirectory == null)
		{
			if(!checkSourceRootsConfigured(module, askUserToCreate))
			{
				return null;
			}
			final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
			List<PsiDirectory> directoryList = new ArrayList<PsiDirectory>();
			for(VirtualFile sourceRoot : sourceRoots)
			{
				final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(sourceRoot);
				if(directory != null)
				{
					directoryList.add(directory);
				}
			}
			PsiDirectory[] sourceDirectories = directoryList.toArray(new PsiDirectory[directoryList.size()]);
			psiDirectory = DirectoryChooserUtil.selectDirectory(project, sourceDirectories, baseDir, File.separatorChar + packageName.replace('.', File.separatorChar));
			if(psiDirectory == null)
			{
				return null;
			}
		}

		String restOfName = packageName;
		boolean askedToCreate = false;
		while(restOfName.length() > 0)
		{
			final String name = getLeftPart(restOfName);
			PsiDirectory foundExistingDirectory = psiDirectory.findSubdirectory(name);
			if(foundExistingDirectory == null)
			{
				if(!askedToCreate && askUserToCreate)
				{
					if(!ApplicationManager.getApplication().isUnitTestMode())
					{
						int toCreate = Messages.showYesNoDialog(project, IdeBundle.message("prompt.create.non.existing.package", packageName), IdeBundle.message("title.package.not.found"), Messages
								.getQuestionIcon());
						if(toCreate != 0)
						{
							return null;
						}
					}
					askedToCreate = true;
				}

				final PsiDirectory psiDirectory1 = psiDirectory;
				try
				{
					psiDirectory = WriteAction.compute(() -> psiDirectory1.createSubdirectory(name));
				}
				catch(IncorrectOperationException e)
				{
					throw e;
				}
				catch(Exception e)
				{
					LOG.error(e);
				}
			}
			else
			{
				psiDirectory = foundExistingDirectory;
			}
			restOfName = cutLeftPart(restOfName);
		}
		return psiDirectory;
	}

	private static PsiDirectory[] filterSourceDirectories(PsiDirectory baseDir, Project project, PsiDirectory[] moduleDirectories)
	{
		final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
		if(fileIndex.isInTestSourceContent(baseDir.getVirtualFile()))
		{
			List<PsiDirectory> result = new ArrayList<PsiDirectory>();
			for(PsiDirectory moduleDirectory : moduleDirectories)
			{
				if(fileIndex.isInTestSourceContent(moduleDirectory.getVirtualFile()))
				{
					result.add(moduleDirectory);
				}
			}
			moduleDirectories = result.toArray(new PsiDirectory[result.size()]);
		}
		return moduleDirectories;
	}

	private static PsiDirectory[] getPackageDirectoriesInModule(PsiJavaPackage rootPackage, Module module)
	{
		return rootPackage.getDirectories(GlobalSearchScope.moduleScope(module));
	}

	private static PsiJavaPackage findLongestExistingPackage(Project project, String packageName)
	{
		PsiManager manager = PsiManager.getInstance(project);
		String nameToMatch = packageName;
		while(true)
		{
			PsiJavaPackage aPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage(nameToMatch);
			if(aPackage != null && isWritablePackage(aPackage))
			{
				return aPackage;
			}
			int lastDotIndex = nameToMatch.lastIndexOf('.');
			if(lastDotIndex >= 0)
			{
				nameToMatch = nameToMatch.substring(0, lastDotIndex);
			}
			else
			{
				return null;
			}
		}
	}

	private static boolean isWritablePackage(PsiJavaPackage aPackage)
	{
		PsiDirectory[] directories = aPackage.getDirectories();
		for(PsiDirectory directory : directories)
		{
			if(directory.isValid() && directory.isWritable())
			{
				return true;
			}
		}
		return false;
	}

	private static PsiDirectory getWritableModuleDirectory(@Nonnull Query<VirtualFile> vFiles, @Nonnull Module module, PsiManager manager)
	{
		for(VirtualFile vFile : vFiles)
		{
			if(ModuleUtilCore.findModuleForFile(vFile, module.getProject()) != module)
			{
				continue;
			}
			PsiDirectory directory = manager.findDirectory(vFile);
			if(directory != null && directory.isValid() && directory.isWritable())
			{
				return directory;
			}
		}
		return null;
	}

	private static PsiJavaPackage findLongestExistingPackage(Module module, String packageName)
	{
		final PsiManager manager = PsiManager.getInstance(module.getProject());

		String nameToMatch = packageName;
		while(true)
		{
			Query<VirtualFile> vFiles = DirectoryIndex.getInstance(module.getProject()).getDirectoriesByPackageName(nameToMatch, false);
			PsiDirectory directory = getWritableModuleDirectory(vFiles, module, manager);
			if(directory != null)
			{
				return JavaDirectoryService.getInstance().getPackage(directory);
			}

			int lastDotIndex = nameToMatch.lastIndexOf('.');
			if(lastDotIndex >= 0)
			{
				nameToMatch = nameToMatch.substring(0, lastDotIndex);
			}
			else
			{
				return null;
			}
		}
	}

	private static String getLeftPart(String packageName)
	{
		int index = packageName.indexOf('.');
		return index > -1 ? packageName.substring(0, index) : packageName;
	}

	private static String cutLeftPart(String packageName)
	{
		int index = packageName.indexOf('.');
		return index > -1 ? packageName.substring(index + 1) : "";
	}

	public static boolean checkSourceRootsConfigured(final Module module)
	{
		return checkSourceRootsConfigured(module, true);
	}

	public static boolean checkSourceRootsConfigured(final Module module, final boolean askUserToSetupSourceRoots)
	{
		VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
		if(sourceRoots.length == 0)
		{
			if(!askUserToSetupSourceRoots)
			{
				return false;
			}

			Project project = module.getProject();
			Messages.showErrorDialog(project, ProjectBundle.message("module.source.roots.not.configured.error", module.getName()), ProjectBundle.message("module.source.roots.not.configured.title"));

			ShowSettingsUtil.getInstance().showProjectStructureDialog(module.getProject(), projectStructureSelector -> {
				projectStructureSelector.select(module.getName(), null, true);
			});

			sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
			if(sourceRoots.length == 0)
			{
				return false;
			}
		}
		return true;
	}

	public static PsiDirectory[] convertRoots(final Project project, VirtualFile[] roots)
	{
		ArrayList<PsiDirectory> dirs = new ArrayList<PsiDirectory>();

		PsiManager psiManager = PsiManager.getInstance(project);
		for(VirtualFile root : roots)
		{
			if(!root.isValid())
			{
				LOG.error("Root " + root + " is not valid!");
			}
			PsiDirectory dir = psiManager.findDirectory(root);
			if(dir != null)
			{
				dirs.add(dir);
			}
		}

		return dirs.toArray(new PsiDirectory[dirs.size()]);
	}

	public static PsiDirectory[] getSourceRootDirectories(final Project project)
	{
		VirtualFile[] files = OrderEnumerator.orderEntries(project).sources().usingCache().getRoots();
		return convertRoots(project, files);
	}

	public static PsiDirectory[] getAllContentRoots(final Project project)
	{
		VirtualFile[] files = ProjectRootManager.getInstance(project).getContentRootsFromAllModules();
		return convertRoots(project, files);
	}

	@Nonnull
	public static PsiDirectory findOrCreateSubdirectory(@Nonnull PsiDirectory directory, @Nonnull String directoryName)
	{
		PsiDirectory subDirectory = directory.findSubdirectory(directoryName);
		if(subDirectory == null)
		{
			subDirectory = directory.createSubdirectory(directoryName);
		}
		return subDirectory;
	}
}
