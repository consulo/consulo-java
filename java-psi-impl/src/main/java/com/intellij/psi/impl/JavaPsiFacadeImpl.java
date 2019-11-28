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
package com.intellij.psi.impl;

import gnu.trove.THashSet;

import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.jetbrains.annotations.TestOnly;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.module.extension.JavaModuleExtension;
import consulo.psi.PsiPackageManager;

/**
 * @author max
 */
@Singleton
public class JavaPsiFacadeImpl extends JavaPsiFacadeEx
{
	private PsiElementFinder[] myElementFinders; //benign data race
	private final PsiConstantEvaluationHelper myConstantEvaluationHelper;

	private final Project myProject;
	private final PsiPackageManager myPackageManager;

	@Inject
	public JavaPsiFacadeImpl(Project project, PsiPackageManager psiManager)
	{
		myProject = project;
		myPackageManager = psiManager;
		myConstantEvaluationHelper = new PsiConstantEvaluationHelperImpl();

		JavaElementType.ANNOTATION.getIndex(); // Initialize stubs.
	}

	@Override
	public PsiClass findClass(@Nonnull final String qualifiedName, @Nonnull GlobalSearchScope scope)
	{
		ProgressIndicatorProvider.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

		if(DumbService.getInstance(getProject()).isDumb())
		{
			PsiClass[] classes = findClassesInDumbMode(qualifiedName, scope);
			if(classes.length != 0)
			{
				return classes[0];
			}
			return null;
		}

		for(PsiElementFinder finder : finders())
		{
			PsiClass aClass = finder.findClass(qualifiedName, scope);
			if(aClass != null)
			{
				return aClass;
			}
		}

		return null;
	}

	@Nonnull
	private PsiClass[] findClassesInDumbMode(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope)
	{
		final String packageName = StringUtil.getPackageName(qualifiedName);
		final PsiJavaPackage pkg = findPackage(packageName);
		final String className = StringUtil.getShortName(qualifiedName);
		if(pkg == null && packageName.length() < qualifiedName.length())
		{
			PsiClass[] containingClasses = findClassesInDumbMode(packageName, scope);
			if(containingClasses.length == 1)
			{
				return PsiElementFinder.filterByName(className, containingClasses[0].getInnerClasses());
			}

			return PsiClass.EMPTY_ARRAY;
		}

		if(pkg == null || !pkg.containsClassNamed(className))
		{
			return PsiClass.EMPTY_ARRAY;
		}

		return pkg.findClassByShortName(className, scope);
	}

	@Override
	@Nonnull
	public PsiClass[] findClasses(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope)
	{
		if(DumbService.getInstance(getProject()).isDumb())
		{
			return findClassesInDumbMode(qualifiedName, scope);
		}

		List<PsiClass> classes = new SmartList<>();
		for(PsiElementFinder finder : finders())
		{
			PsiClass[] finderClasses = finder.findClasses(qualifiedName, scope);
			ContainerUtil.addAll(classes, finderClasses);
		}

		return classes.toArray(new PsiClass[classes.size()]);
	}

	@Nonnull
	private PsiElementFinder[] finders()
	{
		PsiElementFinder[] answer = myElementFinders;
		if(answer == null)
		{
			answer = calcFinders();
			myElementFinders = answer;
		}

		return answer;
	}

	@Nonnull
	private PsiElementFinder[] calcFinders()
	{
		List<PsiElementFinder> elementFinders = new ArrayList<>();
		elementFinders.add(new PsiElementFinderImpl());
		ContainerUtil.addAll(elementFinders, myProject.getExtensions(PsiElementFinder.EP_NAME));
		return elementFinders.toArray(new PsiElementFinder[elementFinders.size()]);
	}

	@Override
	@Nonnull
	public PsiConstantEvaluationHelper getConstantEvaluationHelper()
	{
		return myConstantEvaluationHelper;
	}

