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

import com.intellij.java.impl.util.descriptors.ConfigFileMetaData;
import com.intellij.java.impl.util.descriptors.ConfigFileMetaDataRegistry;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class ConfigFileMetaDataRegistryImpl implements ConfigFileMetaDataRegistry {
  private final List<ConfigFileMetaData> myMetaData = new ArrayList<ConfigFileMetaData>();
  private final Map<String, ConfigFileMetaData> myId2MetaData = new HashMap<String, ConfigFileMetaData>();
  private ConfigFileMetaData[] myCachedMetaData;

  public ConfigFileMetaDataRegistryImpl() {
  }

  public ConfigFileMetaDataRegistryImpl(ConfigFileMetaData[] metaDatas) {
    for (ConfigFileMetaData metaData : metaDatas) {
      registerMetaData(metaData);
    }
  }

  @Nonnull
  public ConfigFileMetaData[] getMetaData() {
    if (myCachedMetaData == null) {
      myCachedMetaData = myMetaData.toArray(new ConfigFileMetaData[myMetaData.size()]);
    }
    return myCachedMetaData;
  }

  @Nullable
  public ConfigFileMetaData findMetaData(@NonNls @Nonnull final String id) {
    return myId2MetaData.get(id);
  }

  public void registerMetaData(@Nonnull final ConfigFileMetaData... metaData) {
    for (ConfigFileMetaData data : metaData) {
      myMetaData.add(data);
      myId2MetaData.put(data.getId(), data);
    }
    myCachedMetaData = null;
  }
}
