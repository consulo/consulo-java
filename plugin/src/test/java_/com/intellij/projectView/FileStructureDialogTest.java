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
package com.intellij.projectView;

import static org.junit.Assert.assertNotNull;

import javax.swing.ListModel;

import consulo.ide.impl.idea.ide.commander.CommanderPanel;
import consulo.fileEditor.structureView.StructureViewBuilder;
import consulo.fileEditor.structureView.StructureViewModel;
import consulo.fileEditor.structureView.TreeBasedStructureViewBuilder;
import consulo.ide.impl.idea.ide.util.FileStructureDialog;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorFactory;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.disposer.Disposable;

public abstract class FileStructureDialogTest extends BaseProjectViewTestCase
{
	public void testFileStructureForClass()
	{
		PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(getPackageDirectory());
		assertNotNull(aPackage);
		PsiClass psiClass = aPackage.getClasses()[0];
		VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
		assertNotNull(virtualFile);
		StructureViewBuilder structureViewBuilder = StructureViewBuilder.PROVIDER.getStructureViewBuilder(virtualFile.getFileType(), virtualFile, myProject);
		assertNotNull(structureViewBuilder);
		final StructureViewModel structureViewModel = ((TreeBasedStructureViewBuilder) structureViewBuilder).createStructureViewModel(null);

		EditorFactory factory = EditorFactory.getInstance();
		assertNotNull(factory);
		Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
		assertNotNull(document);

		Editor editor = factory.createEditor(document, myProject);
		try
		{
			FileStructureDialog dialog = new FileStructureDialog(structureViewModel, editor, myProject, psiClass, new Disposable()
			{
				@Override
				public void dispose()
				{
					structureViewModel.dispose();
				}
			}, true);
			try
			{
				CommanderPanel panel = dialog.getPanel();
				assertListsEqual((ListModel) panel.getModel(), "Inner1\n" + "Inner2\n" + "__method(): void\n" + "_myField1: int\n" + "_myField2: String\n");
			}
			finally
			{
				dialog.close(0);
			}
		}
		finally
		{
			factory.releaseEditor(editor);
		}
	}
}