	@Override
	public PsiJavaPackage findPackage(@Nonnull String qualifiedName)
	{
		for(PsiElementFinder elementFinder : filteredFinders())
		{
			PsiJavaPackage aPackage = elementFinder.findPackage(qualifiedName);
			if(aPackage != null)
			{
				return aPackage;
			}
		}
		return (PsiJavaPackage) myPackageManager.findPackage(qualifiedName, JavaModuleExtension.class);
	}

	@Override
	@Nonnull
	public PsiJavaPackage[] getSubPackages(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope)
	{
		LinkedHashSet<PsiJavaPackage> result = new LinkedHashSet<>();
		for(PsiElementFinder finder : filteredFinders())
		{
			PsiJavaPackage[] packages = finder.getSubPackages(psiPackage, scope);
			ContainerUtil.addAll(result, packages);
		}

		return result.toArray(new PsiJavaPackage[result.size()]);
	}

	@Nonnull
	private PsiElementFinder[] filteredFinders()
	{
		DumbService dumbService = DumbService.getInstance(getProject());
		PsiElementFinder[] finders = finders();
		if(dumbService.isDumb())
		{
			List<PsiElementFinder> list = dumbService.filterByDumbAwareness(Arrays.asList(finders));
			finders = list.toArray(new PsiElementFinder[list.size()]);
		}
		return finders;
	}

	@Override
	@Nonnull
	public PsiJavaParserFacade getParserFacade()
	{
		return getElementFactory(); // TODO: lighter implementation which doesn't mark all the elements as generated.
	}

	@Override
	@Nonnull
	public PsiResolveHelper getResolveHelper()
	{
		return PsiResolveHelper.SERVICE.getInstance(myProject);
	}

	@Override
	@Nonnull
	public PsiNameHelper getNameHelper()
	{
		return PsiNameHelper.getInstance(myProject);
	}

	@Nonnull
	public Set<String> getClassNames(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope)
	{
		Set<String> result = new THashSet<>();
		for(PsiElementFinder finder : filteredFinders())
		{
			result.addAll(finder.getClassNames(psiPackage, scope));
		}
		return result;
	}

	@Nonnull
	public PsiClass[] getClasses(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope)
	{
		List<PsiClass> result = null;
		for(PsiElementFinder finder : filteredFinders())
		{
			PsiClass[] classes = finder.getClasses(psiPackage, scope);
			if(classes.length == 0)
			{
				continue;
			}
			if(result == null)
			{
				result = new ArrayList<>();
			}
			ContainerUtil.addAll(result, classes);
		}

		return result == null ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
	}

	public boolean processPackageDirectories(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope, @Nonnull Processor<PsiDirectory> consumer)
	{
		for(PsiElementFinder finder : filteredFinders())
		{
			if(!finder.processPackageDirectories(psiPackage, scope, consumer))
			{
				return false;
			}
		}
		return true;
	}

	public PsiClass[] findClassByShortName(String name, PsiJavaPackage psiPackage, GlobalSearchScope scope)
	{
		List<PsiClass> result = null;
		for(PsiElementFinder finder : filteredFinders())
		{
			PsiClass[] classes = finder.getClasses(name, psiPackage, scope);
			if(classes.length == 0)
			{
				continue;
			}
			if(result == null)
			{
				result = new ArrayList<>();
			}
			ContainerUtil.addAll(result, classes);
		}

		return result == null ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
	}

	public class PsiElementFinderImpl extends PsiElementFinder implements DumbAware
	{
		@Override
		public PsiClass findClass(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope)
		{
			return JavaFileManager.getInstance(myProject).findClass(qualifiedName, scope);
		}

		@Override
		@Nonnull
		public PsiClass[] findClasses(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope)
		{
			return JavaFileManager.getInstance(myProject).findClasses(qualifiedName, scope);
		}

		@Override
		public PsiJavaPackage findPackage(@Nonnull String qualifiedName)
		{
			return (PsiJavaPackage) PsiPackageManager.getInstance(getProject()).findPackage(qualifiedName, JavaModuleExtension.class);
		}

