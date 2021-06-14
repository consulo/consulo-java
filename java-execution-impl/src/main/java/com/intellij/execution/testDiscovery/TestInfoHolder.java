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
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.vfs.newvfs.persistent.FlushingDaemon;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.*;
import consulo.util.collection.primitive.ints.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

final class TestInfoHolder
{
	final PersistentHashMap<Long, IntList> myMethodQNameToTestNames;
	final PersistentHashMap<Integer, IntObjectMap<IntList>> myTestNameToUsedClassesAndMethodMap;
	final PersistentHashMap<Long, IntList> myTestNameToNearestModule;
	final PersistentStringEnumerator myClassEnumerator;
	final CachingEnumerator<String> myClassEnumeratorCache;
	final PersistentStringEnumerator myMethodEnumerator;
	final CachingEnumerator<String> myMethodEnumeratorCache;
	final PersistentStringEnumerator myTestNameEnumerator;
	final PersistentStringEnumerator myModuleNameEnumerator;
	final List<PersistentEnumeratorDelegate> myConstructedDataFiles = new ArrayList<>(6);

	private ScheduledFuture<?> myFlushingFuture;
	private boolean myDisposed;
	private final Object myLock;

	private static final int VERSION = 4;

	TestInfoHolder(@Nonnull Path basePath, boolean readOnly, Object lock)
	{
		myLock = lock;
		final Path versionFile = getVersionFile(basePath);
		try
		{
			PathKt.createDirectories(basePath);
		}
		catch(IOException e)
		{
			throw new RuntimeException(e);
		}
		final File methodQNameToTestNameFile = basePath.resolve("methodQNameToTestName.data").toFile();
		final File testNameToUsedClassesAndMethodMapFile = basePath.resolve("testToCalledMethodNames.data").toFile();
		final File classNameEnumeratorFile = basePath.resolve("classNameEnumerator.data").toFile();
		final File methodNameEnumeratorFile = basePath.resolve("methodNameEnumerator.data").toFile();
		final File testNameEnumeratorFile = basePath.resolve("testNameEnumerator.data").toFile();
		final File moduleNameEnumeratorFile = basePath.resolve("moduleNameEnumerator.data").toFile();
		final File testNameToNearestModuleFile = basePath.resolve("testNameToNearestModule.data").toFile();

		try
		{
			int version = readVersion(versionFile);
			if(version != VERSION)
			{
				assert !readOnly;
				TestDiscoveryIndex.LOG.info("TestDiscoveryIndex was rewritten due to version change");
				deleteAllIndexDataFiles(methodQNameToTestNameFile, testNameToUsedClassesAndMethodMapFile, classNameEnumeratorFile, methodNameEnumeratorFile, testNameEnumeratorFile,
						moduleNameEnumeratorFile, testNameToNearestModuleFile);

				writeVersion(versionFile);
			}

			PersistentHashMap<Long, IntList> methodQNameToTestNames;
			PersistentHashMap<Integer, IntObjectMap<IntList>> testNameToUsedClassesAndMethodMap;
			PersistentHashMap<Long, IntList> testNameToNearestModule;
			PersistentStringEnumerator classNameEnumerator;
			PersistentStringEnumerator methodNameEnumerator;
			PersistentStringEnumerator testNameEnumerator;
			PersistentStringEnumerator moduleNameEnumerator;

			int iterations = 0;

			while(true)
			{
				++iterations;

				try
				{
					methodQNameToTestNames = new PersistentHashMap<Long, IntList>(methodQNameToTestNameFile, MethodQNameSerializer.INSTANCE, new TestNamesExternalizer())
					{
						@Override
						protected boolean isReadOnly()
						{
							return readOnly;
						}
					};
					myConstructedDataFiles.add(methodQNameToTestNames);

					testNameToUsedClassesAndMethodMap = new PersistentHashMap<Integer, IntObjectMap<IntList>>(testNameToUsedClassesAndMethodMapFile, EnumeratorIntegerDescriptor.INSTANCE,
							new ClassesAndMethodsMapDataExternalizer())
					{
						@Override
						protected boolean isReadOnly()
						{
							return readOnly;
						}
					};
					myConstructedDataFiles.add(testNameToUsedClassesAndMethodMap);

					testNameToNearestModule = new PersistentHashMap<>(testNameToNearestModuleFile, MethodQNameSerializer.INSTANCE, new TestNamesExternalizer());
					myConstructedDataFiles.add(testNameToNearestModule);

					classNameEnumerator = new PersistentStringEnumerator(classNameEnumeratorFile);
					myConstructedDataFiles.add(classNameEnumerator);

					methodNameEnumerator = new PersistentStringEnumerator(methodNameEnumeratorFile);
					myConstructedDataFiles.add(methodNameEnumerator);

					moduleNameEnumerator = new PersistentStringEnumerator(moduleNameEnumeratorFile);
					myConstructedDataFiles.add(moduleNameEnumerator);

					testNameEnumerator = new PersistentStringEnumerator(testNameEnumeratorFile);
					myConstructedDataFiles.add(testNameEnumerator);

					break;
				}
				catch(Throwable throwable)
				{
					TestDiscoveryIndex.LOG.info("TestDiscoveryIndex problem", throwable);
					closeAllConstructedFiles(true);
					myConstructedDataFiles.clear();

					deleteAllIndexDataFiles(methodQNameToTestNameFile, testNameToUsedClassesAndMethodMapFile, classNameEnumeratorFile, methodNameEnumeratorFile, testNameEnumeratorFile,
							moduleNameEnumeratorFile, testNameToNearestModuleFile);
					// try another time
				}

				if(iterations >= 3)
				{
					TestDiscoveryIndex.LOG.error("Unexpected circular initialization problem");
					assert false;
				}
			}

			myMethodQNameToTestNames = methodQNameToTestNames;
			myTestNameToUsedClassesAndMethodMap = testNameToUsedClassesAndMethodMap;
			myTestNameToNearestModule = testNameToNearestModule;
			myClassEnumerator = classNameEnumerator;
			myMethodEnumerator = methodNameEnumerator;
			myTestNameEnumerator = testNameEnumerator;
			myModuleNameEnumerator = moduleNameEnumerator;
			myMethodEnumeratorCache = new CachingEnumerator<>(methodNameEnumerator, EnumeratorStringDescriptor.INSTANCE);
			myClassEnumeratorCache = new CachingEnumerator<>(classNameEnumerator, EnumeratorStringDescriptor.INSTANCE);

			myFlushingFuture = FlushingDaemon.everyFiveSeconds(() ->
			{
				synchronized(myLock)
				{
					if(myDisposed)
					{
						myFlushingFuture.cancel(false);
						return;
					}
					for(PersistentEnumeratorDelegate dataFile : myConstructedDataFiles)
					{
						if(dataFile.isDirty())
						{
							dataFile.force();
						}
					}
					myClassEnumeratorCache.clear();
					myMethodEnumeratorCache.clear();
				}
			});
		}
		catch(IOException ex)
		{
			throw new RuntimeException(ex);
		}
	}

