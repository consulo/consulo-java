/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.indexing.impl.search;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.psi.*;
import consulo.index.io.DataIndexer;
import consulo.index.io.EnumeratorStringDescriptor;
import consulo.index.io.ID;
import consulo.index.io.KeyDescriptor;
import consulo.index.io.data.DataExternalizer;
import consulo.index.io.data.DataInputOutputUtil;
import consulo.language.impl.internal.psi.stub.FileContentImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.stub.DefaultFileTypeSpecificInputFilter;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.FileBasedIndexExtension;
import consulo.language.psi.stub.FileContent;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

public class JavaFunctionalExpressionIndex extends FileBasedIndexExtension<String, Collection<JavaFunctionalExpressionIndex.IndexHolder>> {
  public static final ID<String, Collection<IndexHolder>> JAVA_FUNCTIONAL_EXPRESSION_INDEX_ID = ID.create("java.functional.expression");
  private static final String THIS_REF_NAME = "this";
  private static final String SUPER_REF_NAME = "super";

  @Nonnull
  @Override
  public ID<String, Collection<IndexHolder>> getName() {
    return JAVA_FUNCTIONAL_EXPRESSION_INDEX_ID;
  }

  @Nonnull
  @Override
  public DataIndexer<String, Collection<IndexHolder>, FileContent> getIndexer() {
    return new DataIndexer<String, Collection<IndexHolder>, FileContent>() {
      @Nonnull
      @Override
      public Map<String, Collection<IndexHolder>> map(@Nonnull FileContent inputData) {
        if (!JavaStubElementTypes.JAVA_FILE.shouldBuildStubFor(inputData.getFile())) {
          return Collections.emptyMap();
        }
        final CharSequence contentAsText = inputData.getContentAsText();
        if (!StringUtil.contains(contentAsText, "::") && !StringUtil.contains(contentAsText, "->")) {
          return Collections.emptyMap();
        }

        final PsiFile file = ((FileContentImpl) inputData).getPsiFileForPsiDependentIndex();
        if (!(file instanceof PsiJavaFile)) {
          return Collections.emptyMap();
        }

        final HashMap<String, Collection<IndexHolder>> methodsMap = new HashMap<>();
        for (PsiFunctionalExpression expression : SyntaxTraverser.psiTraverser().withRoot(file).filter(PsiFunctionalExpression.class)) {
          final PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class, true, PsiStatement.class, PsiModifierListOwner.class);
          if (expressionList != null) {
            final PsiElement parent = expressionList.getParent();
            String methodName = null;
            if (parent instanceof PsiMethodCallExpression) {
              methodName = ((PsiMethodCallExpression) parent).getMethodExpression().getReferenceName();
              if (methodName != null) {
                final boolean thisRef = methodName.equals(THIS_REF_NAME);
                if (thisRef || methodName.equals(SUPER_REF_NAME)) {
                  methodName = null;
                  final PsiClass containingClass = PsiTreeUtil.getParentOfType(parent, PsiClass.class);
                  if (containingClass != null) {
                    if (thisRef) {
                      methodName = containingClass.getName();
                    } else {
                      final PsiReferenceList extendsList = containingClass.getExtendsList();
                      if (extendsList != null) {
                        final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
                        if (referenceElements.length > 0) {
                          methodName = referenceElements[0].getReferenceName();
                        }
                      }
                    }
                  }
                }
              }
            } else if (parent instanceof PsiNewExpression) {
              final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression) parent).getClassOrAnonymousClassReference();
              if (classReference != null) {
                methodName = classReference.getReferenceName();
              }
            } else if (parent instanceof PsiEnumConstant) {
              final PsiClass containingClass = ((PsiEnumConstant) parent).getContainingClass();
              if (containingClass != null) {
                final String shortEnumName = containingClass.getName();
                if (shortEnumName != null) { //should be always true as enums can't be local
                  methodName = shortEnumName;
                }
              }
            }

            if (methodName != null) {
              Collection<IndexHolder> holders = methodsMap.get(methodName);
              if (holders == null) {
                holders = new HashSet<IndexHolder>();
                methodsMap.put(methodName, holders);
              }
              holders.add(new IndexHolder(expression instanceof PsiLambdaExpression ? ((PsiLambdaExpression) expression).getParameterList().getParametersCount() : -1,
                  expressionList.getExpressions().length, LambdaUtil.getLambdaIdx(expressionList, expression)));
            }
          }
        }

        return methodsMap;
      }
    };
  }

  @Nonnull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Nonnull
  @Override
  public DataExternalizer<Collection<IndexHolder>> getValueExternalizer() {
    return new DataExternalizer<Collection<IndexHolder>>() {
      @Override
      public void save(@Nonnull DataOutput out, Collection<IndexHolder> holders) throws IOException {
        DataInputOutputUtil.writeINT(out, holders.size());
        for (IndexHolder holder : holders) {
          DataInputOutputUtil.writeINT(out, holder.getLambdaParamsNumber());
          DataInputOutputUtil.writeINT(out, holder.getMethodArgsLength());
          DataInputOutputUtil.writeINT(out, holder.getFunctionExpressionIndex());
        }
      }

      @Override
      public Collection<IndexHolder> read(@Nonnull DataInput in) throws IOException {
        int l = DataInputOutputUtil.readINT(in);
        final Collection<IndexHolder> holders = new HashSet<IndexHolder>(l);
        while (l-- > 0) {
          holders.add(new IndexHolder(DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in)));
        }
        return holders;
      }
    };
  }

  @Nonnull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  public static class IndexHolder {
    private final int myLambdaParamsNumber;
    private final int myMethodArgsLength;
    private final int myFunctionExpressionIndex;

    public IndexHolder(int lambdaParamsNumber, int methodArgsLength, int functionExpressionIndex) {
      myLambdaParamsNumber = lambdaParamsNumber;
      myMethodArgsLength = methodArgsLength;
      myFunctionExpressionIndex = functionExpressionIndex;
    }

    public int getLambdaParamsNumber() {
      return myLambdaParamsNumber;
    }

    public int getMethodArgsLength() {
      return myMethodArgsLength;
    }

    public int getFunctionExpressionIndex() {
      return myFunctionExpressionIndex;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      IndexHolder holder = (IndexHolder) o;

      if (myLambdaParamsNumber != holder.myLambdaParamsNumber) {
        return false;
      }
      if (myMethodArgsLength != holder.myMethodArgsLength) {
        return false;
      }
      if (myFunctionExpressionIndex != holder.myFunctionExpressionIndex) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = myLambdaParamsNumber;
      result = 31 * result + myMethodArgsLength;
      result = 31 * result + myFunctionExpressionIndex;
      return result;
    }
  }
}
