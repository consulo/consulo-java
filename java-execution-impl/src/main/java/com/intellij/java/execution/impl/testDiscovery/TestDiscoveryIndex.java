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
package com.intellij.java.execution.impl.testDiscovery;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.ApplicationManager;
import consulo.disposer.Disposable;
import consulo.index.io.data.DataInputOutputUtil;
import consulo.index.io.data.IOUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.startup.StartupManager;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;
import consulo.util.io.PathKt;
import consulo.util.lang.function.ThrowableFunction;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Maxim.Mossienko on 7/9/2015.
 */
@Singleton
@ServiceAPI(value = ComponentScope.PROJECT, lazy = false)
@ServiceImpl
public class TestDiscoveryIndex implements Disposable {
  static final Logger LOG = Logger.getInstance(TestDiscoveryIndex.class);

  //private volatile TestInfoHolder mySystemHolder;
  private TestDataController myLocalTestRunDataController;
  private TestDataController myRemoteTestRunDataController;

  @Inject
  public TestDiscoveryIndex(Project project) {
    this(project, TestDiscoveryExtension.baseTestDiscoveryPathForProject(project));
  }

  public TestDiscoveryIndex(final Project project, @Nonnull Path basePath) {
    if (project.isDefault()) {
      return;
    }

    myLocalTestRunDataController = new TestDataController(basePath, false);
    myRemoteTestRunDataController = new TestDataController(null, true);

    if (Files.exists(basePath)) {
      StartupManager.getInstance(project).registerPostStartupActivity(() -> ApplicationManager.getApplication().executeOnPooledThread(() ->
                                                                                                                                      {
                                                                                                                                        myLocalTestRunDataController
                                                                                                                                          .getHolder(); // proactively init with maybe io costly compact
                                                                                                                                      }));
    }

    //{
    //  setRemoteTestRunDataPath("C:\\ultimate\\system\\testDiscovery\\145.save");
    //}
  }

