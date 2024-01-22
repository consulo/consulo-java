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

package com.intellij.java.impl.util.descriptors;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nullable;

/**
 * @author nik
 */
@ServiceAPI(ComponentScope.APPLICATION)
public abstract class ConfigFileFactory {

  public static ConfigFileFactory getInstance() {
    return ServiceManager.getService(ConfigFileFactory.class);
  }


  public abstract ConfigFileMetaDataProvider createMetaDataProvider(ConfigFileMetaData... metaDatas);

  public abstract ConfigFileInfoSet createConfigFileInfoSet(ConfigFileMetaDataProvider metaDataProvider);

  public abstract ConfigFileContainer createConfigFileContainer(Project project, ConfigFileMetaDataProvider metaDataProvider,
                                                              ConfigFileInfoSet configuration);

  public abstract ConfigFileMetaDataRegistry createMetaDataRegistry();

  @jakarta.annotation.Nullable
  public abstract VirtualFile createFile(@Nullable Project project, String url, ConfigFileVersion version, final boolean forceNew);

  public abstract ConfigFileContainer createSingleFileContainer(Project project, ConfigFileMetaData metaData);
}