	private void closeAllConstructedFiles(boolean ignoreCloseProblem)
	{
		for(Closeable closeable : myConstructedDataFiles)
		{
			try
			{
				closeable.close();
			}
			catch(Throwable throwable)
			{
				if(!ignoreCloseProblem)
				{
					throw new RuntimeException(throwable);
				}
			}
		}
	}

	private static void deleteAllIndexDataFiles(File... files)
	{
		for(File file : files)
		{
			IOUtil.deleteAllFilesStartingWith(file);
		}
	}

	private static void writeVersion(@Nonnull Path versionFile) throws IOException
	{
		try (final DataOutputStream versionOut = new DataOutputStream(PathKt.outputStream(versionFile)))
		{
			DataInputOutputUtil.writeINT(versionOut, VERSION);
		}
	}

	private static int readVersion(@Nonnull Path versionFile) throws IOException
	{
		InputStream inputStream = PathKt.inputStreamIfExists(versionFile);
		if(inputStream == null)
		{
			return 0;
		}
		try (DataInputStream versionInput = new DataInputStream(inputStream))
		{
			return DataInputOutputUtil.readINT(versionInput);
		}
	}

	void dispose()
	{
		assert Thread.holdsLock(myLock);
		try
		{
			closeAllConstructedFiles(false);
		}
		finally
		{
			myDisposed = true;
		}
	}