		@Override
		@Nonnull
		public PsiJavaPackage[] getSubPackages(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope)
		{
			final Map<String, PsiJavaPackage> packagesMap = new HashMap<>();
			final String qualifiedName = psiPackage.getQualifiedName();
			for(PsiDirectory dir : psiPackage.getDirectories(scope))
			{
				PsiDirectory[] subDirs = dir.getSubdirectories();
				for(PsiDirectory subDir : subDirs)
				{
					final PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(subDir);
					if(aPackage != null)
					{
						final String subQualifiedName = aPackage.getQualifiedName();
						if(subQualifiedName.startsWith(qualifiedName) && !packagesMap.containsKey(subQualifiedName))
						{
							packagesMap.put(aPackage.getQualifiedName(), aPackage);
						}
					}
				}
			}

			packagesMap.remove(qualifiedName);    // avoid SOE caused by returning a package as a subpackage of itself
			return packagesMap.values().toArray(new PsiJavaPackage[packagesMap.size()]);
		}

		@Override
		@Nonnull
		public PsiClass[] getClasses(@Nonnull PsiJavaPackage psiPackage, @Nonnull final GlobalSearchScope scope)
		{
			return getClasses(null, psiPackage, scope);
		}

		@Override
		@Nonnull
		public PsiClass[] getClasses(@Nullable String shortName, @Nonnull PsiJavaPackage psiPackage, @Nonnull final GlobalSearchScope scope)
		{
			List<PsiClass> list = null;
			String packageName = psiPackage.getQualifiedName();
			for(PsiDirectory dir : psiPackage.getDirectories(scope))
			{
				PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(dir);
				if(classes.length == 0)
				{
					continue;
				}
				if(list == null)
				{
					list = new ArrayList<>();
				}
				for(PsiClass aClass : classes)
				{
					// class file can be located in wrong place inside file system
					String qualifiedName = aClass.getQualifiedName();
					if(qualifiedName != null)
					{
						qualifiedName = StringUtil.getPackageName(qualifiedName);
					}
					if(Comparing.strEqual(qualifiedName, packageName))
					{
						if(shortName == null || shortName.equals(aClass.getName()))
						{
							list.add(aClass);
						}
					}
				}
			}
			if(list == null)
			{
				return PsiClass.EMPTY_ARRAY;
			}

			if(list.size() > 1)
			{
				ContainerUtil.quickSort(list, new Comparator<PsiClass>()
				{
					@Override
					public int compare(PsiClass o1, PsiClass o2)
					{
						VirtualFile file1 = PsiUtilCore.getVirtualFile(o1);
						VirtualFile file2 = PsiUtilCore.getVirtualFile(o2);
						return file1 == null ? file2 == null ? 0 : -1 : file2 == null ? 1 : scope.compare(file2, file1);
					}
				});
			}

			return list.toArray(new PsiClass[list.size()]);
		}

		@Nonnull
		@Override
		public Set<String> getClassNames(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope)
		{
			Set<String> names = null;
			FileIndexFacade facade = FileIndexFacade.getInstance(myProject);
			for(PsiDirectory dir : psiPackage.getDirectories(scope))
			{
				for(PsiFile file : dir.getFiles())
				{
					if(file instanceof PsiClassOwner && file.getViewProvider().getLanguages().size() == 1)
					{
						VirtualFile vFile = file.getVirtualFile();
						if(vFile != null &&
								!(file instanceof PsiCompiledElement) &&
								!facade.isInSourceContent(vFile) &&
								(!scope.isForceSearchingInLibrarySources() || !StubTreeLoader.getInstance().canHaveStub(vFile)))
						{
							continue;
						}

						Set<String> inFile = file instanceof PsiClassOwnerEx ? ((PsiClassOwnerEx) file).getClassNames() : getClassNames(((PsiClassOwner) file).getClasses());

						if(inFile.isEmpty())
						{
							continue;
						}
						if(names == null)
						{
							names = new HashSet<>();
						}
						names.addAll(inFile);
					}
				}

			}
			return names == null ? Collections.<String>emptySet() : names;
		}


