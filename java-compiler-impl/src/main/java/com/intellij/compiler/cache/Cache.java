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
package com.intellij.compiler.cache;

import com.intellij.compiler.classParsing.*;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.make.CacheUtils;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.indexing.impl.ChangeTrackingValueContainer;
import com.intellij.util.indexing.impl.MapIndexStorage;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import consulo.logging.Logger;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 * Date: Aug 8, 2003
 * Time: 7:03:56 PM
 */
public class Cache
{
	private static class MyMapIndexStorage extends MapIndexStorage<Integer, ClassInfo>
	{
		protected MyMapIndexStorage(@Nonnull File storageFile, @Nonnull KeyDescriptor<Integer> keyDescriptor, int cacheSize) throws IOException
		{
			super(storageFile, keyDescriptor, MyDataExternalizer.INSTANCE, cacheSize, true);
		}

		@Override
		protected void checkCanceled()
		{
			ProgressManager.checkCanceled();
		}
	}

	private static class MyDataExternalizer implements DataExternalizer<ClassInfo>
	{
		private static final MyDataExternalizer INSTANCE = new MyDataExternalizer();

		@Override
		public void save(DataOutput out, ClassInfo value) throws IOException
		{
			value.save(out);
		}

		@Override
		public ClassInfo read(DataInput in) throws IOException
		{
			return new ClassInfo(in);
		}
	}

	private static final Logger LOG = Logger.getInstance(Cache.class);
	public static final int UNKNOWN = -1;

	private final MyMapIndexStorage myQNameToClassInfoMap;

	private final BackwardDependenciesStorage myDependencies;
	private final CompilerDependencyStorage<Integer> myQNameToReferencedClassesMap;
	private final CompilerDependencyStorage<Integer> myQNameToSubclassesMap;
	private final PersistentHashMap<Integer, Boolean> myRemoteQNames;
	private final String myStorePath;

	public Cache(@NonNls final String storePath, final int cacheSize) throws IOException
	{
		myStorePath = storePath;
		new File(storePath).mkdirs();

		myQNameToClassInfoMap = new MyMapIndexStorage(getOrCreateFile("classes_cache"), EnumeratorIntegerDescriptor.INSTANCE, cacheSize * 2);

		myDependencies = new BackwardDependenciesStorage(getOrCreateFile("bdeps"), cacheSize);
		myQNameToReferencedClassesMap = new CompilerDependencyStorage<>(getOrCreateFile("fdeps"), EnumeratorIntegerDescriptor.INSTANCE, cacheSize);
		myQNameToSubclassesMap = new CompilerDependencyStorage<>(getOrCreateFile("subclasses"), EnumeratorIntegerDescriptor.INSTANCE, cacheSize);

		myRemoteQNames = new PersistentHashMap<>(getOrCreateFile("remote"), EnumeratorIntegerDescriptor.INSTANCE, new DataExternalizer<Boolean>()
		{
			@Override
			public void save(DataOutput out, Boolean value) throws IOException
			{
				out.writeBoolean(value.booleanValue());
			}

			@Override
			public Boolean read(DataInput in) throws IOException
			{
				return in.readBoolean();
			}
		}, cacheSize);
	}

	private File getOrCreateFile(final String fileName)
	{
		final File file = new File(myStorePath, fileName);
		FileUtil.createIfDoesntExist(file);
		return file;
	}

	public void dispose() throws CacheCorruptedException
	{
		CacheCorruptedException ex = null;
		try
		{
			myQNameToClassInfoMap.close();
		}
		catch(Throwable e)
		{
			LOG.info(e);
			ex = new CacheCorruptedException(e);
		}
		try
		{
			myRemoteQNames.close();
		}
		catch(IOException e)
		{
			LOG.info(e);
			if(ex != null)
			{
				ex = new CacheCorruptedException(e);
			}
		}

		myQNameToReferencedClassesMap.dispose();
		myDependencies.dispose();
		myQNameToSubclassesMap.dispose();

		if(ex != null)
		{
			throw ex;
		}
	}