	private static final int REMOVED_MARKER = -1;

	void doUpdateFromDiff(final int testNameId,
						  @Nullable IntObjectMap<IntList> classData,
						  @Nullable IntObjectMap<IntList> previousClassData,
						  @Nullable Integer moduleId) throws IOException
	{
		ValueDiff valueDiff = new ValueDiff(classData, previousClassData);

		if(valueDiff.hasRemovedDelta())
		{
			for(int classQName : valueDiff.myRemovedClassData.keys())
			{
				for(int methodName : valueDiff.myRemovedClassData.get(classQName).toArray())
				{
					myMethodQNameToTestNames.appendData(createKey(classQName, methodName), dataOutput ->
					{
						DataInputOutputUtil.writeINT(dataOutput, REMOVED_MARKER);
						DataInputOutputUtil.writeINT(dataOutput, testNameId);
					});
				}
			}
		}

		if(valueDiff.hasAddedDelta())
		{
			for(int classQName : valueDiff.myAddedOrChangedClassData.keys())
			{
				for(int methodName : valueDiff.myAddedOrChangedClassData.get(classQName).toArray())
				{
					myMethodQNameToTestNames.appendData(createKey(classQName, methodName), dataOutput -> DataInputOutputUtil.writeINT(dataOutput, testNameId));
					if(moduleId != null)
					{
						myTestNameToNearestModule.appendData(createKey(classQName, methodName), dataOutput -> DataInputOutputUtil.writeINT(dataOutput, moduleId));
					}
				}
			}
		}

		if((valueDiff.hasAddedDelta() || valueDiff.hasRemovedDelta()))
		{
			if(classData != null)
			{
				myTestNameToUsedClassesAndMethodMap.put(testNameId, classData);
			}
			else
			{
				myTestNameToUsedClassesAndMethodMap.remove(testNameId);
			}
		}
	}

	public boolean isDisposed()
	{
		return myDisposed;
	}

	public static boolean isValidPath(@Nonnull Path path)
	{
		try
		{
			return readVersion(getVersionFile(path)) == VERSION;
		}
		catch(IOException ex)
		{
			return false;
		}
	}

	private static class TestNamesExternalizer implements DataExternalizer<IntList>
	{
		public void save(@Nonnull DataOutput dataOutput, IntList testNameIds) throws IOException
		{
			for(int testNameId : testNameIds.toArray())
			{
				DataInputOutputUtil.writeINT(dataOutput, testNameId);
			}
		}

		public IntList read(@Nonnull DataInput dataInput) throws IOException
		{
			IntSet result = IntSets.newHashSet();

			while(((InputStream) dataInput).available() > 0)
			{
				int id = DataInputOutputUtil.readINT(dataInput);
				if(REMOVED_MARKER == id)
				{
					id = DataInputOutputUtil.readINT(dataInput);
					if(!result.remove(id))
					{
						result.add(-id);
					}
				}
				else
				{
					result.add(id);
				}
			}

			return IntLists.newArrayList(result.toArray());
		}
	}

	private static class ClassesAndMethodsMapDataExternalizer implements DataExternalizer<IntObjectMap<IntList>>
	{
		public void save(@Nonnull final DataOutput dataOutput, IntObjectMap<IntList> classAndMethodsMap) throws IOException
		{
			DataInputOutputUtil.writeINT(dataOutput, classAndMethodsMap.size());
			final int[] classNameIds = classAndMethodsMap.keys();
			Arrays.sort(classNameIds);

			int prevClassNameId = 0;
			for(int classNameId : classNameIds)
			{
				DataInputOutputUtil.writeINT(dataOutput, classNameId - prevClassNameId);
				IntList value = classAndMethodsMap.get(classNameId);
				DataInputOutputUtil.writeINT(dataOutput, value.size());

				final int[] methodNameIds = value.toArray();
				Arrays.sort(methodNameIds);
				int prevMethodNameId = 0;
				for(int methodNameId : methodNameIds)
				{
					DataInputOutputUtil.writeINT(dataOutput, methodNameId - prevMethodNameId);
					prevMethodNameId = methodNameId;
				}
				prevClassNameId = classNameId;
			}
		}

