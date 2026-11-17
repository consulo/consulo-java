/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.debugger.impl.memory;

import consulo.execution.debug.memory.TypeInfo;
import consulo.internal.com.sun.jdi.ReferenceType;

/**
 * A class of the debugged VM in the Memory view.
 *
 * @author VISTALL
 * @since 2026-11-17
 */
public final class JavaTypeInfo implements TypeInfo {
    private final ReferenceType myReferenceType;

    public JavaTypeInfo(ReferenceType referenceType) {
        myReferenceType = referenceType;
    }

    public ReferenceType getReferenceType() {
        return myReferenceType;
    }

    @Override
    public String name() {
        return myReferenceType.name();
    }

    @Override
    public boolean canGetInstanceInfo() {
        return myReferenceType.virtualMachine().canGetInstanceInfo();
    }

    @Override
    public int hashCode() {
        return myReferenceType.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof JavaTypeInfo info && info.myReferenceType.equals(myReferenceType);
    }
}
