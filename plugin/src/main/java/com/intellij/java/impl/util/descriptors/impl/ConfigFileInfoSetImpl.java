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
import consulo.util.lang.Comparing;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.collection.MultiValuesMap;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * @author nik
 */
public class ConfigFileInfoSetImpl implements ConfigFileInfoSet {
  private static final Logger LOG = Logger.getInstance(ConfigFileInfoSetImpl.class);
  @NonNls
  private static final String ELEMENT_NAME = "deploymentDescriptor";
  @NonNls
  private static final String ID_ATTRIBUTE = "name";
  @NonNls
  private static final String URL_ATTRIBUTE = "url";
  private final MultiValuesMap<ConfigFileMetaData, ConfigFileInfo> myConfigFiles = new MultiValuesMap<ConfigFileMetaData, ConfigFileInfo>();
  private
  @Nullable
  ConfigFileContainerImpl myContainer;
  private final ConfigFileMetaDataProvider myMetaDataProvider;

  public ConfigFileInfoSetImpl(ConfigFileMetaDataProvider metaDataProvider) {
    myMetaDataProvider = metaDataProvider;
  }

  public void addConfigFile(ConfigFileInfo descriptor) {
    myConfigFiles.put(descriptor.getMetaData(), descriptor);
    onChange();
  }

  public void addConfigFile(ConfigFileMetaData metaData, String url) {
    addConfigFile(new ConfigFileInfo(metaData, url));
  }

  public void removeConfigFile(ConfigFileInfo descriptor) {
    myConfigFiles.remove(descriptor.getMetaData(), descriptor);
    onChange();
  }

  public void replaceConfigFile(ConfigFileMetaData metaData, String newUrl) {
    myConfigFiles.removeAll(metaData);
    addConfigFile(new ConfigFileInfo(metaData, newUrl));
  }

  public ConfigFileInfo updateConfigFile(ConfigFile configFile) {
    myConfigFiles.remove(configFile.getMetaData(), configFile.getInfo());
    ConfigFileInfo info = new ConfigFileInfo(configFile.getMetaData(), configFile.getUrl());
    myConfigFiles.put(info.getMetaData(), info);
    ((ConfigFileImpl) configFile).setInfo(info);
    return info;
  }

  public void removeConfigFiles(ConfigFileMetaData... metaData) {
    for (ConfigFileMetaData data : metaData) {
      myConfigFiles.removeAll(data);
    }
    onChange();
  }

  @Nullable
  public ConfigFileInfo getConfigFileInfo(ConfigFileMetaData metaData) {
    Collection<ConfigFileInfo> descriptors = myConfigFiles.get(metaData);
    if (descriptors == null || descriptors.isEmpty()) {
      return null;
    }
    return descriptors.iterator().next();
  }

  public ConfigFileInfo[] getConfigFileInfos() {
    Collection<ConfigFileInfo> configurations = myConfigFiles.values();
    return configurations.toArray(new ConfigFileInfo[configurations.size()]);
  }

  public void setConfigFileInfos(Collection<ConfigFileInfo> descriptors) {
    myConfigFiles.clear();
    for (ConfigFileInfo descriptor : descriptors) {
      myConfigFiles.put(descriptor.getMetaData(), descriptor);
    }
    onChange();
  }

  private void onChange() {
    if (myContainer != null) {
      myContainer.updateDescriptors(myConfigFiles);
    }
  }


  public ConfigFileMetaDataProvider getMetaDataProvider() {
    return myMetaDataProvider;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myConfigFiles.clear();
    List<Element> children = element.getChildren(ELEMENT_NAME);
    for (Element child : children) {
      String id = child.getAttributeValue(ID_ATTRIBUTE);
      if (id != null) {
        ConfigFileMetaData metaData = myMetaDataProvider.findMetaData(id);
        if (metaData != null) {
          String url = child.getAttributeValue(URL_ATTRIBUTE);
          myConfigFiles.put(metaData, new ConfigFileInfo(metaData, url));
        }
      }
    }
    onChange();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(Element element) throws WriteExternalException {
    TreeSet<ConfigFileInfo> sortedConfigFiles = new TreeSet<ConfigFileInfo>(new Comparator<ConfigFileInfo>() {
      public int compare(ConfigFileInfo o1, ConfigFileInfo o2) {
        int id = Comparing.compare(o1.getMetaData().getId(), o2.getMetaData().getId());
        return id != 0 ? id : Comparing.compare(o1.getUrl(), o2.getUrl());
      }
    });
    sortedConfigFiles.addAll(myConfigFiles.collectValues());
    for (ConfigFileInfo configuration : sortedConfigFiles) {
      Element child = new Element(ELEMENT_NAME);
      ConfigFileMetaData metaData = configuration.getMetaData();
      child.setAttribute(ID_ATTRIBUTE, metaData.getId());
      child.setAttribute(URL_ATTRIBUTE, configuration.getUrl());
      element.addContent(child);
    }
  }

  public void setContainer(@Nonnull ConfigFileContainerImpl container) {
    LOG.assertTrue(myContainer == null);
    myContainer = container;
    myContainer.updateDescriptors(myConfigFiles);
  }
}
