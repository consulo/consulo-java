/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.psi.impl.search;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.source.JavaLightTreeUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiKeyword;
import consulo.application.ApplicationManager;
import consulo.application.util.StringSearcher;
import consulo.index.io.DataIndexer;
import consulo.index.io.EnumeratorStringDescriptor;
import consulo.index.io.ID;
import consulo.index.io.KeyDescriptor;
import consulo.index.io.data.DataInputOutputUtil;
import consulo.language.ast.*;
import consulo.language.psi.stub.*;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

import static com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType.*;

public class JavaNullMethodArgumentIndex extends ScalarIndexExtension<JavaNullMethodArgumentIndex.MethodCallData> {
  private static final Logger LOG = Logger.getInstance(JavaNullMethodArgumentIndex.class);

  public static final ID<MethodCallData, Void> INDEX_ID = ID.create("java.null.method.argument");
  private static final TokenSet CALL_TYPES = TokenSet.create(METHOD_CALL_EXPRESSION, NEW_EXPRESSION, ANONYMOUS_CLASS);
  private boolean myOfflineMode = ApplicationManager.getApplication().isCommandLine() && !ApplicationManager.getApplication().isUnitTestMode();

  @Nonnull
  @Override
  public ID<MethodCallData, Void> getName() {
    return INDEX_ID;
  }

  @Nonnull
  @Override
  public DataIndexer<MethodCallData, Void, FileContent> getIndexer() {
    return inputData ->
    {
      if (myOfflineMode) {
        return Collections.emptyMap();
      }

      int[] nullOffsets = new StringSearcher(PsiKeyword.NULL, true, true).findAllOccurrences(inputData.getContentAsText());
      if (nullOffsets.length == 0) {
        return Collections.emptyMap();
      }

      LighterAST lighterAst = ((PsiDependentFileContent) inputData).getLighterAST();
      Set<LighterASTNode> calls = findCallsWithNulls(lighterAst, nullOffsets);
      if (calls.isEmpty()) {
        return Collections.emptyMap();
      }

      Map<MethodCallData, Void> result = new HashMap<>();
      for (LighterASTNode element : calls) {
        final IntList indices = getNullParameterIndices(lighterAst, element);
        if (indices != null) {
          final String name = getMethodName(lighterAst, element, element.getTokenType());
          if (name != null) {
            for (int i = 0; i < indices.size(); i++) {
              result.put(new MethodCallData(name, indices.get(i)), null);
            }
          }
        }
      }
      return result;
    };
  }

  @Nonnull
  private static Set<LighterASTNode> findCallsWithNulls(LighterAST lighterAst, int[] nullOffsets) {
    Set<LighterASTNode> calls = new HashSet<>();
    for (int offset : nullOffsets) {
      LighterASTNode leaf = LightTreeUtil.findLeafElementAt(lighterAst, offset);
      LighterASTNode literal = leaf == null ? null : lighterAst.getParent(leaf);
      if (isNullLiteral(lighterAst, literal)) {
        LighterASTNode exprList = lighterAst.getParent(literal);
        if (exprList != null && exprList.getTokenType() == EXPRESSION_LIST) {
          ContainerUtil.addIfNotNull(calls, LightTreeUtil.getParentOfType(lighterAst, exprList, CALL_TYPES, ElementType.MEMBER_BIT_SET));
        }
      }
    }
    return calls;
  }

  @Nullable
  private static IntList getNullParameterIndices(LighterAST lighterAst, @Nonnull LighterASTNode methodCall) {
    final LighterASTNode node = LightTreeUtil.firstChildOfType(lighterAst, methodCall, EXPRESSION_LIST);
    if (node == null) {
      return null;
    }
    final List<LighterASTNode> parameters = JavaLightTreeUtil.getExpressionChildren(lighterAst, node);
    IntList indices = IntLists.newArrayList(1);
    for (int idx = 0; idx < parameters.size(); idx++) {
      if (isNullLiteral(lighterAst, parameters.get(idx))) {
        indices.add(idx);
      }
    }
    return indices;
  }

  private static boolean isNullLiteral(LighterAST lighterAst, @Nullable LighterASTNode expr) {
    return expr != null && expr.getTokenType() == LITERAL_EXPRESSION && lighterAst.getChildren(expr).get(0).getTokenType() == JavaTokenType.NULL_KEYWORD;
  }

  @Nullable
  private static String getMethodName(LighterAST lighterAst, @Nonnull LighterASTNode call, IElementType elementType) {
    if (elementType == NEW_EXPRESSION || elementType == ANONYMOUS_CLASS) {
      final List<LighterASTNode> refs = LightTreeUtil.getChildrenOfType(lighterAst, call, JAVA_CODE_REFERENCE);
      if (refs.isEmpty()) {
        return null;
      }
      final LighterASTNode lastRef = refs.get(refs.size() - 1);
      return JavaLightTreeUtil.getNameIdentifierText(lighterAst, lastRef);
    }

    LOG.assertTrue(elementType == METHOD_CALL_EXPRESSION);
    final LighterASTNode methodReference = lighterAst.getChildren(call).get(0);
    if (methodReference.getTokenType() == REFERENCE_EXPRESSION) {
      return JavaLightTreeUtil.getNameIdentifierText(lighterAst, methodReference);
    }
    return null;
  }

  @Nonnull
  @Override
  public KeyDescriptor<MethodCallData> getKeyDescriptor() {
    return new KeyDescriptor<MethodCallData>() {
      @Override
      public void save(@Nonnull DataOutput out, MethodCallData value) throws IOException {
        EnumeratorStringDescriptor.INSTANCE.save(out, value.getMethodName());
        DataInputOutputUtil.writeINT(out, value.getNullParameterIndex());
      }

      @Override
      public MethodCallData read(@Nonnull DataInput in) throws IOException {
        return new MethodCallData(EnumeratorStringDescriptor.INSTANCE.read(in), DataInputOutputUtil.readINT(in));
      }
    };
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @Nonnull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@Nullable Project project, @Nonnull VirtualFile file) {
        return JavaStubElementTypes.JAVA_FILE.shouldBuildStubFor(file);
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  public static final class MethodCallData {
    @Nonnull
    private final String myMethodName;
    private final int myNullParameterIndex;

    public MethodCallData(@Nonnull String name, int index) {
      myMethodName = name;
      myNullParameterIndex = index;
    }

    @Nonnull
    public String getMethodName() {
      return myMethodName;
    }

    public int getNullParameterIndex() {
      return myNullParameterIndex;
    }


    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      MethodCallData data = (MethodCallData) o;

      if (myNullParameterIndex != data.myNullParameterIndex) {
        return false;
      }
      if (!myMethodName.equals(data.myMethodName)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = myMethodName.hashCode();
      result = 31 * result + myNullParameterIndex;
      return result;
    }

    @Override
    public String toString() {
      return "MethodCallData{" + "myMethodName='" + myMethodName + '\'' + ", myNullParameterIndex=" + myNullParameterIndex + '}';
    }
  }
}
