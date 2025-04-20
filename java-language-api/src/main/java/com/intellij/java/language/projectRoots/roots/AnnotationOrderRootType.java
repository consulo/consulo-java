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
package com.intellij.java.language.projectRoots.roots;

import consulo.annotation.component.ExtensionImpl;
import consulo.content.OrderRootType;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntryWithTracking;
import consulo.module.content.layer.orderEntry.RootPolicy;
import consulo.util.collection.ArrayUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class AnnotationOrderRootType extends OrderRootType {
    @Nonnull
    public static OrderRootType getInstance() {
        return getOrderRootType(AnnotationOrderRootType.class);
    }

    public AnnotationOrderRootType() {
        super("javaExternalAnnotations");
    }

    @Nonnull
    public static VirtualFile[] getFiles(@Nonnull OrderEntry entry) {
        List<VirtualFile> result = new ArrayList<VirtualFile>();
        RootPolicy<List<VirtualFile>> policy = new RootPolicy<List<VirtualFile>>() {
            @Override
            public List<VirtualFile> visitOrderEntry(OrderEntry orderEntry, List<VirtualFile> value) {
                if (orderEntry instanceof OrderEntryWithTracking) {
                    Collections.addAll(value, orderEntry.getFiles(getInstance()));
                }
                return value;
            }
        };
        entry.accept(policy, result);
        return VirtualFileUtil.toVirtualFileArray(result);
    }

    @Nonnull
    public static String[] getUrls(@Nonnull OrderEntry entry) {
        List<String> result = new ArrayList<String>();
        RootPolicy<List<String>> policy = new RootPolicy<List<String>>() {
            @Override
            public List<String> visitOrderEntry(OrderEntry orderEntry, List<String> value) {
                if (orderEntry instanceof OrderEntryWithTracking) {
                    Collections.addAll(value, orderEntry.getUrls(getInstance()));
                }
                return value;
            }
        };
        entry.accept(policy, result);
        return ArrayUtil.toStringArray(result);
    }
}
