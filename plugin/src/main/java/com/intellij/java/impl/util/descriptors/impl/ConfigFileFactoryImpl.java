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
import consulo.application.ApplicationManager;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.ide.IdeBundle;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.ide.impl.idea.util.FileContentUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Singleton;

import jakarta.annotation.Nullable;
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
  public ConfigFileMetaDataProvider createMetaDataProvider(final ConfigFileMetaData... metaDatas) {
    return new ConfigFileMetaDataRegistryImpl(metaDatas);
  }

  @Override
  public ConfigFileMetaDataRegistry createMetaDataRegistry() {
    return new ConfigFileMetaDataRegistryImpl();
  }

  @Override
  public ConfigFileInfoSet createConfigFileInfoSet(final ConfigFileMetaDataProvider metaDataProvider) {
    return new ConfigFileInfoSetImpl(metaDataProvider);
  }

  @Override
  public ConfigFileContainer createConfigFileContainer(final Project project, final ConfigFileMetaDataProvider metaDataProvider, final ConfigFileInfoSet configuration) {
    return new ConfigFileContainerImpl(project, metaDataProvider, (ConfigFileInfoSetImpl) configuration);
  }

  private static String getText(final String templateName, @Nullable Project project) throws IOException {
    final FileTemplateManager templateManager = project == null ? FileTemplateManager.getDefaultInstance() : FileTemplateManager.getInstance(project);
    final FileTemplate template = templateManager.getJ2eeTemplate(templateName);
    if (template == null) {
      return "";
    }
    return template.getText(templateManager.getDefaultProperties());
  }

  @Override
  @Nullable
  public VirtualFile createFile(@Nullable Project project, String url, ConfigFileVersion version, final boolean forceNew) {
    return createFileFromTemplate(project, url, version.getTemplateName(), forceNew);
  }

  @Nullable
  private VirtualFile createFileFromTemplate(@Nullable final Project project, String url, final String templateName, final boolean forceNew) {
    final LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    final File file = new File(consulo.ide.impl.idea.openapi.vfs.VfsUtil.urlToPath(url));
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
      final VirtualFile childData;
      if (existingFile == null || existingFile.isDirectory()) {
        final VirtualFile virtualFile;
        if (!FileUtil.createParentDirs(file) || (virtualFile = fileSystem.refreshAndFindFileByIoFile(file.getParentFile())) == null) {
          throw new IOException(IdeBundle.message("error.message.unable.to.create.file", file.getPath()));
        }
        childData = virtualFile.createChildData(this, file.getName());
      } else {
        childData = existingFile;
      }
      FileContentUtil.setFileText(project, childData, text);
      return childData;
    } catch (final IOException e) {
      LOG.info(e);
      ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(IdeBundle.message("message.text.error.creating.deployment.descriptor", e.getLocalizedMessage()), IdeBundle
          .message("message.text.creating.deployment.descriptor")));
    }
    return null;
  }

  @Override
  public ConfigFileContainer createSingleFileContainer(Project project, ConfigFileMetaData metaData) {
    final ConfigFileMetaDataProvider metaDataProvider = createMetaDataProvider(metaData);
    return createConfigFileContainer(project, metaDataProvider, createConfigFileInfoSet(metaDataProvider));
  }
}
