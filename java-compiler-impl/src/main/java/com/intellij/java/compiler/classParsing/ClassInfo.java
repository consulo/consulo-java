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
package com.intellij.java.compiler.classParsing;

import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.java.compiler.cache.SymbolTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.ClsFormatException;
import consulo.logging.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 */
public final class ClassInfo implements Cloneable {
  private static final Logger LOG = Logger.getInstance(ClassInfo.class);

  private static final ReferenceInfo[] EMPTY_REF_ARRAY = new ReferenceInfo[0];

  private final int myQualifiedName;
  private final int myGenericSignature;
  private final int mySuperQualifiedName;
  private final int myFlags;
  private String myPath;
  private final String mySourceFileName;
  private final int[] mySuperInterfaces;
  private final FieldInfo[] myFields;
  private final MethodInfo[] myMethods;
  private ReferenceInfo[] myReferences;
  private final AnnotationConstantValue[] myRuntimeVisibleAnnotations;
  private final AnnotationConstantValue[] myRuntimeInvisibleAnnotations;

  public ClassInfo(ClassFileReader reader, SymbolTable symbolTable) throws CacheCorruptedException {
    try {
      final int qName = symbolTable.getId(reader.getQualifiedName());
      myQualifiedName = qName;

      final String genericSignature = reader.getGenericSignature();
      myGenericSignature = genericSignature != null ? symbolTable.getId(genericSignature) : -1;

      myPath = reader.getPath();

      final String superClass = reader.getSuperClass();
      final int superQName = "".equals(superClass) ? -1 : symbolTable.getId(superClass);
      mySuperQualifiedName = superQName;

      LOG.assertTrue(superQName != qName);

      final String[] superInterfaces = reader.getSuperInterfaces();
      mySuperInterfaces = ArrayUtil.newIntArray(superInterfaces.length);
      for (int idx = 0; idx < superInterfaces.length; idx++) {
        mySuperInterfaces[idx] = symbolTable.getId(superInterfaces[idx]);
      }

      final String sourceFileName = reader.getSourceFileName();
      mySourceFileName = sourceFileName != null ? sourceFileName : "";

      myFlags = reader.getAccessFlags();

      myRuntimeVisibleAnnotations = reader.getRuntimeVisibleAnnotations();
      myRuntimeInvisibleAnnotations = reader.getRuntimeInvisibleAnnotations();

      final Collection<ReferenceInfo> refs = reader.getReferences();
      myReferences = refs.toArray(new ReferenceInfo[refs.size()]);

      myFields = reader.getFields();
      myMethods = reader.getMethods();
    } catch (ClsFormatException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public ClassInfo(DataInput in) throws IOException {
    myQualifiedName = in.readInt();
    mySuperQualifiedName = in.readInt();
    myGenericSignature = in.readInt();
    myFlags = in.readInt();
    myPath = in.readUTF();
    mySourceFileName = in.readUTF();

    final int ifaceCount = in.readInt();
    mySuperInterfaces = new int[ifaceCount];
    for (int idx = 0; idx < ifaceCount; idx++) {
      mySuperInterfaces[idx] = in.readInt();
    }

    final int fieldCount = in.readInt();
    myFields = new FieldInfo[fieldCount];
    for (int idx = 0; idx < fieldCount; idx++) {
      myFields[idx] = new FieldInfo(in);
    }

    final int methodCount = in.readInt();
    myMethods = new MethodInfo[methodCount];
    for (int idx = 0; idx < methodCount; idx++) {
      myMethods[idx] = new MethodInfo(in);
    }

    final int refCount = in.readInt();
    myReferences = refCount > 0 ? new ReferenceInfo[refCount] : EMPTY_REF_ARRAY;
    for (int idx = 0; idx < refCount; idx++) {
      myReferences[idx] = MemberInfoExternalizer.loadReferenceInfo(in);
    }

    myRuntimeVisibleAnnotations = MemberInfoExternalizer.readAnnotationConstantValueArray1(in);
    myRuntimeInvisibleAnnotations = MemberInfoExternalizer.readAnnotationConstantValueArray1(in);
  }

  public ClassInfo clone() {
    try {
      return (ClassInfo) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public void save(DataOutput out) throws IOException {
    out.writeInt(myQualifiedName);
    out.writeInt(mySuperQualifiedName);
    out.writeInt(myGenericSignature);
    out.writeInt(myFlags);
    out.writeUTF(myPath);
    out.writeUTF(mySourceFileName);
    out.writeInt(mySuperInterfaces.length);
    for (int ifaceQName : mySuperInterfaces) {
      out.writeInt(ifaceQName);
    }

    out.writeInt(myFields.length);
    for (FieldInfo field : myFields) {
      field.save(out);
    }

    out.writeInt(myMethods.length);
    for (MethodInfo method : myMethods) {
      method.save(out);
    }

    out.writeInt(myReferences.length);
    for (ReferenceInfo info : myReferences) {
      MemberInfoExternalizer.saveReferenceInfo(out, info);
    }

    MemberInfoExternalizer.writeConstantValueArray1(out, myRuntimeVisibleAnnotations);
    MemberInfoExternalizer.writeConstantValueArray1(out, myRuntimeInvisibleAnnotations);
  }

  public int getQualifiedName() {
    return myQualifiedName;
  }

  public int getGenericSignature() {
    return myGenericSignature;
  }

  public int getSuperQualifiedName() {
    return mySuperQualifiedName;
  }

  public int[] getSuperInterfaces() {
    return mySuperInterfaces;
  }

  public int getFlags() {
    return myFlags;
  }

  public String getPath() {
    return myPath;
  }

  public void setPath(String path) {
    myPath = path;
  }

  public String getSourceFileName() {
    return mySourceFileName;
  }

  public AnnotationConstantValue[] getRuntimeVisibleAnnotations() {
    return myRuntimeVisibleAnnotations;
  }

  public AnnotationConstantValue[] getRuntimeInvisibleAnnotations() {
    return myRuntimeInvisibleAnnotations;
  }

  public ReferenceInfo[] getReferences() {
    return myReferences;
  }

  public void clearReferences() {
    myReferences = EMPTY_REF_ARRAY;
  }

  public FieldInfo[] getFields() {
    return myFields;
  }

  public MethodInfo[] getMethods() {
    return myMethods;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ClassInfo classInfo = (ClassInfo) o;
    return myQualifiedName == classInfo.myQualifiedName &&
        myGenericSignature == classInfo.myGenericSignature &&
        mySuperQualifiedName == classInfo.mySuperQualifiedName &&
        myFlags == classInfo.myFlags &&
        Objects.equals(myPath, classInfo.myPath) &&
        Objects.equals(mySourceFileName, classInfo.mySourceFileName) &&
        Arrays.equals(mySuperInterfaces, classInfo.mySuperInterfaces) &&
        Arrays.equals(myFields, classInfo.myFields) &&
        Arrays.equals(myMethods, classInfo.myMethods) &&
        Arrays.equals(myReferences, classInfo.myReferences) &&
        Arrays.equals(myRuntimeVisibleAnnotations, classInfo.myRuntimeVisibleAnnotations) &&
        Arrays.equals(myRuntimeInvisibleAnnotations, classInfo.myRuntimeInvisibleAnnotations);
  }

  @Override
  public int hashCode() {
    return MethodInfo.hashCode(myQualifiedName, myGenericSignature, mySuperQualifiedName, myFlags, myPath, mySourceFileName, mySuperInterfaces, myFields, myMethods, myReferences,
        myRuntimeVisibleAnnotations, myRuntimeInvisibleAnnotations);
  }
}