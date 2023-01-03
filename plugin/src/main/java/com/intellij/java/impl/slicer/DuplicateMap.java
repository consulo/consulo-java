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
package com.intellij.java.impl.slicer;

import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.document.util.TextRange;
import consulo.usage.UsageInfo;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.Maps;

import java.util.Map;

/**
* @author cdr
*/ // rehash map on each PSI modification since SmartPsiPointer's hashCode() and equals() are changed
public class DuplicateMap {
  private static final HashingStrategy<SliceUsage> USAGE_INFO_EQUALITY = new HashingStrategy<SliceUsage>() {
    @Override
    public int hashCode(SliceUsage object) {
      UsageInfo info = object.getUsageInfo();
      TextRange range = info.getRangeInElement();
      return range == null ? 0 : range.hashCode();
    }

    @Override
    public boolean equals(SliceUsage o1, SliceUsage o2) {
      return o1.getUsageInfo().equals(o2.getUsageInfo());
    }
  };
  private final Map<SliceUsage, SliceNode> myDuplicates = Maps.newHashMap(USAGE_INFO_EQUALITY);

  public SliceNode putNodeCheckDupe(final SliceNode node) {
    return ApplicationManager.getApplication().runReadAction(new Computable<SliceNode>() {
      @Override
      public SliceNode compute() {
        SliceUsage usage = node.getValue();
        SliceNode eq = myDuplicates.get(usage);
        if (eq == null) {
          myDuplicates.put(usage, node);
        }
        return eq;
      }
    });
  }

  public void clear() {
    myDuplicates.clear();
  }
}
