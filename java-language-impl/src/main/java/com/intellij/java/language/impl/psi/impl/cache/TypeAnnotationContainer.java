// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.cache;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.compiled.ClsAnnotationParameterListImpl;
import com.intellij.java.language.impl.psi.impl.compiled.ClsElementImpl;
import com.intellij.java.language.impl.psi.impl.compiled.ClsJavaCodeReferenceElementImpl;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiAnnotationStubImpl;
import com.intellij.java.language.psi.*;
import consulo.application.util.AtomicNotNullLazyValue;
import consulo.application.util.NotNullLazyValue;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.meta.MetaDataService;
import consulo.language.psi.meta.PsiMetaData;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An immutable container that holds all the type annotations for some type (including internal type components).
 */
public class TypeAnnotationContainer {
  /**
   * A container that contains no type annotations.
   */
  public static final TypeAnnotationContainer EMPTY = new TypeAnnotationContainer(Collections.emptyList());

  private final List<TypeAnnotationEntry> myList;

  private TypeAnnotationContainer(List<TypeAnnotationEntry> entries) {
    myList = entries;
  }

  /**
   * @return type annotation container for array element
   * (assuming that this type annotation container is used for the array type)
   */
  @Nonnull
  public TypeAnnotationContainer forArrayElement() {
    if (isEmpty()) {
      return this;
    }
    List<TypeAnnotationEntry> list = ContainerUtil.mapNotNull(myList, entry -> entry.forPathElement(Collector.ARRAY_ELEMENT));
    return list.isEmpty() ? EMPTY : new TypeAnnotationContainer(list);
  }

  /**
   * @return type annotation container for enclosing class
   * (assuming that this type annotation container is used for the inner class)
   */
  @Nonnull
  public TypeAnnotationContainer forEnclosingClass() {
    if (isEmpty()) {
      return this;
    }
    List<TypeAnnotationEntry> list = ContainerUtil.mapNotNull(myList, entry -> entry.forPathElement(Collector.ENCLOSING_CLASS));
    return list.isEmpty() ? EMPTY : new TypeAnnotationContainer(list);
  }

  /**
   * @return type annotation container for wildcard bound
   * (assuming that this type annotation container is used for the bounded wildcard type)
   */
  @Nonnull
  public TypeAnnotationContainer forBound() {
    if (isEmpty()) {
      return this;
    }
    List<TypeAnnotationEntry> list = ContainerUtil.mapNotNull(myList, entry -> entry.forPathElement(Collector.WILDCARD_BOUND));
    return list.isEmpty() ? EMPTY : new TypeAnnotationContainer(list);
  }

  /**
   * @param index type argument index
   * @return type annotation container for given type argument
   * (assuming that this type annotation container is used for class type with type arguments)
   */
  @Nonnull
  public TypeAnnotationContainer forTypeArgument(int index) {
    if (isEmpty()) {
      return this;
    }
    List<TypeAnnotationEntry> list = ContainerUtil.mapNotNull(myList, e -> e.forTypeArgument(index));
    return list.isEmpty() ? EMPTY : new TypeAnnotationContainer(list);
  }

  /**
   * @return true if this type annotation container contains no type annotations
   */
  public boolean isEmpty() {
    return myList.isEmpty();
  }

  /**
   * @param parent parent element for annotations
   * @return TypeAnnotationProvider that provides all the top-level annotations
   */
  public TypeAnnotationProvider getProvider(PsiElement parent) {
    if (isEmpty()) {
      return TypeAnnotationProvider.EMPTY;
    }
    return new TypeAnnotationProvider() {
      @Override
      @Nonnull
      public PsiAnnotation[] getAnnotations() {
        List<PsiAnnotation> result = new ArrayList<>();
        for (TypeAnnotationEntry entry : myList) {
          if (entry.myPath.length == 0) {
            PsiAnnotation anno = parent instanceof PsiCompiledElement ? new ClsTypeAnnotationImpl(parent, entry.myText) :
                JavaPsiFacade.getElementFactory(parent.getProject()).createAnnotationFromText(entry.myText, parent);
            result.add(anno);
          }
        }
        return result.toArray(PsiAnnotation.EMPTY_ARRAY);
      }
    };
  }