	public int importClassInfo(ClassFileReader reader, SymbolTable symbolTable) throws ClsFormatException, CacheCorruptedException
	{
		try
		{
			final ClassInfo classInfo = new ClassInfo(reader, symbolTable);
			myQNameToClassInfoMap.addValue(classInfo.getQualifiedName(), 0, classInfo);
			return classInfo.getQualifiedName();
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public void importClassInfo(Cache fromCache, final int qName) throws CacheCorruptedException
	{
		try
		{
			final ClassInfo classInfo = fromCache.getClassInfo(qName);
			if(classInfo != null)
			{
				final ClassInfo clone = classInfo.clone();
				clone.clearReferences();
				myQNameToClassInfoMap.addValue(classInfo.getQualifiedName(), 0, classInfo);
			}
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public AnnotationConstantValue[] getRuntimeVisibleAnnotations(int classId) throws CacheCorruptedException
	{
		final ClassInfo classInfo = getClassInfo(classId);
		return classInfo != null ? classInfo.getRuntimeVisibleAnnotations() : AnnotationConstantValue.EMPTY_ARRAY;
	}

	public AnnotationConstantValue[] getRuntimeInvisibleAnnotations(int classId) throws CacheCorruptedException
	{
		try
		{
			final ClassInfo classInfo = getClassInfo(classId);
			return classInfo != null ? classInfo.getRuntimeInvisibleAnnotations() : AnnotationConstantValue.EMPTY_ARRAY;
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public int[] getSubclasses(int classId) throws CacheCorruptedException
	{
		try
		{
			return myQNameToSubclassesMap.getValues(classId);
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public void addSubclass(int classId, int subclassQName) throws CacheCorruptedException
	{
		try
		{
			myQNameToSubclassesMap.addValue(classId, subclassQName);
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public void removeSubclass(int classId, int subclassQName) throws CacheCorruptedException
	{
		try
		{
			myQNameToSubclassesMap.removeValue(classId, subclassQName);
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public int[] getReferencedClasses(int qName) throws CacheCorruptedException
	{
		try
		{
			return myQNameToReferencedClassesMap.getValues(qName);
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public void clearReferencedClasses(int qName) throws CacheCorruptedException
	{
		try
		{
			myQNameToReferencedClassesMap.remove(qName);
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public Collection<ReferenceInfo> getReferences(int classId) throws CacheCorruptedException
	{
		try
		{
			final ClassInfo classInfo = getClassInfo(classId);
			return classInfo != null ? Arrays.asList(classInfo.getReferences()) : Collections.<ReferenceInfo>emptyList();
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public String getSourceFileName(int classId) throws CacheCorruptedException
	{
		final ClassInfo classInfo = getClassInfo(classId);
		return classInfo != null ? classInfo.getSourceFileName() : "";
	}

	public boolean isRemote(int classId) throws CacheCorruptedException
	{
		try
		{
			return myRemoteQNames.containsMapping(classId);
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public void setRemote(int classId, boolean remote) throws CacheCorruptedException
	{
		try
		{
			if(remote)
			{
				myRemoteQNames.put(classId, Boolean.TRUE);
			}
			else
			{
				myRemoteQNames.remove(classId);
			}
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public int getSuperQualifiedName(int classId) throws CacheCorruptedException
	{
		final ClassInfo classInfo = getClassInfo(classId);
		return classInfo != null ? classInfo.getSuperQualifiedName() : UNKNOWN;
	}

	public String getPath(int classId) throws CacheCorruptedException
	{
		final ClassInfo classInfo = getClassInfo(classId);
		return classInfo != null ? classInfo.getPath() : "";
	}

	public void setPath(int classId, String path) throws CacheCorruptedException
	{
		ClassInfo classInfo = getClassInfo(classId);
		if(classInfo != null)
		{
			classInfo.setPath(path);

			removeClassInfo(classInfo);

			try
			{
				myQNameToClassInfoMap.addValue(classId, 0, classInfo);
			}
			catch(Throwable e)
			{
				throw new CacheCorruptedException(e);
			}
		}
	}

	public int getGenericSignature(int classId) throws CacheCorruptedException
	{
		final ClassInfo classInfo = getClassInfo(classId);
		return classInfo != null ? classInfo.getGenericSignature() : UNKNOWN;
	}

	public boolean containsClass(int qName) throws CacheCorruptedException
	{
		return getClassInfo(qName) != null;
	}

	public int[] getSuperInterfaces(int classId) throws CacheCorruptedException
	{
		final ClassInfo classInfo = getClassInfo(classId);
		return classInfo != null ? classInfo.getSuperInterfaces() : ArrayUtil.EMPTY_INT_ARRAY;
	}

	public int getFlags(int classId) throws CacheCorruptedException
	{
		final ClassInfo classInfo = getClassInfo(classId);
		return classInfo != null ? classInfo.getFlags() : UNKNOWN;
	}

	public FieldInfo[] getFields(int qName) throws CacheCorruptedException
	{
		final ClassInfo classInfo = getClassInfo(qName);
		return classInfo != null ? classInfo.getFields() : FieldInfo.EMPTY_ARRAY;
	}

	@Nullable
	public FieldInfo findField(final int classDeclarationId, final int name, final int descriptor) throws CacheCorruptedException
	{
		try
		{
			for(FieldInfo fieldInfo : getFields(classDeclarationId))
			{
				if(fieldInfo.getName() == name && fieldInfo.getDescriptor() == descriptor)
				{
					return fieldInfo;
				}
			}
			return null;
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	@Nullable
	public FieldInfo findFieldByName(final int classDeclarationId, final int name) throws CacheCorruptedException
	{
		try
		{
			for(FieldInfo fieldInfo : getFields(classDeclarationId))
			{
				if(fieldInfo.getName() == name)
				{
					return fieldInfo;
				}
			}
			return null;
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public MethodInfo[] getMethods(int classQName) throws CacheCorruptedException
	{
		final ClassInfo classInfo = getClassInfo(classQName);
		return classInfo != null ? classInfo.getMethods() : MethodInfo.EMPTY_ARRAY;
	}

	@Nullable
	public MethodInfo findMethod(final int classQName, final int name, final int descriptor) throws CacheCorruptedException
	{
		try
		{
			for(MethodInfo methodInfo : getMethods(classQName))
			{
				if(methodInfo.getName() == name && methodInfo.getDescriptor() == descriptor)
				{
					return methodInfo;
				}
			}
			return null;
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public List<MethodInfo> findMethodsByName(final int classDeclarationId, final int name) throws CacheCorruptedException
	{
		try
		{
			final List<MethodInfo> methods = new ArrayList<MethodInfo>();
			for(MethodInfo methodInfo : getMethods(classDeclarationId))
			{
				if(methodInfo.getName() == name)
				{
					methods.add(methodInfo);
				}
			}
			return methods;
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	@Nullable
	public MethodInfo findMethodsBySignature(final int classDeclarationId, final String signature, SymbolTable symbolTable) throws CacheCorruptedException
	{
		try
		{
			for(MethodInfo methodInfo : getMethods(classDeclarationId))
			{
				if(signature.equals(CacheUtils.getMethodSignature(symbolTable.getSymbol(methodInfo.getName()), symbolTable.getSymbol(methodInfo.getDescriptor()))))
				{
					return methodInfo;
				}
			}
			return null;
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public void addClassReferencer(int qName, int referencerQName) throws CacheCorruptedException
	{
		try
		{
			if(qName == referencerQName)
			{
				return; // do not log self-dependencies
			}
			if(containsClass(qName))
			{
				myDependencies.addClassReferencer(qName, referencerQName);
				myQNameToReferencedClassesMap.addValue(referencerQName, qName);
			}
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public void removeClassReferencer(int qName, int referencerQName) throws CacheCorruptedException
	{
		try
		{
			myDependencies.removeReferencer(qName, referencerQName);
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public void addFieldReferencer(int qName, int fieldName, int referencerQName) throws CacheCorruptedException
	{
		try
		{
			if(qName != referencerQName && containsClass(qName))
			{
				myDependencies.addFieldReferencer(qName, referencerQName, fieldName);
				myQNameToReferencedClassesMap.addValue(referencerQName, qName);
			}
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public void addMethodReferencer(int qName, int methodName, int methodDescriptor, int referencerQName) throws CacheCorruptedException
	{
		try
		{
			if(qName != referencerQName && containsClass(qName))
			{
				myDependencies.addMethodReferencer(qName, referencerQName, methodName, methodDescriptor);
				myQNameToReferencedClassesMap.addValue(referencerQName, qName);
			}
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	/**
	 * @NotNull
	 */
	public Dependency[] getBackDependencies(final int classQName) throws CacheCorruptedException
	{
		return myDependencies.getDependencies(classQName);
	}

	public void wipe()
	{
		try
		{
			dispose();
		}
		catch(CacheCorruptedException ignored)
		{
		}
		finally
		{
			final File[] files = new File(myStorePath).listFiles();
			if(files != null)
			{
				for(File file : files)
				{
					if(!file.isDirectory())
					{
						FileUtil.delete(file);
					}
				}
			}
		}
	}

	@Nullable
	private ClassInfo getClassInfo(int qName) throws CacheCorruptedException
	{
		try
		{
			ValueContainer<ClassInfo> data = myQNameToClassInfoMap.read(qName);

			ValueContainer.ValueIterator<ClassInfo> valueIterator = data.getValueIterator();

			if(valueIterator.hasNext())
			{
				return valueIterator.next();
			}
			return null;
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	public void removeClass(final int qName) throws CacheCorruptedException
	{
		try
		{
			final ClassInfo classInfo = getClassInfo(qName);
			if(classInfo == null)
			{
				return;
			}

			myDependencies.remove(qName);

			removeClassInfo(classInfo);

			myQNameToReferencedClassesMap.remove(qName);
			myQNameToSubclassesMap.remove(qName);
			myRemoteQNames.remove(qName);
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	private void removeClassInfo(ClassInfo classInfo) throws CacheCorruptedException
	{
		try
		{
			ChangeTrackingValueContainer<ClassInfo> container = myQNameToClassInfoMap.read(classInfo.getQualifiedName());

			if(container.size() != 0)
			{
				container.removeAssociatedValue(0);
			}
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}
}
