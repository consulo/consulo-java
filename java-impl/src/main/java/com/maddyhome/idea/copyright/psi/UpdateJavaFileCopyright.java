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

package com.maddyhome.idea.copyright.psi;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.swing.JPanel;

import javax.annotation.Nullable;
import consulo.logging.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.ui.TemplateCommentPanel;
import consulo.copyright.config.CopyrightFileConfig;

public class UpdateJavaFileCopyright extends UpdatePsiFileCopyright<CopyrightFileConfig>
{
	public static final int LOCATION_BEFORE_PACKAGE = 1;
	public static final int LOCATION_BEFORE_IMPORT = 2;
	public static final int LOCATION_BEFORE_CLASS = 3;

	public UpdateJavaFileCopyright(@Nonnull PsiFile psiFile, @Nonnull CopyrightProfile copyrightProfile)
	{
		super(psiFile, copyrightProfile);
	}

	@Override
	protected boolean accept()
	{
		return getFile() instanceof PsiJavaFile;
	}

	@Override
	protected void scanFile()
	{
		logger.debug("updating " + getFile().getVirtualFile());

		PsiClassOwner javaFile = (PsiClassOwner) getFile();
		PsiElement pkg = getPackageStatement();
		PsiElement[] imports = getImportsList();
		PsiClass topclass = null;
		PsiClass[] classes = javaFile.getClasses();
		if(classes.length > 0)
		{
			topclass = classes[0];
		}

		PsiElement first = javaFile.getFirstChild();

		int location = getLanguageOptions().getFileLocation();
		if(pkg != null)
		{
			checkComments(first, pkg, location == LOCATION_BEFORE_PACKAGE);
			first = pkg;
		}
		else if(location == LOCATION_BEFORE_PACKAGE)
		{
			location = LOCATION_BEFORE_IMPORT;
		}

		if(imports != null && imports.length > 0)
		{
			checkComments(first, imports[0], location == LOCATION_BEFORE_IMPORT);
			first = imports[0];
		}
		else if(location == LOCATION_BEFORE_IMPORT)
		{
			location = LOCATION_BEFORE_CLASS;
		}

		if(topclass != null)
		{
			final List<PsiComment> comments = new ArrayList<PsiComment>();
			collectComments(first, topclass, comments);
			collectComments(topclass.getFirstChild(), topclass.getModifierList(), comments);
			checkCommentsForTopClass(topclass, location, comments);
		}
		else if(location == LOCATION_BEFORE_CLASS)
		{
			// no package, no imports, no top level class
		}
	}

	protected void checkCommentsForTopClass(PsiClass topclass, int location, List<PsiComment> comments)
	{
		checkComments(topclass.getModifierList(), location == LOCATION_BEFORE_CLASS, comments);
	}

	@Nullable
	protected PsiElement[] getImportsList()
	{
		final PsiJavaFile javaFile = (PsiJavaFile) getFile();
		final PsiImportList importList = javaFile.getImportList();
		return importList == null ? null : importList.getChildren();
	}

	@Nullable
	protected PsiElement getPackageStatement()
	{
		PsiJavaFile javaFile = (PsiJavaFile) getFile();
		return javaFile.getPackageStatement();
	}

	private static final Logger logger = Logger.getInstance(UpdateJavaFileCopyright.class);

	public static class UpdateJavaCopyrightsProvider extends UpdateCopyrightsProvider<CopyrightFileConfig>
	{
		@Nonnull
		@Override
		public UpdatePsiFileCopyright<CopyrightFileConfig> createInstance(@Nonnull PsiFile file, @Nonnull CopyrightProfile copyrightProfile)
		{
			return new UpdateJavaFileCopyright(file, copyrightProfile);
		}

		@Nonnull
		@Override
		public CopyrightFileConfig createDefaultOptions()
		{
			return new CopyrightFileConfig();
		}

		@Nonnull
		@Override
		public TemplateCommentPanel createConfigurable(@Nonnull Project project, @Nonnull TemplateCommentPanel parentPane,
				@Nonnull FileType fileType)
		{
			return new TemplateCommentPanel(fileType, parentPane, project)
			{
				@Override
				public void addAdditionalComponents(@Nonnull JPanel additionalPanel)
				{
					addLocationInFile(new String[]{
							"Before Package",
							"Before Imports",
							"Before Class"
					});
				}
			};
		}
	}
}