		public IntObjectMap<IntList> read(@Nonnull DataInput dataInput) throws IOException
		{
			int numberOfClasses = DataInputOutputUtil.readINT(dataInput);
			IntObjectMap<IntList> result = IntMaps.newIntObjectHashMap();
			int prevClassNameId = 0;

			while(numberOfClasses-- > 0)
			{
				int classNameId = DataInputOutputUtil.readINT(dataInput) + prevClassNameId;
				int numberOfMethods = DataInputOutputUtil.readINT(dataInput);
				IntList methodNameIds = IntLists.newArrayList(numberOfMethods);

				int prevMethodNameId = 0;
				while(numberOfMethods-- > 0)
				{
					final int methodNameId = DataInputOutputUtil.readINT(dataInput) + prevMethodNameId;
					methodNameIds.add(methodNameId);
					prevMethodNameId = methodNameId;
				}

				result.put(classNameId, methodNameIds);
				prevClassNameId = classNameId;
			}
			return result;
		}
	}

	private static class MethodQNameSerializer implements KeyDescriptor<Long>
	{
		public static final MethodQNameSerializer INSTANCE = new MethodQNameSerializer();

		@Override
		public void save(@Nonnull DataOutput out, Long value) throws IOException
		{
			out.writeLong(value);
		}

		@Override
		public Long read(@Nonnull DataInput in) throws IOException
		{
			return in.readLong();
		}
	}

	@Nonnull
	static Path getVersionFile(Path path)
	{
		return path.resolve("index.version");
	}

	static long createKey(int classQName, int methodName)
	{
		return ((long) classQName << 32) | methodName;
	}

	static class ValueDiff
	{
		final IntObjectMap<IntList> myAddedOrChangedClassData;
		final IntObjectMap<IntList> myRemovedClassData;

		ValueDiff(@Nullable IntObjectMap<IntList> classData, @Nullable IntObjectMap<IntList> previousClassData)
		{
			IntObjectMap<IntList> addedOrChangedClassData = classData;
			IntObjectMap<IntList> removedClassData = previousClassData;

			if(previousClassData != null && !previousClassData.isEmpty())
			{
				removedClassData = IntMaps.newIntObjectHashMap();
				addedOrChangedClassData = IntMaps.newIntObjectHashMap();

				if(classData != null)
				{
					for(int classQName : classData.keys())
					{
						IntList currentMethods = classData.get(classQName);
						IntList previousMethods = previousClassData.get(classQName);

						if(previousMethods == null)
						{
							addedOrChangedClassData.put(classQName, currentMethods);
							continue;
						}

						final int[] previousMethodIds = previousMethods.toArray();
						IntSet previousMethodsSet = IntSets.newHashSet(previousMethodIds);
						final int[] currentMethodIds = currentMethods.toArray();
						IntSet currentMethodsSet = IntSets.newHashSet(currentMethodIds);
						currentMethodsSet.removeAll(previousMethodIds);
						previousMethodsSet.removeAll(currentMethodIds);

						if(!currentMethodsSet.isEmpty())
						{
							addedOrChangedClassData.put(classQName, IntLists.newArrayList(currentMethodsSet.toArray()));
						}
						if(!previousMethodsSet.isEmpty())
						{
							removedClassData.put(classQName, IntLists.newArrayList(previousMethodsSet.toArray()));
						}
					}
				}
				if(classData != null)
				{
					for(int classQName : previousClassData.keys())
					{
						if(classData.containsKey(classQName))
						{
							continue;
						}

						IntList previousMethods = previousClassData.get(classQName);
						removedClassData.put(classQName, previousMethods);
					}
				}
			}

			myAddedOrChangedClassData = addedOrChangedClassData;
			myRemovedClassData = removedClassData;
		}

		public boolean hasRemovedDelta()
		{
			return myRemovedClassData != null && !myRemovedClassData.isEmpty();
		}

		public boolean hasAddedDelta()
		{
			return myAddedOrChangedClassData != null && !myAddedOrChangedClassData.isEmpty();
		}
	}
}
