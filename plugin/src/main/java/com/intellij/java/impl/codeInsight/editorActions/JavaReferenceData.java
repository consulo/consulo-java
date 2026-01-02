/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.editorActions;

import consulo.logging.Logger;
import consulo.util.lang.lazy.LazyValue;
import jakarta.annotation.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;
import java.util.function.Supplier;

/**
 * @author Denis Fokin
 */
public class JavaReferenceData implements Cloneable, Serializable {
    private static final Supplier<DataFlavor> DATA_FLAVOR = LazyValue.nullable(() -> {
        try {
            return new DataFlavor(
                DataFlavor.javaJVMLocalObjectMimeType + ";class=" + JavaReferenceData.class.getName(),
                "JavaReferenceData",
                JavaReferenceData.class.getClassLoader()
            );
        }
        catch (NoClassDefFoundError | IllegalArgumentException | ClassNotFoundException e) {
            Logger.getInstance(JavaReferenceData.class).warn("Fail to initialized JavaReferenceData", e);
            return null;
        }
    });

    @Nullable
    public static DataFlavor getDataFlavor() {
        return DATA_FLAVOR.get();
    }

    public int startOffset;
    public int endOffset;
    public final String qClassName;
    public final String staticMemberName;

    public JavaReferenceData(int startOffset, int endOffset, String qClassName, String staticMemberDescriptor) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.qClassName = qClassName;
        this.staticMemberName = staticMemberDescriptor;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException();
        }
    }
}
