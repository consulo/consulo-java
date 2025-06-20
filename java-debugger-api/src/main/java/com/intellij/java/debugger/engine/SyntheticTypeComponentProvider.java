/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.debugger.engine;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.internal.com.sun.jdi.TypeComponent;

/**
 * @author Nikolay.Tropin
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface SyntheticTypeComponentProvider {
    boolean isSynthetic(TypeComponent typeComponent);

    //override this method to prevent other providers treating type component as synthetic
    default boolean isNotSynthetic(TypeComponent typeComponent) {
        return false;
    }
}
