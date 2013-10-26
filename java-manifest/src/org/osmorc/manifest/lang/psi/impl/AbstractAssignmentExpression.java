/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.osmorc.manifest.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.osmorc.manifest.lang.ManifestFileType;
import org.osmorc.manifest.lang.psi.AssignmentExpression;
import org.osmorc.manifest.lang.psi.Directive;
import org.osmorc.manifest.lang.psi.HeaderValuePart;
import org.osmorc.manifest.lang.psi.stub.AssignmentExpressionStub;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public abstract class AbstractAssignmentExpression extends ManifestElementBase<AssignmentExpressionStub> implements AssignmentExpression {

  protected AbstractAssignmentExpression(AssignmentExpressionStub stub, @NotNull IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public AbstractAssignmentExpression(@NotNull ASTNode node) {
    super(node);
  }

  public String getName() {
    String result;
    AssignmentExpressionStub stub = getStub();
    if (stub != null) {
      result = stub.getName();
    }
    else {
      HeaderValuePart namePsi = getNamePsi();
      result = namePsi != null ? namePsi.getUnwrappedText() : null;
    }

    return result != null ? result : "<unnamed>";
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiFile fromText = PsiFileFactory.getInstance(getProject())
      .createFileFromText("DUMMY.MF", ManifestFileType.INSTANCE, String.format("Dummy: dummy;%s:=%s\n", name, getValue()));

    Directive directive = PsiTreeUtil.findChildOfType(fromText, Directive.class);

    assert directive != null;

    getNamePsi().replace(directive.getNamePsi());
    return this;
  }

  @Override
  public void setValue(@NotNull String value) {
    final String oldValue = getValue();
    String dummyTemplate;
    if(oldValue.endsWith("\n")) {
      dummyTemplate = "Dummy: dummy;%s:=%s\n";
    }
    else {
      dummyTemplate = "Dummy: dummy;%s:=%s";
    }
    PsiFile fromText = PsiFileFactory.getInstance(getProject())
      .createFileFromText("DUMMY.MF", ManifestFileType.INSTANCE, String.format(dummyTemplate, getName(), value));

    Directive directive = PsiTreeUtil.findChildOfType(fromText, Directive.class);

    assert directive != null;

    HeaderValuePart valuePsi = getValuePsi();
    if(valuePsi == null) {
      replace(directive);
    }
    else {
      valuePsi.replace(directive.getValuePsi());
    }
  }

  public HeaderValuePart getNamePsi() {
    return PsiTreeUtil.getChildOfType(this, HeaderValuePart.class);
  }

  public String getValue() {
    String result;
    AssignmentExpressionStub stub = getStub();
    if (stub != null) {
      result = stub.getValue();
    }
    else {
      HeaderValuePart valuePsi = getValuePsi();
      result = valuePsi != null ? valuePsi.getUnwrappedText() : null;
    }

    return result != null ? result : "";
  }

  public HeaderValuePart getValuePsi() {
    HeaderValuePart namePsi = getNamePsi();

    return namePsi != null ? PsiTreeUtil.getNextSiblingOfType(namePsi, HeaderValuePart.class) : null;
  }
}
