/*
 * Copyright 2013-2018 consulo.io
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

package consulo.java.fileEditor.impl;

import javax.annotation.Nullable;

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.psi.PsiManager;
import consulo.annotation.access.RequiredReadAction;

/**
 * @author VISTALL
 * @since 2018-07-15
 */
public class JavaModuleEditorTabTitleProvider implements EditorTabTitleProvider
{
	@Nullable
	@Override
	@RequiredReadAction
	public String getEditorTabTitle(Project project, VirtualFile virtualFile)
	{
		if(PsiJavaModule.MODULE_INFO_CLASS.equals(virtualFile.getNameWithoutExtension()))
		{
			PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
			if(file instanceof PsiJavaFile)
			{
				PsiJavaModule moduleDeclaration = ((PsiJavaFile) file).getModuleDeclaration();
				if(moduleDeclaration != null)
				{
					return moduleDeclaration.getName();
				}
			}
		}
		return null;
	}
}
