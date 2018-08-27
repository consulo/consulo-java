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

import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.arrangement.MemberOrderService;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.compiled.ClsElementImpl;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import consulo.java.module.extension.JavaModuleExtension;
import consulo.roots.OrderEntryWithTracking;
import consulo.roots.types.SourcesOrderRootType;

/**
 * @author yole
 */
@Singleton
public class JavaPsiImplementationHelperImpl extends JavaPsiImplementationHelper
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.JavaPsiImplementationHelperImpl");

	private final Project myProject;

	@Inject
	public JavaPsiImplementationHelperImpl(Project project)
	{
		myProject = project;
	}

	@Override
	public PsiClass getOriginalClass(PsiClass psiClass)
	{
		PsiCompiledElement cls = psiClass.getUserData(ClsElementImpl.COMPILED_ELEMENT);
		if(cls != null && cls.isValid())
		{
			return (PsiClass) cls;
		}

		if(DumbService.isDumb(myProject))
		{
			return psiClass;
		}

		VirtualFile vFile = psiClass.getContainingFile().getVirtualFile();
		final ProjectFileIndex idx = ProjectRootManager.getInstance(myProject).getFileIndex();
		if(vFile == null || !idx.isInLibrarySource(vFile))
		{
			return psiClass;
		}

		String fqn = psiClass.getQualifiedName();
		if(fqn == null)
		{
			return psiClass;
		}

		final Set<OrderEntry> orderEntries = ContainerUtil.newHashSet(idx.getOrderEntriesForFile(vFile));
		GlobalSearchScope librariesScope = LibraryScopeCache.getInstance(myProject).getLibrariesOnlyScope();
		for(PsiClass original : JavaPsiFacade.getInstance(myProject).findClasses(fqn, librariesScope))
		{
			PsiFile psiFile = original.getContainingFile();
			if(psiFile != null)
			{
				VirtualFile candidateFile = psiFile.getVirtualFile();
				if(candidateFile != null)
				{
					// order for file and vFile has non empty intersection.
					List<OrderEntry> entries = idx.getOrderEntriesForFile(candidateFile);
					//noinspection ForLoopReplaceableByForEach
					for(int i = 0; i < entries.size(); i++)
					{
						if(orderEntries.contains(entries.get(i)))
						{
							return original;
						}
					}
				}
			}
		}

		return psiClass;
	}

	@Override
	public PsiElement getClsFileNavigationElement(PsiJavaFile clsFile)
	{
		PsiClass[] classes = clsFile.getClasses();
		if(classes.length == 0)
		{
			return clsFile;
		}

		String sourceFileName = ((ClsClassImpl) classes[0]).getSourceFileName();
		String packageName = clsFile.getPackageName();
		String relativePath = packageName.isEmpty() ? sourceFileName : packageName.replace('.', '/') + '/' + sourceFileName;

		ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(clsFile.getProject());
		for(OrderEntry orderEntry : index.getOrderEntriesForFile(clsFile.getContainingFile().getVirtualFile()))
		{
			if(!(orderEntry instanceof OrderEntryWithTracking))
			{
				continue;
			}
			for(VirtualFile root : orderEntry.getFiles(SourcesOrderRootType.getInstance()))
			{
				VirtualFile source = root.findFileByRelativePath(relativePath);
				if(source != null && source.isValid())
				{
					PsiFile psiSource = clsFile.getManager().findFile(source);
					if(psiSource instanceof PsiClassOwner)
					{
						return psiSource;
					}
				}
			}
		}

		return clsFile;
	}

	@javax.annotation.Nullable
	@Override
	public LanguageLevel getClassesLanguageLevel(VirtualFile virtualFile)
	{
		final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
		final VirtualFile sourceRoot = index.getSourceRootForFile(virtualFile);
		final VirtualFile folder = virtualFile.getParent();
		if(sourceRoot != null && folder != null)
		{
			String relativePath = VfsUtilCore.getRelativePath(folder, sourceRoot, '/');
			if(relativePath == null)
			{
				throw new AssertionError("Null relative path: folder=" + folder + "; root=" + sourceRoot);
			}
			List<OrderEntry> orderEntries = index.getOrderEntriesForFile(virtualFile);
			if(orderEntries.isEmpty())
			{
				LOG.error("Inconsistent: " + DirectoryIndex.getInstance(myProject).getInfoForDirectory(folder).toString());
			}
			final VirtualFile[] files = orderEntries.get(0).getFiles(OrderRootType.CLASSES);
			for(VirtualFile rootFile : files)
			{
				final VirtualFile classFile = rootFile.findFileByRelativePath(relativePath);
				if(classFile != null)
				{
					final PsiJavaFile javaFile = getPsiFileInRoot(classFile);
					if(javaFile != null)
					{
						return javaFile.getLanguageLevel();
					}
				}
			}
			final Module moduleForFile = ModuleUtil.findModuleForFile(virtualFile, myProject);
			if(moduleForFile == null)
			{
				return null;
			}
			final JavaModuleExtension extension = ModuleUtil.getExtension(moduleForFile, JavaModuleExtension.class);
			return extension == null ? null : extension.getLanguageLevel();
		}
		return null;
	}

	@Nullable
	private PsiJavaFile getPsiFileInRoot(final VirtualFile dirFile)
	{
		final VirtualFile[] children = dirFile.getChildren();
		for(VirtualFile child : children)
		{
			if(JavaClassFileType.INSTANCE.equals(child.getFileType()))
			{
				final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(child);
				if(psiFile instanceof PsiJavaFile)
				{
					return (PsiJavaFile) psiFile;
				}
			}
		}
		return null;
	}

	@Override
	public ASTNode getDefaultImportAnchor(PsiImportList list, PsiImportStatementBase statement)
	{
		CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(list.getProject());
		ImportHelper importHelper = new ImportHelper(settings);
		return importHelper.getDefaultAnchor(list, statement);
	}

	@javax.annotation.Nullable
	@Override
	public PsiElement getDefaultMemberAnchor(@Nonnull PsiClass aClass, @Nonnull PsiMember member)
	{
		CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(aClass.getProject());
		MemberOrderService service = ServiceManager.getService(MemberOrderService.class);
		PsiElement anchor = service.getAnchor(member, settings.getCommonSettings(JavaLanguage.INSTANCE), aClass);

		PsiElement newAnchor = skipWhitespaces(aClass, anchor);
		if(newAnchor != null)
		{
			return newAnchor;
		}

		if(anchor != null && anchor != aClass)
		{
			anchor = anchor.getNextSibling();
			while(anchor instanceof PsiJavaToken && (anchor.getText().equals(",") || anchor.getText().equals(";")))
			{
				final boolean afterComma = anchor.getText().equals(",");
				anchor = anchor.getNextSibling();
				if(afterComma)
				{
					newAnchor = skipWhitespaces(aClass, anchor);
					if(newAnchor != null)
					{
						return newAnchor;
					}
				}
			}
			if(anchor != null)
			{
				return anchor;
			}
		}

		// The main idea is to avoid to anchor to 'white space' element because that causes reformatting algorithm
		// to perform incorrectly. The algorithm is encapsulated at the PostprocessReformattingAspect.doPostponedFormattingInner().
		final PsiElement lBrace = aClass.getLBrace();
		if(lBrace != null)
		{
			PsiElement result = lBrace.getNextSibling();
			while(result instanceof PsiWhiteSpace)
			{
				result = result.getNextSibling();
			}
			return result;
		}

		return aClass.getRBrace();
	}

	private static PsiElement skipWhitespaces(PsiClass aClass, PsiElement anchor)
	{
		if(anchor != null && PsiTreeUtil.skipSiblingsForward(anchor, PsiWhiteSpace.class) == aClass.getRBrace())
		{
			// Given member should be inserted as the last child.
			return aClass.getRBrace();
		}
		return null;
	}

	// TODO remove as soon as an arrangement sub-system is provided for groovy.
	public static int getMemberOrderWeight(PsiElement member, CodeStyleSettings settings)
	{
		if(member instanceof PsiField)
		{
			if(member instanceof PsiEnumConstant)
			{
				return 1;
			}
			return ((PsiField) member).hasModifierProperty(PsiModifier.STATIC) ? settings.STATIC_FIELDS_ORDER_WEIGHT + 1 : settings.FIELDS_ORDER_WEIGHT + 1;
		}
		if(member instanceof PsiMethod)
		{
			if(((PsiMethod) member).isConstructor())
			{
				return settings.CONSTRUCTORS_ORDER_WEIGHT + 1;
			}
			return ((PsiMethod) member).hasModifierProperty(PsiModifier.STATIC) ? settings.STATIC_METHODS_ORDER_WEIGHT + 1 : settings.METHODS_ORDER_WEIGHT + 1;
		}
		if(member instanceof PsiClass)
		{
			return ((PsiClass) member).hasModifierProperty(PsiModifier.STATIC) ? settings.STATIC_INNER_CLASSES_ORDER_WEIGHT + 1 : settings.INNER_CLASSES_ORDER_WEIGHT + 1;
		}
		return -1;
	}

	@Override
	public void setupCatchBlock(String exceptionName, PsiElement context, PsiCatchSection catchSection)
	{
		final FileTemplate catchBodyTemplate = FileTemplateManager.getInstance(catchSection.getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
		LOG.assertTrue(catchBodyTemplate != null);

		final Properties props = new Properties();
		props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, exceptionName);
		if(context != null && context.isPhysical())
		{
			final PsiDirectory directory = context.getContainingFile().getContainingDirectory();
			if(directory != null)
			{
				JavaTemplateUtil.setPackageNameAttribute(props, directory);
			}
		}

		final PsiCodeBlock codeBlockFromText;
		try
		{
			codeBlockFromText = PsiElementFactory.SERVICE.getInstance(myProject).createCodeBlockFromText("{\n" + catchBodyTemplate.getText(props) + "\n}", null);
		}
		catch(ProcessCanceledException ce)
		{
			throw ce;
		}
		catch(Throwable e)
		{
			throw new IncorrectOperationException("Incorrect file template", e);
		}
		catchSection.getCatchBlock().replace(codeBlockFromText);
	}
}