  public boolean hasTestTrace(@Nonnull String testName) throws IOException {
    Boolean result = myLocalTestRunDataController.withTestDataHolder(localHolder ->
                                                                     {          // todo: remote run data
                                                                       final int testNameId =
                                                                         localHolder.myTestNameEnumerator.tryEnumerate(testName);
                                                                       if (testNameId == 0) {
                                                                         return myRemoteTestRunDataController.withTestDataHolder(
                                                                           remoteHolder ->
                                                                           {
                                                                             final int testNameId1 =
                                                                               remoteHolder.myTestNameEnumerator.tryEnumerate(testName);
                                                                             return testNameId1 != 0 && remoteHolder.myTestNameToUsedClassesAndMethodMap
                                                                               .get(testNameId1) != null;
                                                                           }) != null;
                                                                       }
                                                                       return localHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId) != null;
                                                                     });
    return result == Boolean.TRUE;
  }

  public void removeTestTrace(@Nonnull String testName) throws IOException {
    myLocalTestRunDataController.withTestDataHolder(localHolder ->
                                                    {
                                                      final int testNameId =
                                                        localHolder.myTestNameEnumerator.tryEnumerate(testName);  // todo remove remote data isn't possible
                                                      if (testNameId != 0) {
                                                        localHolder.doUpdateFromDiff(testNameId,
                                                                                     null,
                                                                                     localHolder.myTestNameToUsedClassesAndMethodMap.get(
                                                                                       testNameId),
                                                                                     null);
                                                      }
                                                      return null;
                                                    });
  }

  public void setRemoteTestRunDataPath(@Nonnull Path path) {
    if (!TestInfoHolder.isValidPath(path)) {
      path = null;
    }
    myRemoteTestRunDataController.init(path);
    // todo: should we remove our local run data ?
  }

  public Collection<String> getTestsByMethodName(@Nonnull String classFQName, @Nonnull String methodName) throws IOException {
    return myLocalTestRunDataController.withTestDataHolder(new ThrowableFunction<TestInfoHolder, Collection<String>, IOException>() {
      @Override
      public Collection<String> apply(TestInfoHolder localHolder) throws IOException {
        IntList remoteList =
          myRemoteTestRunDataController.withTestDataHolder(remoteHolder -> remoteHolder.myMethodQNameToTestNames.get(TestInfoHolder.createKey(
            remoteHolder
              .myClassEnumerator.enumerate(classFQName),
            remoteHolder.myMethodEnumerator.enumerate(methodName))));

        final IntList localList =
          localHolder.myMethodQNameToTestNames.get(TestInfoHolder.createKey(localHolder.myClassEnumerator.enumerate(classFQName),
                                                                            localHolder.myMethodEnumerator
                                                                              .enumerate(methodName)));

        if (remoteList == null) {
          return testIdsToTestNames(localList, localHolder);
        }

        Collection<String> testsFromRemote =
          myRemoteTestRunDataController.withTestDataHolder(remoteHolder -> testIdsToTestNames(remoteList, remoteHolder));

        if (localList == null) {
          return testsFromRemote;
        }
        Set<String> setOfStrings = new HashSet<>(testsFromRemote);

        for (int testNameId : localList.toArray()) {
          if (testNameId < 0) {
            setOfStrings.remove(localHolder.myTestNameEnumerator.valueOf(-testNameId));
            continue;
          }
          setOfStrings.add(localHolder.myTestNameEnumerator.valueOf(testNameId));
        }

        return setOfStrings;
      }

      private Collection<String> testIdsToTestNames(IntList localList, TestInfoHolder localHolder) throws IOException {
        if (localList == null) {
          return Collections.emptyList();
        }

        final ArrayList<String> result = new ArrayList<>(localList.size());
        for (int testNameId : localList.toArray()) {
          if (testNameId < 0) {
            int a = 1;
            continue;
          }
          result.add(localHolder.myTestNameEnumerator.valueOf(testNameId));
        }
        return result;
      }
    });
  }


  public Collection<String> getTestModulesByMethodName(@Nonnull String classFQName,
                                                       @Nonnull String methodName,
                                                       String prefix) throws IOException {
    return myLocalTestRunDataController.withTestDataHolder(new ThrowableFunction<TestInfoHolder, Collection<String>, IOException>() {
      @Override
      public Collection<String> apply(TestInfoHolder localHolder) throws IOException {
        List<String> modules = getTestModules(localHolder);
        List<String> modulesFromRemote = myRemoteTestRunDataController.withTestDataHolder(this::getTestModules);
        Set<String> modulesSet = new HashSet<>(modules);
        if (modulesFromRemote != null) {
          modulesSet.addAll(modulesFromRemote);
        }
        return modulesSet;
      }

      private List<String> getTestModules(TestInfoHolder holder) throws IOException {
        // todo merging with remote
        final IntList list = holder.myTestNameToNearestModule.get(TestInfoHolder.createKey(holder.myClassEnumerator.enumerate(classFQName),
                                                                                           holder.myMethodEnumerator.enumerate
                                                                                             (methodName)));
        if (list == null) {
          return Collections.emptyList();
        }
        final ArrayList<String> result = new ArrayList<>(list.size());
        for (int moduleNameId : list.toArray()) {
          final String moduleNameWithPrefix = holder.myModuleNameEnumerator.valueOf(moduleNameId);
          if (moduleNameWithPrefix != null && moduleNameWithPrefix.startsWith(prefix)) {
            result.add(moduleNameWithPrefix.substring(prefix.length()));
          }
        }
        return result;
      }
    });
  }

  static class TestDataController {
    private final Object myLock = new Object();
    private Path myBasePath;
    private final boolean myReadOnly;
    private volatile TestInfoHolder myHolder;

    TestDataController(Path basePath, boolean readonly) {
      myReadOnly = readonly;
      init(basePath);
    }

    void init(Path basePath) {
      if (myHolder != null) {
        dispose();
      }

      synchronized (myLock) {
        myBasePath = basePath;
      }
    }

    private TestInfoHolder getHolder() {
      TestInfoHolder holder = myHolder;

      if (holder == null) {
        synchronized (myLock) {
          holder = myHolder;
          if (holder == null && myBasePath != null) {
            myHolder = holder = new TestInfoHolder(myBasePath, myReadOnly, myLock);
          }
        }
      }
      return holder;
    }

    private void dispose() {
      synchronized (myLock) {
        TestInfoHolder holder = myHolder;
        if (holder != null) {
          holder.dispose();
          myHolder = null;
        }
      }
    }

    private void thingsWentWrongLetsReinitialize(@Nullable TestInfoHolder holder, Throwable throwable) throws IOException {
      LOG.error("Unexpected problem", throwable);
      if (holder != null) {
        holder.dispose();
      }
      PathKt.delete(TestInfoHolder.getVersionFile(myBasePath));

      myHolder = null;
      if (throwable instanceof IOException) {
        throw (IOException)throwable;
      }
    }

    public <R> R withTestDataHolder(ThrowableFunction<TestInfoHolder, R, IOException> action) throws IOException {
      synchronized (myLock) {
        TestInfoHolder holder = getHolder();
        if (holder == null || holder.isDisposed()) {
          return null;
        }
        try {
          return action.apply(holder);
        }
        catch (Throwable throwable) {
          if (!myReadOnly) {
            thingsWentWrongLetsReinitialize(holder, throwable);
          }
          else {
            LOG.error(throwable);
          }
        }
        return null;
      }
    }
  }

  public static TestDiscoveryIndex getInstance(Project project) {
    return project.getComponent(TestDiscoveryIndex.class);
  }

  @Override
  public void dispose() {
    if (myLocalTestRunDataController != null) {
      myLocalTestRunDataController.dispose();
    }

    if (myRemoteTestRunDataController != null) {
      myRemoteTestRunDataController.dispose();
    }
  }

  public void updateFromTestTrace(@Nonnull File file,
                                  @Nullable final String moduleName,
                                  @Nonnull final String frameworkPrefix) throws IOException {
    int fileNameDotIndex = file.getName().lastIndexOf('.');
    final String testName = fileNameDotIndex != -1 ? file.getName().substring(0, fileNameDotIndex) : file.getName();
    doUpdateFromTestTrace(file, testName, moduleName != null ? frameworkPrefix + moduleName : null);
  }

  private void doUpdateFromTestTrace(File file, final String testName, @Nullable final String moduleName) throws IOException {
    myLocalTestRunDataController.withTestDataHolder(localHolder ->
                                                    {
                                                      final int testNameId = localHolder.myTestNameEnumerator.enumerate(testName);
                                                      IntObjectMap<IntList> classData = loadClassAndMethodsMap(file, localHolder);
                                                      IntObjectMap<IntList> previousClassData =
                                                        localHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId);
                                                      if (previousClassData == null) {
                                                        previousClassData =
                                                          myRemoteTestRunDataController.withTestDataHolder(remoteDataHolder ->
                                                                                                           {
                                                                                                             IntObjectMap<IntList>
                                                                                                               remoteClassData =
                                                                                                               remoteDataHolder.myTestNameToUsedClassesAndMethodMap
                                                                                                                 .get(testNameId);
                                                                                                             if (remoteClassData == null) {
                                                                                                               return null;
                                                                                                             }
                                                                                                             IntObjectMap<IntList> result =
                                                                                                               IntMaps.newIntObjectHashMap(
                                                                                                                 remoteClassData.size());
                                                                                                             for (IntObjectMap.IntObjectEntry<IntList> entry : remoteClassData
                                                                                                               .entrySet()) {
                                                                                                               int remoteClassKey =
                                                                                                                 entry.getKey();
                                                                                                               IntList
                                                                                                                 remoteClassMethodIds =
                                                                                                                 entry.getValue();

                                                                                                               int localClassKey =
                                                                                                                 localHolder.myClassEnumeratorCache
                                                                                                                   .enumerate(
                                                                                                                     remoteDataHolder.myClassEnumeratorCache
                                                                                                                       .valueOf(
                                                                                                                         remoteClassKey));
                                                                                                               IntList localClassIds =
                                                                                                                 IntLists.newArrayList(
                                                                                                                   remoteClassMethodIds.size());
                                                                                                               for (int methodId : remoteClassMethodIds
                                                                                                                 .toArray()) {
                                                                                                                 localClassIds.add(
                                                                                                                   localHolder.myMethodEnumeratorCache
                                                                                                                     .enumerate(
                                                                                                                       remoteDataHolder.myMethodEnumeratorCache
                                                                                                                         .valueOf(methodId)));
                                                                                                               }
                                                                                                               result.put(localClassKey,
                                                                                                                          localClassIds);
                                                                                                             }
                                                                                                             return result;
                                                                                                           });
                                                      }

                                                      localHolder.doUpdateFromDiff(testNameId,
                                                                                   classData,
                                                                                   previousClassData,
                                                                                   moduleName != null ? localHolder.myModuleNameEnumerator.enumerate(
                                                                                     moduleName) : null);
                                                      return null;
                                                    });
  }

  @Nonnull
  private static IntObjectMap<IntList> loadClassAndMethodsMap(File file, TestInfoHolder holder) throws IOException {
    DataInputStream inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(file), 64 * 1024));
    byte[] buffer = IOUtil.allocReadWriteUTFBuffer();

    try {
      int numberOfClasses = DataInputOutputUtil.readINT(inputStream);
      IntObjectMap<IntList> classData = IntMaps.newIntObjectHashMap(numberOfClasses);
      while (numberOfClasses-- > 0) {
        String classQName = IOUtil.readUTFFast(buffer, inputStream);
        int classId = holder.myClassEnumeratorCache.enumerate(classQName);
        int numberOfMethods = DataInputOutputUtil.readINT(inputStream);
        IntList methodsList = IntLists.newArrayList(numberOfMethods);

        while (numberOfMethods-- > 0) {
          String methodName = IOUtil.readUTFFast(buffer, inputStream);
          methodsList.add(holder.myMethodEnumeratorCache.enumerate(methodName));
        }

        classData.put(classId, methodsList);
      }
      return classData;
    }
    finally {
      inputStream.close();
    }
  }
}
