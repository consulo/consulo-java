/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.configurations;

import consulo.execution.CantRunException;
import consulo.module.Module;
import consulo.project.Project;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.ide.impl.idea.openapi.roots.ModuleRootModificationUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.roots.ModuleRootManagerTestCase;
import consulo.java.execution.configurations.OwnJavaParameters;

/**
 * @author nik
 */
public abstract class JavaParametersTest extends ModuleRootManagerTestCase
{
	public void testLibrary() throws Exception
	{
		ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());
		assertClasspath(myModule, OwnJavaParameters.JDK_AND_CLASSES_AND_TESTS, getRtJar(), getJDomJar());
		assertClasspath(myModule, OwnJavaParameters.CLASSES_ONLY, getJDomJar());
		assertClasspath(myModule, OwnJavaParameters.CLASSES_AND_TESTS, getJDomJar());
		assertClasspath(myProject, OwnJavaParameters.JDK_AND_CLASSES_AND_TESTS, getRtJar(), getJDomJar());
	}

	public void testModuleSourcesAndOutput() throws Exception
	{
		addSourceRoot(myModule, false);
		addSourceRoot(myModule, true);
		VirtualFile output = setModuleOutput(myModule, false);
		VirtualFile testOutput = setModuleOutput(myModule, true);

		assertClasspath(myModule, OwnJavaParameters.CLASSES_ONLY, output);
		assertClasspath(myModule, OwnJavaParameters.CLASSES_AND_TESTS, testOutput, output);
		assertClasspath(myModule, OwnJavaParameters.JDK_AND_CLASSES_AND_TESTS, getRtJar(), testOutput, output);
	}

	public void testLibraryScope() throws Exception
	{
		ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary(), DependencyScope.RUNTIME, false);
		ModuleRootModificationUtil.addDependency(myModule, createAsmLibrary(), DependencyScope.TEST, false);

		assertClasspath(myModule, OwnJavaParameters.CLASSES_AND_TESTS, getJDomJar(), getAsmJar());
		assertClasspath(myModule, OwnJavaParameters.CLASSES_ONLY, getJDomJar());
	}

	public void testProvidedScope() throws Exception
	{
		ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary(), DependencyScope.PROVIDED, false);

		assertClasspath(myModule, OwnJavaParameters.CLASSES_AND_TESTS, getJDomJar());
		assertClasspath(myModule, OwnJavaParameters.CLASSES_ONLY);
	}

	public void testModuleDependency() throws Exception
	{
		Module dep = createModule("dep");
		VirtualFile depOutput = setModuleOutput(dep, false);
		VirtualFile depTestOutput = setModuleOutput(dep, true);
		ModuleRootModificationUtil.addDependency(dep, createJDomLibrary());
		ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.COMPILE, false);

		assertClasspath(myModule, OwnJavaParameters.CLASSES_ONLY, depOutput, getJDomJar());
		assertClasspath(myModule, OwnJavaParameters.CLASSES_AND_TESTS, depTestOutput, depOutput, getJDomJar());
	}

	public void testModuleDependencyScope() throws Exception
	{
		Module dep = createModule("dep");
		ModuleRootModificationUtil.addDependency(dep, createJDomLibrary());
		ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.TEST, true);

		assertClasspath(myModule, OwnJavaParameters.CLASSES_ONLY);
		assertClasspath(myModule, OwnJavaParameters.CLASSES_AND_TESTS, getJDomJar());

		assertClasspath(myProject, OwnJavaParameters.CLASSES_ONLY, getJDomJar());
	}

	private static void assertClasspath(Module module, int type, VirtualFile... roots) throws CantRunException
	{
		OwnJavaParameters OwnJavaParameters = new OwnJavaParameters();
		OwnJavaParameters.configureByModule(module, type);
		assertRoots(OwnJavaParameters.getClassPath(), roots);
	}

	private void assertClasspath(Project project, int type, VirtualFile... roots) throws CantRunException
	{
		OwnJavaParameters OwnJavaParameters = new OwnJavaParameters();
		OwnJavaParameters.configureByProject(project, type, getTestProjectJdk());
		assertRoots(OwnJavaParameters.getClassPath(), roots);
	}
}
