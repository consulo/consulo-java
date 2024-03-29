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
package com.intellij.java.debugger.impl.descriptors.data;

import com.intellij.java.debugger.impl.ui.impl.watch.FieldDescriptorImpl;
import consulo.project.Project;
import consulo.internal.com.sun.jdi.Field;
import consulo.internal.com.sun.jdi.ObjectReference;
import jakarta.annotation.Nonnull;

public final class FieldData extends DescriptorData<FieldDescriptorImpl>{
  private final ObjectReference myObjRef;
  private final Field myField;

  public FieldData(@Nonnull ObjectReference objRef, @Nonnull Field field) {
    myObjRef = objRef;
    myField = field;
  }

  protected FieldDescriptorImpl createDescriptorImpl(Project project) {
    return new FieldDescriptorImpl(project, myObjRef, myField);
  }

  public boolean equals(Object object) {
    if(!(object instanceof FieldData)) {
      return false;
    }
    final FieldData fieldData = (FieldData)object;
    return fieldData.myField == myField && fieldData.myObjRef.equals(myObjRef);
  }

  public int hashCode() {
    return myObjRef.hashCode() + myField.hashCode();
  }

  public DisplayKey<FieldDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<FieldDescriptorImpl>(myField);
  }
}
