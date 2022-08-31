// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.indexing.impl.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.java.language.impl.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;

import javax.annotation.Nonnull;
import java.util.Collection;

import static java.util.Collections.singletonMap;

public class JavaAutoModuleNameIndex extends ScalarIndexExtension<String>
{
	private static final ID<String, Void> NAME = ID.create("java.auto.module.name");

	private final FileBasedIndex.InputFilter myFilter =
			(project, file) -> file.isDirectory() && file.getParent() == null && "jar".equalsIgnoreCase(file.getExtension()) && JavaModuleNameIndex.descriptorFile(file) == null;

	private final DataIndexer<String, Void, FileContent> myIndexer = data -> singletonMap(LightJavaModule.moduleName(data.getFile()), null);

	@Nonnull
	@Override
	public ID<String, Void> getName()
	{
		return NAME;
	}

	@Override
	public boolean indexDirectories()
	{
		return true;
	}

	@Override
	public int getVersion()
	{
		return 6;
	}

	@Nonnull
	@Override
	public KeyDescriptor<String> getKeyDescriptor()
	{
		return EnumeratorStringDescriptor.INSTANCE;
	}

	@Override
	public boolean dependsOnFileContent()
	{
		return false;
	}

	@Nonnull
	@Override
	public FileBasedIndex.InputFilter getInputFilter()
	{
		return myFilter;
	}

	@Nonnull
	@Override
	public DataIndexer<String, Void, FileContent> getIndexer()
	{
		return myIndexer;
	}

	@Nonnull
	public static Collection<VirtualFile> getFilesByKey(@Nonnull String moduleName, @Nonnull GlobalSearchScope scope)
	{
		return FileBasedIndex.getInstance().getContainingFiles(NAME, moduleName, scope);
	}

	@Nonnull
	public static Collection<String> getAllKeys(@Nonnull Project project)
	{
		return FileBasedIndex.getInstance().getAllKeys(NAME, project);
	}
}