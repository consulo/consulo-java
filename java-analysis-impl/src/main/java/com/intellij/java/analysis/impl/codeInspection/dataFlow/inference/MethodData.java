/*
 * Copyright 2013-2017 consulo.io
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

package com.intellij.java.analysis.impl.codeInspection.dataFlow.inference;

import com.intellij.java.language.impl.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.java.language.impl.psi.impl.source.PsiMethodImpl;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiMethod;
import consulo.document.Document;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.stub.gist.GistManager;
import consulo.language.util.IncorrectOperationException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * from kotlin
 */
class MethodData {
  private final MethodReturnInferenceResult methodReturn;
  private final PurityInferenceResult purity;
  private final List<PreContract> contracts;
  private final BitSet notNullParameters;
  private final int bodyStart;
  private final int bodyEnd;

  private volatile PsiCodeBlock myDetachedBody;

  MethodData(MethodReturnInferenceResult methodReturn, PurityInferenceResult purity, List<PreContract> contracts, BitSet notNullParameters, int bodyStart, int bodyEnd) {
    this.methodReturn = methodReturn;
    this.purity = purity;
    this.contracts = contracts;
    this.notNullParameters = notNullParameters;
    this.bodyStart = bodyStart;
    this.bodyEnd = bodyEnd;
  }

  @Nullable
  public MethodReturnInferenceResult getMethodReturn() {
    return methodReturn;
  }

  public PurityInferenceResult getPurity() {
    return purity;
  }

  public List<PreContract> getContracts() {
    return contracts;
  }

  public BitSet getNotNullParameters() {
    return notNullParameters;
  }

  public int getBodyStart() {
    return bodyStart;
  }

  public int getBodyEnd() {
    return bodyEnd;
  }

  @Nonnull
  public Supplier<PsiCodeBlock> methodBody(PsiMethodImpl method) {
    return () -> {
      PsiMethodStub stub = method.getStub();
      if (stub != null) {
        PsiCodeBlock detachedBody = this.myDetachedBody;
        if (detachedBody == null) {
          detachedBody = getDetachedBody(method);
          myDetachedBody = detachedBody;
        } else {
          assert detachedBody.getParent() == method || detachedBody.getContainingFile().getContext() == method;
        }

        return detachedBody;
      } else {
        return method.getBody();
      }
    };
  }

  private PsiCodeBlock getDetachedBody(PsiMethod method) {
    Document document = method.getContainingFile().getViewProvider().getDocument();
    if (document == null) {
      return method.getBody();
    }
    try {
      CharSequence bodyText = PsiDocumentManager.getInstance(method.getProject()).getLastCommittedText(document).subSequence(bodyStart, bodyEnd);
      return JavaPsiFacade.getElementFactory(method.getProject()).createCodeBlockFromText(bodyText, method);
    } catch (PsiInvalidElementAccessException | IncorrectOperationException e) {
      GistManager.getInstance().invalidateData();
      throw e;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MethodData that = (MethodData) o;
    return bodyStart == that.bodyStart &&
        bodyEnd == that.bodyEnd &&
        Objects.equals(methodReturn, that.methodReturn) &&
        Objects.equals(purity, that.purity) &&
        Objects.equals(contracts, that.contracts) &&
        Objects.equals(notNullParameters, that.notNullParameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(methodReturn, purity, contracts, notNullParameters, bodyStart, bodyEnd);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("MethodData{");
    sb.append("methodReturn=").append(methodReturn);
    sb.append(", purity=").append(purity);
    sb.append(", contracts=").append(contracts);
    sb.append(", notNullParameters=").append(notNullParameters);
    sb.append(", bodyStart=").append(bodyStart);
    sb.append(", bodyEnd=").append(bodyEnd);
    sb.append('}');
    return sb.toString();
  }
}
