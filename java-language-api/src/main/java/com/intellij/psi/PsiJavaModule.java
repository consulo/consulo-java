/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.util.containers.ContainerUtil;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Represents a Java module declaration.
 *
 * @since 2016.3
 */
public interface PsiJavaModule extends NavigatablePsiElement, PsiNameIdentifierOwner, PsiModifierListOwner, PsiJavaDocumentedElement
{
	String MODULE_INFO_CLASS = "module-info";
	String MODULE_INFO_FILE = MODULE_INFO_CLASS + ".java";
	String MODULE_INFO_CLS_FILE = MODULE_INFO_CLASS + ".class";
	String JAVA_BASE = "java.base";
	String AUTO_MODULE_NAME = "Automatic-Module-Name";

	/* See http://openjdk.java.net/jeps/261#Class-loaders, "Class loaders" */
	Set<String> UPGRADEABLE = ContainerUtil.immutableSet(
			"java.activation", "java.compiler", "java.corba", "java.transaction", "java.xml.bind", "java.xml.ws", "java.xml.ws.annotation",
			"jdk.internal.vm.compiler", "jdk.xml.bind", "jdk.xml.ws");

	@Override
	@Nonnull
	PsiJavaModuleReferenceElement getNameIdentifier();

	@Override
	@Nonnull
	String getName();

	@Nonnull
	Iterable<PsiRequiresStatement> getRequires();

	@Nonnull
	Iterable<PsiPackageAccessibilityStatement> getExports();

	@Nonnull
	Iterable<PsiPackageAccessibilityStatement> getOpens();

	@Nonnull
	Iterable<PsiUsesStatement> getUses();

	@Nonnull
	Iterable<PsiProvidesStatement> getProvides();
}