		@Override
		public boolean processPackageDirectories(@Nonnull PsiJavaPackage psiPackage, @Nonnull final GlobalSearchScope scope, @Nonnull final Processor<PsiDirectory> consumer)
		{
			final PsiManager psiManager = PsiManager.getInstance(getProject());
			return DirectoryIndex.getInstance(getProject()).getDirectoriesByPackageName(psiPackage.getQualifiedName(), false).forEach(new ReadActionProcessor<VirtualFile>()
			{
				@RequiredReadAction
				@Override
				public boolean processInReadAction(final VirtualFile dir)
				{
					if(!scope.contains(dir))
					{
						return true;
					}
					PsiDirectory psiDir = psiManager.findDirectory(dir);
					return psiDir == null || consumer.process(psiDir);
				}
			});
		}
	}


	@Override
	public boolean isPartOfPackagePrefix(@Nonnull String packageName)
	{
		final Collection<String> packagePrefixes = JavaFileManager.getInstance(myProject).getNonTrivialPackagePrefixes();
		for(final String subpackageName : packagePrefixes)
		{
			if(isSubpackageOf(subpackageName, packageName))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isSubpackageOf(@Nonnull String subpackageName, @Nonnull String packageName)
	{
		return subpackageName.equals(packageName) || subpackageName.startsWith(packageName) && subpackageName.charAt(packageName.length()) == '.';
	}

	@Override
	public boolean isInPackage(@Nonnull PsiElement element, @Nonnull PsiJavaPackage aPackage)
	{
		final PsiFile file = FileContextUtil.getContextFile(element);
		if(file instanceof JavaDummyHolder)
		{
			return ((JavaDummyHolder) file).isInPackage(aPackage);
		}
		if(file instanceof PsiJavaFile)
		{
			final String packageName = ((PsiJavaFile) file).getPackageName();
			return packageName.equals(aPackage.getQualifiedName());
		}
		return false;
	}

	@Override
	public boolean arePackagesTheSame(@Nonnull PsiElement element1, @Nonnull PsiElement element2)
	{
		PsiFile file1 = FileContextUtil.getContextFile(element1);
		PsiFile file2 = FileContextUtil.getContextFile(element2);
		if(Comparing.equal(file1, file2))
		{
			return true;
		}
		if(file1 instanceof JavaDummyHolder && file2 instanceof JavaDummyHolder)
		{
			return true;
		}
		if(file1 instanceof JavaDummyHolder || file2 instanceof JavaDummyHolder)
		{
			JavaDummyHolder dummyHolder = (JavaDummyHolder) (file1 instanceof JavaDummyHolder ? file1 : file2);
			PsiElement other = file1 instanceof JavaDummyHolder ? file2 : file1;
			return dummyHolder.isSamePackage(other);
		}
		if(!(file1 instanceof PsiClassOwner))
		{
			return false;
		}
		if(!(file2 instanceof PsiClassOwner))
		{
			return false;
		}
		String package1 = ((PsiClassOwner) file1).getPackageName();
		String package2 = ((PsiClassOwner) file2).getPackageName();
		return Comparing.equal(package1, package2);
	}

	@Override
	@Nonnull
	public Project getProject()
	{
		return myProject;
	}

	@Override
	@Nonnull
	public PsiElementFactory getElementFactory()
	{
		return PsiElementFactory.SERVICE.getInstance(myProject);
	}

	@TestOnly
	@Override
	public void setAssertOnFileLoadingFilter(@Nonnull final VirtualFileFilter filter, @Nonnull Disposable parentDisposable)
	{
		((PsiManagerImpl) PsiManager.getInstance(myProject)).setAssertOnFileLoadingFilter(filter, parentDisposable);
	}
}
