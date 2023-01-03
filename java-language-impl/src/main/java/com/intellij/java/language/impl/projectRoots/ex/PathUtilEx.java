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

package com.intellij.java.language.impl.projectRoots.ex;

import consulo.content.bundle.Sdk;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.util.lang.ComparatorUtil;
import consulo.util.lang.StringUtil;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static consulo.util.collection.ContainerUtil.map;
import static consulo.util.collection.ContainerUtil.skipNulls;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 14, 2004
 */
public class PathUtilEx {

  private static final Function<Module, Sdk> MODULE_JDK = module -> ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
  private static final Function<Sdk, String> JDK_VERSION = jdk -> StringUtil.notNullize(jdk.getVersionString());

  @Nullable
  public static Sdk getAnyJdk(Project project) {
    return chooseJdk(project, Arrays.asList(ModuleManager.getInstance(project).getModules()));
  }

  @Nullable
  public static Sdk chooseJdk(Project project, Collection<Module> modules) {
   /* Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectJdk != null) {
      return projectJdk;
    }  */
    return chooseJdk(modules);
  }

  @Nullable
  public static Sdk chooseJdk(Collection<Module> modules) {
    List<Sdk> jdks = skipNulls(map(skipNulls(modules), MODULE_JDK));
    if (jdks.isEmpty()) {
      return null;
    }
    Collections.sort(jdks, ComparatorUtil.compareBy(JDK_VERSION, String.CASE_INSENSITIVE_ORDER));
    return jdks.get(jdks.size() - 1);
  }
}
