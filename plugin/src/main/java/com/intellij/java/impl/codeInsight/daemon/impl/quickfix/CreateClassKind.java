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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.ClassKind;
import com.intellij.java.language.psi.util.JavaElementKind;
import consulo.java.language.impl.icon.JavaPsiImplIconGroup;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;

/**
 * @author ven
 */
public enum CreateClassKind implements ClassKind {
    CLASS(JavaElementKind.CLASS, PlatformIconGroup.nodesClass()),
    INTERFACE(JavaElementKind.INTERFACE, PlatformIconGroup.nodesInterface()),
    ENUM(JavaElementKind.ENUM, PlatformIconGroup.nodesEnum()),
    ANNOTATION(JavaElementKind.ANNOTATION, PlatformIconGroup.nodesAnnotationtype()),
    RECORD(JavaElementKind.RECORD, JavaPsiImplIconGroup.nodesRecord());

    private final JavaElementKind myKind;
    private final Image myKindIcon;

    CreateClassKind(JavaElementKind kind, Image kindIcon) {
        myKind = kind;
        myKindIcon = kindIcon;
    }

    public Image getKindIcon() {
        return myKindIcon;
    }

    @Override
    public String getDescription() {
        return myKind.subject();
    }

    @Override
    public String getDescriptionAccusative() {
        return myKind.object();
    }
}
