package com.intellij.navigation;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.java.indexing.impl.ClassImplementationsSearch;
import com.intellij.java.indexing.impl.MethodImplementationsSearch;
import consulo.module.Module;
import consulo.module.ModuleManager;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.scope.GlobalSearchScope;

import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * @author cdr
 */
public abstract class GotoImplementationTest extends CodeInsightTestCase
{

	private static Collection<PsiClass> getClassImplementations(final PsiClass psiClass)
	{
		List<PsiClass> list = new ArrayList<>();
		ClassImplementationsSearch.processImplementations(psiClass, element ->
		{
			if(element instanceof PsiClass)
			{
				list.add((PsiClass) element);
			}
			return true;
		}, psiClass.getUseScope());

		return list;
	}

//	@Override
//	protected void setUpProject() throws Exception
//	{
//		final String root = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/navigation/alexProject";
//		VirtualFile vfsRoot = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(root));
//
//		VirtualFile projectFile = vfsRoot.findChild("test.ipr");
//		myProject = ProjectManagerEx.getInstanceEx().loadProject(projectFile.getPath());
//
//		simulateProjectOpen();
//		ProjectManagerEx.getInstanceEx().openTestProject(myProject);
//	}

	public void test() throws Exception
	{

		ModuleManager moduleManager = ModuleManager.getInstance(getProject());
		Module[] modules = moduleManager.getModules();
		assertEquals(3, modules.length);

		Module module1 = moduleManager.findModuleByName("test1");
		Module module2 = moduleManager.findModuleByName("test2");
		Module module3 = moduleManager.findModuleByName("test3");
		PsiClass test1 = myJavaFacade.findClass("com.test.TestI", GlobalSearchScope.moduleScope(module1));
		PsiClass test2 = myJavaFacade.findClass("com.test.TestI", GlobalSearchScope.moduleScope(module2));
		PsiClass test3 = myJavaFacade.findClass("com.test.TestI", GlobalSearchScope.moduleScope(module3));
		HashSet<PsiClass> expectedImpls1 = new HashSet<PsiClass>(Arrays.asList(myJavaFacade.findClass("com.test.TestIImpl1", GlobalSearchScope.moduleScope(module1)), myJavaFacade.findClass("com" +
				".test" + ".TestIImpl2", GlobalSearchScope.moduleScope(module1))));
		assertEquals(expectedImpls1, new HashSet<PsiClass>(getClassImplementations(test1)));

		PsiMethod psiMethod = test1.findMethodsByName("test", false)[0];
		Set<PsiMethod> expectedMethodImpl1 = new HashSet<PsiMethod>(Arrays.asList(myJavaFacade.findClass("com.test.TestIImpl1", GlobalSearchScope.moduleScope(module1)).findMethodsByName("test",
				false)[0], myJavaFacade.findClass("com.test.TestIImpl2", GlobalSearchScope.moduleScope(module1)).findMethodsByName("test", false)[0]));
		assertEquals(expectedMethodImpl1, new HashSet<PsiMethod>(Arrays.asList(MethodImplementationsSearch.getMethodImplementations(psiMethod, psiMethod.getUseScope()))));

		HashSet<PsiClass> expectedImpls2 = new HashSet<PsiClass>(Arrays.asList(myJavaFacade.findClass("com.test.TestIImpl1", GlobalSearchScope.moduleScope(module2)), myJavaFacade.findClass("com" +
				".test" + ".TestIImpl3", GlobalSearchScope.moduleScope(module2))));
		assertEquals(expectedImpls2, new HashSet<PsiClass>(getClassImplementations(test2)));

		HashSet<PsiClass> expectedImpls3 = new HashSet<PsiClass>(Arrays.asList(myJavaFacade.findClass("com.test.TestIImpl1", GlobalSearchScope.moduleScope(module3))));
		assertEquals(expectedImpls3, new HashSet<PsiClass>(getClassImplementations(test3)));

	}

}
