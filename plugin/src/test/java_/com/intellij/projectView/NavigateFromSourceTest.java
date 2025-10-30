/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.projectView;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.JTree;

import consulo.dataContext.DataManager;
import consulo.project.ui.view.ProjectView;
import consulo.ide.impl.idea.ide.projectView.impl.AbstractProjectViewPSIPane;
import consulo.ide.impl.idea.ide.projectView.impl.ProjectViewImpl;
import consulo.project.ui.view.ProjectViewPane;
import consulo.ide.impl.idea.ide.projectView.impl.ProjectViewToolWindowFactory;
import consulo.dataContext.DataContext;
import consulo.fileEditor.FileEditorManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.virtualFileSystem.VirtualFile;
import consulo.project.ui.wm.ToolWindowId;
import consulo.project.ui.wm.ToolWindowManager;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.testFramework.PlatformTestUtil;

@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class NavigateFromSourceTest extends BaseProjectViewTestCase
{
	public void testShowClassMembers() throws Exception
	{
		useStandardProviders();
		final PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(getPackageDirectory());
		sortClassesByName(classes);
		PsiClass psiClass = classes[0];

		final AbstractProjectViewPSIPane pane = myStructure.createPane();
		final PsiFile containingFile = psiClass.getContainingFile();
		final VirtualFile virtualFile = containingFile.getVirtualFile();

		myStructure.checkNavigateFromSourceBehaviour(psiClass, virtualFile, pane);

		PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" + " -PsiDirectory: showClassMembers\n" + "  -PsiDirectory: src\n" + "   -PsiDirectory: com\n" + "    -PsiDirectory: package1\n"
				+ "     [Class1]\n" + "     Class2\n" + getRootFiles() + " +External Libraries\n", true);

		changeClassTextAndTryToNavigate("class Class11 {}", (PsiJavaFile) containingFile, pane, "-Project\n" + " -PsiDirectory: showClassMembers\n" + "  -PsiDirectory: src\n" + "   -PsiDirectory: " +
				"com\n" + "    -PsiDirectory: package1\n" + "     -Class1.java\n" + "      [Class11]\n" + "     Class2\n" + getRootFiles() + " +External Libraries\n");

		changeClassTextAndTryToNavigate("class Class1 {}", (PsiJavaFile) containingFile, pane, "-Project\n" + " -PsiDirectory: showClassMembers\n" + "  -PsiDirectory: src\n" + "   -PsiDirectory: " +
				"com\n" + "    -PsiDirectory: package1\n" + "     -Class1.java\n" + "      [Class1]\n" + "     Class2\n" + getRootFiles() + " +External Libraries\n");

		doTestMultipleSelection(pane, ((PsiJavaFile) containingFile).getClasses()[0]);
	}

	public void testAutoscrollFromSourceOnOpening() throws Exception
	{
		final PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(getPackageDirectory());
		PsiClass psiClass = classes[0];

		FileEditorManager.getInstance(getProject()).openFile(psiClass.getContainingFile().getVirtualFile(), true);

		ProjectView projectView = ProjectView.getInstance(getProject());

		((ProjectViewImpl) projectView).setAutoscrollFromSource(true, ProjectViewPane.ID);

		ToolWindow toolWindow = ToolWindowManager.getInstance(getProject()).getToolWindow(ToolWindowId.PROJECT_VIEW);

		new ProjectViewToolWindowFactory().createToolWindowContent(getProject(), toolWindow);

		projectView.changeView(ProjectViewPane.ID);

		JComponent component = ((ProjectViewImpl) projectView).getComponent();
		DataContext context = DataManager.getInstance().getDataContext(component);
		PsiElement element = context.getData(PsiElement.KEY);
		assertEquals("Class1.java", ((PsiJavaFile) element).getName());
	}

	private static void doTestMultipleSelection(final AbstractProjectViewPSIPane pane, final PsiClass psiClass)
	{
		JTree tree = pane.getTree();
		int rowCount = tree.getRowCount();
		for(int i = 0; i < rowCount; i++)
		{
			tree.addSelectionRow(i);
		}

		pane.select(psiClass, psiClass.getContainingFile().getVirtualFile(), true);

		assertEquals(10, tree.getSelectionCount());
	}

	private static void changeClassTextAndTryToNavigate(final String newClassString,
			PsiJavaFile psiFile,
			final AbstractProjectViewPSIPane pane,
			final String expected) throws IOException, InterruptedException
	{
		PsiClass psiClass = psiFile.getClasses()[0];
		final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
		final JTree tree = pane.getTree();
		writeToFile(virtualFile, newClassString.getBytes());

		PlatformTestUtil.waitForAlarm(600);

		psiClass = psiFile.getClasses()[0];
		pane.select(psiClass, virtualFile, true);
		PlatformTestUtil.assertTreeEqual(tree, expected, true);
	}

	private static void writeToFile(final VirtualFile virtualFile, final byte[] b) throws IOException
	{
		virtualFile.setBinaryContent(b);
	}


}
