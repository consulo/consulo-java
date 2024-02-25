/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.impl.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.java.language.impl.psi.impl.source.JavaFileElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.java.*;
import com.intellij.java.language.impl.psi.util.JavaImplicitClassUtil;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiKeyword;
import consulo.language.ast.ASTNode;
import consulo.language.psi.stub.IStubFileElementType;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import jakarta.annotation.Nonnull;

/**
 * @author max
 */
public interface JavaStubElementTypes {
  JavaModifierListElementType MODIFIER_LIST = new JavaModifierListElementType();
  JavaAnnotationElementType ANNOTATION = new JavaAnnotationElementType();
  JavaAnnotationParameterListType ANNOTATION_PARAMETER_LIST = new JavaAnnotationParameterListType();
  JavaNameValuePairType NAME_VALUE_PAIR = new JavaNameValuePairType();
  JavaLiteralExpressionElementType LITERAL_EXPRESSION = new JavaLiteralExpressionElementType();
  LambdaExpressionElementType LAMBDA_EXPRESSION = new LambdaExpressionElementType();
  MethodReferenceElementType METHOD_REFERENCE = new MethodReferenceElementType();
  JavaParameterListElementType PARAMETER_LIST = new JavaParameterListElementType();
  JavaParameterElementType PARAMETER = new JavaParameterElementType();
  JavaTypeParameterElementType TYPE_PARAMETER = new JavaTypeParameterElementType();
  JavaTypeParameterListElementType TYPE_PARAMETER_LIST = new JavaTypeParameterListElementType();
  JavaClassInitializerElementType CLASS_INITIALIZER = new JavaClassInitializerElementType();
  JavaImportListElementType IMPORT_LIST = new JavaImportListElementType();
  JavaModuleElementType MODULE = new JavaModuleElementType();
  JavaRequiresStatementElementType REQUIRES_STATEMENT = new JavaRequiresStatementElementType();
  JavaUsesStatementElementType USES_STATEMENT = new JavaUsesStatementElementType();
  JavaProvidesStatementElementType PROVIDES_STATEMENT = new JavaProvidesStatementElementType();

  JavaPackageAccessibilityStatementElementType EXPORTS_STATEMENT = new JavaPackageAccessibilityStatementElementType("EXPORTS_STATEMENT");
  JavaPackageAccessibilityStatementElementType OPENS_STATEMENT = new JavaPackageAccessibilityStatementElementType("OPENS_STATEMENT");

  JavaRecordComponentElementType RECORD_COMPONENT = new JavaRecordComponentElementType();
  JavaRecordHeaderElementType RECORD_HEADER = new JavaRecordHeaderElementType();

  JavaClassElementType CLASS = new JavaClassElementType("CLASS") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new ClassElement(this);
    }
  };
  JavaClassElementType ANONYMOUS_CLASS = new JavaClassElementType("ANONYMOUS_CLASS") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new AnonymousClassElement();
    }
  };
  JavaClassElementType IMPLICIT_CLASS = new JavaClassElementType("IMPLICIT_CLASS") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new ImplicitClassElement();
    }

    @Override
    public void indexStub(@Nonnull PsiClassStub stub, @Nonnull IndexSink sink) {
      StubElement parent = stub.getParentStub();
      if (parent instanceof PsiJavaFileStub) {
        sink.occurrence(JavaStubIndexKeys.IMPLICIT_CLASSES, JavaImplicitClassUtil.getJvmName(((PsiJavaFileStub)parent).getPsi().getName()));
      }
    }
  };
  JavaClassElementType ENUM_CONSTANT_INITIALIZER = new JavaClassElementType("ENUM_CONSTANT_INITIALIZER") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new EnumConstantInitializerElement();
    }
  };

  JavaMethodElementType METHOD = new JavaMethodElementType("METHOD") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new MethodElement();
    }
  };
  JavaMethodElementType ANNOTATION_METHOD = new JavaMethodElementType("ANNOTATION_METHOD") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new AnnotationMethodElement();
    }
  };

  JavaFieldStubElementType FIELD = new JavaFieldStubElementType("FIELD") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new FieldElement();
    }
  };
  JavaFieldStubElementType ENUM_CONSTANT = new JavaFieldStubElementType("ENUM_CONSTANT") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new EnumConstantElement();
    }
  };

  JavaClassReferenceListElementType EXTENDS_LIST = new JavaClassReferenceListElementType("EXTENDS_LIST") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new ReferenceListElement(this, JavaTokenType.EXTENDS_KEYWORD, PsiKeyword.EXTENDS);
    }
  };
  JavaClassReferenceListElementType PERMITS_LIST = new JavaClassReferenceListElementType("PERMITS_LIST") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new ReferenceListElement(this, JavaTokenType.PERMITS_KEYWORD, PsiKeyword.PERMITS);
    }
  };
  JavaClassReferenceListElementType IMPLEMENTS_LIST = new JavaClassReferenceListElementType("IMPLEMENTS_LIST") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new ReferenceListElement(this, JavaTokenType.IMPLEMENTS_KEYWORD, PsiKeyword.IMPLEMENTS);
    }
  };
  JavaClassReferenceListElementType THROWS_LIST = new JavaClassReferenceListElementType("THROWS_LIST") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new ReferenceListElement(this, JavaTokenType.THROWS_KEYWORD, PsiKeyword.THROWS);
    }
  };
  JavaClassReferenceListElementType EXTENDS_BOUND_LIST = new JavaClassReferenceListElementType("EXTENDS_BOUND_LIST") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new TypeParameterExtendsBoundsListElement();
    }
  };
  JavaClassReferenceListElementType PROVIDES_WITH_LIST = new JavaClassReferenceListElementType("PROVIDES_WITH_LIST") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new ReferenceListElement(this, JavaTokenType.WITH_KEYWORD, PsiKeyword.WITH);
    }
  };

  JavaImportStatementElementType IMPORT_STATEMENT = new JavaImportStatementElementType("IMPORT_STATEMENT") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new ImportStatementElement();
    }
  };
  JavaImportStatementElementType IMPORT_STATIC_STATEMENT = new JavaImportStatementElementType("IMPORT_STATIC_STATEMENT") {
    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return new ImportStaticStatementElement();
    }
  };

  IStubFileElementType JAVA_FILE = new JavaFileElementType();
}