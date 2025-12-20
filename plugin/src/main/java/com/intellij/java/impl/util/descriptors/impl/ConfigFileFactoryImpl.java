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

package com.intellij.java.impl.util.descriptors.impl;

import com.intellij.java.impl.util.descriptors.*;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.ide.localize.IdeLocalize;
import consulo.language.util.LanguageFileContentUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
@Singleton
@ServiceImpl
public class ConfigFileFactoryImpl extends ConfigFileFactory {
  private static final Logger LOG = Logger.getInstance(ConfigFileFactoryImpl.class);

  @Override
  public ConfigFileMetaDataProvider createMetaDataProvider(ConfigFileMetaData... metaDatas) {
    return new ConfigFileMetaDataRegistryImpl(metaDatas);
  }

  @Override
  public ConfigFileMetaDataRegistry createMetaDataRegistry() {
    return new ConfigFileMetaDataRegistryImpl();
  }

  @Override
  public ConfigFileInfoSet createConfigFileInfoSet(ConfigFileMetaDataProvider metaDataProvider) {
    return new ConfigFileInfoSetImpl(metaDataProvider);
  }

  @Override
  public ConfigFileContainer createConfigFileContainer(Project project, ConfigFileMetaDataProvider metaDataProvider, ConfigFileInfoSet configuration) {
    return new ConfigFileContainerImpl(project, metaDataProvider, (ConfigFileInfoSetImpl) configuration);
  }

  private static String getText(String templateName, @Nullable Project project) throws IOException {
    FileTemplateManager templateManager = project == null ? FileTemplateManager.getDefaultInstance() : FileTemplateManager.getInstance(project);
    FileTemplate template = templateManager.getJ2eeTemplate(templateName);
    if (template == null) {
      return "";
    }
    return template.getText(templateManager.getDefaultProperties());
  }

  @Override
  @Nullable
  public VirtualFile createFile(@Nullable Project project, String url, ConfigFileVersion version, boolean forceNew) {
    return createFileFromTemplate(project, url, version.getTemplateName(), forceNew);
  }

  @Nullable
  private VirtualFile createFileFromTemplate(@Nullable Project project, String url, String templateName, boolean forceNew) {
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    File file = new File(VirtualFileUtil.urlToPath(url));
    VirtualFile existingFile = fileSystem.refreshAndFindFileByIoFile(file);
    if (existingFile != null) {
      existingFile.refresh(false, false);
      if (!existingFile.isValid()) {
        existingFile = null;
      }
    }

    if (existingFile != null && !forceNew) {
      return existingFile;
    }
    try {
      String text = getText(templateName, project);
      VirtualFile childData;
      if (existingFile == null || existingFile.isDirectory()) {
        VirtualFile virtualFile;
        if (!FileUtil.createParentDirs(file) || (virtualFile = fileSystem.refreshAndFindFileByIoFile(file.getParentFile())) == null) {
          throw new IOException(IdeLocalize.errorMessageUnableToCreateFile(file.getPath()).get());
        }
        childData = virtualFile.createChildData(this, file.getName());
      } else {
        childData = existingFile;
      }
      LanguageFileContentUtil.setFileText(project, childData, text);
      return childData;
    } catch (IOException e) {
      LOG.info(e);
      Application.get().invokeLater(() -> Messages.showErrorDialog(
        IdeLocalize.messageTextErrorCreatingDeploymentDescriptor(e.getLocalizedMessage()).get(),
        IdeLocalize.messageTextCreatingDeploymentDescriptor().get()
      ));
    }
    return null;
  }

  @Override
  public ConfigFileContainer createSingleFileContainer(Project project, ConfigFileMetaData metaData) {
    ConfigFileMetaDataProvider metaDataProvider = createMetaDataProvider(metaData);
    return createConfigFileContainer(project, metaDataProvider, createConfigFileInfoSet(metaDataProvider));
  }
}