  /**
   * Creates PsiAnnotationStub elements for top-level annotations in this container
   *
   * @param parent parent stub
   */
  public void createAnnotationStubs(StubElement<?> parent) {
    for (TypeAnnotationEntry entry : myList) {
      if (entry.myPath.length == 0) {
        new PsiAnnotationStubImpl(parent, entry.myText);
      }
    }
  }

  /**
   * @param type    type
   * @param context context PsiElement
   * @return type annotated with annotations from this container
   */
  public PsiType applyTo(PsiType type, PsiElement context) {
    if (isEmpty()) {
      return type;
    }
    if (type instanceof PsiArrayType) {
      PsiType componentType = ((PsiArrayType) type).getComponentType();
      PsiType modifiedComponentType = forArrayElement().applyTo(componentType, context);
      if (componentType != modifiedComponentType) {
        type = type instanceof PsiEllipsisType ? new PsiEllipsisType(modifiedComponentType) : modifiedComponentType.createArrayType();
      }
    }
    // TODO: support generics, bounds and enclosing classes
    return type.annotate(getProvider(context));
  }

  /**
   * Serializes TypeAnnotationContainer into the supplied stream.
   *
   * @param dataStream stream to write to
   * @param container  a container to serialize
   * @throws IOException if the stream throws
   */
  public static void writeTypeAnnotations(@Nonnull StubOutputStream dataStream, @Nonnull TypeAnnotationContainer container)
      throws IOException {
    dataStream.writeShort(container.myList.size());
    for (TypeAnnotationEntry entry : container.myList) {
      dataStream.writeShort(entry.myPath.length);
      dataStream.write(entry.myPath);
      dataStream.writeUTFFast(entry.myText);
    }
  }

  /**
   * Reads TypeAnnotationContainer from the supplied stream.
   *
   * @param dataStream stream to read from
   * @return deserialized TypeAnnotationContainer
   * @throws IOException if the stream throws
   */
  public static
  @Nonnull
  TypeAnnotationContainer readTypeAnnotations(@Nonnull StubInputStream dataStream) throws IOException {
    short count = dataStream.readShort();
    TypeAnnotationEntry[] entries = new TypeAnnotationEntry[count];
    for (int i = 0; i < count; i++) {
      short pathLength = dataStream.readShort();
      byte[] path = new byte[pathLength];
      dataStream.readFully(path);
      String text = dataStream.readUTFFast();
      entries[i] = new TypeAnnotationEntry(path, text);
    }
    return new TypeAnnotationContainer(Arrays.asList(entries));
  }

  @Override
  public String toString() {
    return StringUtil.join(myList, "\n");
  }

  public static class Collector {
    public static final byte ARRAY_ELEMENT = 0;
    public static final byte ENCLOSING_CLASS = 1;
    public static final byte WILDCARD_BOUND = 2;
    public static final byte TYPE_ARGUMENT = 3;

    private final
    @Nonnull
    ArrayList<TypeAnnotationEntry> myList = new ArrayList<>();
    protected final
    @Nonnull
    TypeInfo myTypeInfo;

    public Collector(@Nonnull TypeInfo info) {
      myTypeInfo = info;
    }

    public void add(@Nonnull byte[] path, @Nonnull String text) {
      myList.add(new TypeAnnotationEntry(path, text));
    }

    public void install() {
      if (myList.isEmpty()) {
        myTypeInfo.setTypeAnnotations(EMPTY);
      } else {
        myList.trimToSize();
        myTypeInfo.setTypeAnnotations(new TypeAnnotationContainer(myList));
      }
    }
  }

  private static class TypeAnnotationEntry {
    /**
     * path is stored as the sequence of ARRAY_ELEMENT, ENCLOSING_CLASS, WILDCARD_BOUND and TYPE_ARGUMENT bytes.
     * The TYPE_ARGUMENT byte is followed by the type argument index byte.
     */
    @Nonnull
    final byte[] myPath;
    final
    @Nonnull
    String myText;

    private TypeAnnotationEntry(@Nonnull byte[] path, @Nonnull String text) {
      myPath = path;
      myText = text;
    }

    private TypeAnnotationEntry forPathElement(int wanted) {
      if (myPath.length > 0 && myPath[0] == wanted) {
        return new TypeAnnotationEntry(Arrays.copyOfRange(myPath, 1, myPath.length), myText);
      }
      return null;
    }

