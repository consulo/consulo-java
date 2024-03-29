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
package com.intellij.java.compiler.impl.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class ClassInfoConstantValue extends ConstantValue{
  private final int myValue;

  public ClassInfoConstantValue(int value) {
    myValue = value;
  }

  public ClassInfoConstantValue(DataInput in) throws IOException{
    myValue = in.readInt();
  }

  public int getValue() {
    return myValue;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    out.writeInt(myValue);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClassInfoConstantValue)) return false;

    final ClassInfoConstantValue classInfoConstantValue = (ClassInfoConstantValue)o;

    if (myValue != classInfoConstantValue.myValue) return false;

    return true;
  }

  public int hashCode() {
    return myValue;
  }
}
