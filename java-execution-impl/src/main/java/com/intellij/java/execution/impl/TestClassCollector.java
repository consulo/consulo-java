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
package com.intellij.java.execution.impl;

import consulo.application.ReadAction;
import consulo.application.progress.ProgressManager;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.component.ProcessCanceledException;
import consulo.execution.test.TestSearchScope;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.layer.OrderEnumerator;
import consulo.util.collection.ArrayUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.nodep.classloader.UrlClassLoader;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.PathsList;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class TestClassCollector {
  private static final Logger LOG = Logger.getInstance(TestClassCollector.class);

  public static String[] collectClassFQNames(String packageName, @jakarta.annotation.Nullable Path rootPath, JavaTestConfigurationBase configuration, Function<ClassLoader, Predicate<Class<?>>> predicateProducer) {
    Module module = configuration.getConfigurationModule().getModule();
    ClassLoader classLoader = createUsersClassLoader(configuration);
    Set<String> classes = new HashSet<>();
    try {
      String packagePath = packageName.replace('.', '/');
      Enumeration<URL> resources = classLoader.getResources(packagePath);

      Predicate<Class<?>> classPredicate = predicateProducer.apply(classLoader);
      while (resources.hasMoreElements()) {
        URL url = resources.nextElement();

        //don't search for tests in jars
        if ("jar".equals(url.getProtocol())) {
          continue;
        }

        Path baseDir = Paths.get(url.toURI());

        //collect tests under single module test output only
        if (rootPath != null && !baseDir.startsWith(rootPath)) {
          continue;
        }

        String pathSeparator = baseDir.getFileSystem().getSeparator();
        Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            ProgressManager.checkCanceled();
            FileVisitResult result = super.visitFile(file, attrs);
            File f = file.toFile();
            String fName = f.getName();
            if (fName.endsWith(".class")) {
              try {
                Path relativePath = baseDir.relativize(file.getParent());
                String subpackageName = StringUtil.getQualifiedName(relativePath.toString().replace(pathSeparator, "."), FileUtil.getNameWithoutExtension(fName));
                String fqName = StringUtil.getQualifiedName(packageName, subpackageName);
                Class<?> aClass = Class.forName(fqName, false, classLoader);
                //is potential junit 4
                int modifiers = aClass.getModifiers();
                if (Modifier.isAbstract(modifiers) || !Modifier.isPublic(modifiers) || aClass.isMemberClass() && !Modifier.isStatic(modifiers)) {
                  return result;
                }
                if (classPredicate.test(aClass)) {
                  classes.add(fqName);
                }
              } catch (Throwable e) {
                LOG.error("error processing: " + fName + " of " + baseDir.toString(), e);
              }
            }
            return result;
          }
        });
      }
    } catch (ProcessCanceledException e) {
      throw e;
    } catch (Throwable e) {
      LOG.error(e);
    }

    return ArrayUtil.toStringArray(classes);
  }

  public static ClassLoader createUsersClassLoader(JavaTestConfigurationBase configuration) {
    Module module = configuration.getConfigurationModule().getModule();
    List<URL> urls = new ArrayList<>();

    PathsList pathsList = ReadAction.compute(() -> (module == null || configuration.getTestSearchScope() == TestSearchScope.WHOLE_PROJECT ? OrderEnumerator.orderEntries(configuration.getProject
        ()) : OrderEnumerator.orderEntries(module)).runtimeOnly().recursively().getPathsList()); //include jdk to avoid NoClassDefFoundError for classes inside tools.jar
    for (VirtualFile file : pathsList.getVirtualFiles()) {
      try {
        urls.add(VirtualFileUtil.virtualToIoFile(file).toURI().toURL());
      } catch (MalformedURLException ignored) {
        LOG.info(ignored);
      }
    }

    return UrlClassLoader.build().allowLock().useCache().urls(urls).get();
  }

  @Nullable
  public static Path getRootPath(Module module, final boolean chooseSingleModule) {
    if (chooseSingleModule) {
      ModuleCompilerPathsManager moduleExtension = ModuleCompilerPathsManager.getInstance(module);
      VirtualFile tests = moduleExtension.getCompilerOutput(TestContentFolderTypeProvider.getInstance());
      if (tests != null) {
        return Paths.get(VirtualFileUtil.virtualToIoFile(tests).toURI());
      }
    }
    return null;
  }
}