    public TypeAnnotationEntry forTypeArgument(int index) {
      if (myPath.length > 1 && myPath[0] == Collector.TYPE_ARGUMENT && myPath[1] == index) {
        return new TypeAnnotationEntry(Arrays.copyOfRange(myPath, 2, myPath.length), myText);
      }
      return null;
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder();
      int pos = 0;
      while (pos < myPath.length) {
        switch (myPath[pos]) {
          case Collector.ARRAY_ELEMENT:
            result.append('[');
            break;
          case Collector.ENCLOSING_CLASS:
            result.append('.');
            break;
          case Collector.WILDCARD_BOUND:
            result.append('*');
            break;
          case Collector.TYPE_ARGUMENT:
            result.append(myPath[++pos]).append(';');
            break;
        }
        pos++;
      }
      return result + "->" + myText;
    }
  }

  static class ClsTypeAnnotationImpl extends ClsElementImpl implements PsiAnnotation {
    private final NotNullLazyValue<ClsJavaCodeReferenceElementImpl> myReferenceElement;
    private final NotNullLazyValue<ClsAnnotationParameterListImpl> myParameterList;
    private final PsiElement myParent;
    private final String myText;

    ClsTypeAnnotationImpl(PsiElement parent, String text) {
      myParent = parent;
      myText = text;
      myReferenceElement = AtomicNotNullLazyValue.createValue(() -> {
        int index = myText.indexOf('(');
        String refText = index > 0 ? myText.substring(1, index) : myText.substring(1);
        return new ClsJavaCodeReferenceElementImpl(ClsTypeAnnotationImpl.this, refText);
      });
      myParameterList = AtomicNotNullLazyValue.createValue(() -> {
        PsiNameValuePair[] attrs = myText.indexOf('(') > 0
            ? JavaPsiFacade.getElementFactory(getProject()).createAnnotationFromText(myText, myParent)
            .getParameterList().getAttributes()
            : PsiNameValuePair.EMPTY_ARRAY;
        return new ClsAnnotationParameterListImpl(ClsTypeAnnotationImpl.this, attrs);
      });
    }

    @Override
    @Nonnull
    public PsiAnnotationParameterList getParameterList() {
      return myParameterList.getValue();
    }

    @Override
    @Nullable
    public String getQualifiedName() {
      return getNameReferenceElement().getCanonicalText();
    }

    @Override
    @Nonnull
    public PsiJavaCodeReferenceElement getNameReferenceElement() {
      return myReferenceElement.getValue();
    }

    @Override
    public
    @Nullable
    PsiAnnotationMemberValue findAttributeValue(@Nullable String attributeName) {
      return PsiImplUtil.findAttributeValue(this, attributeName);
    }

    @Override
    public
    @Nullable
    PsiAnnotationMemberValue findDeclaredAttributeValue(@Nullable String attributeName) {
      return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
    }

    @Override
    public <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@Nullable String attributeName, @Nullable T value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public
    @Nullable
    PsiAnnotationOwner getOwner() {
      return ObjectUtil.tryCast(myParent, PsiAnnotationOwner.class);
    }

    @Override
    public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
      buffer.append(myText);
    }

    @Override
    public String getText() {
      return myText;
    }

    @Override
    public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
      setMirrorCheckingType(element, null);
      PsiAnnotation mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
      setMirror(getNameReferenceElement(), mirror.getNameReferenceElement());
      setMirror(getParameterList(), mirror.getParameterList());
    }

    @Override
    @Nonnull
    public PsiElement[] getChildren() {
      return new PsiElement[]{
          myReferenceElement.getValue(),
          getParameterList()
      };
    }

    @Override
    public PsiElement getParent() {
      return myParent;
    }

    @Override
    public void accept(@Nonnull PsiElementVisitor visitor) {
      if (visitor instanceof JavaElementVisitor) {
        ((JavaElementVisitor) visitor).visitAnnotation(this);
      } else {
        visitor.visitElement(this);
      }
    }

    @Nullable
    @Override
    public PsiMetaData getMetaData() {
      return MetaDataService.getInstance().getMeta(this);
    }
  }
}
