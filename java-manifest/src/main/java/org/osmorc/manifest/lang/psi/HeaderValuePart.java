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

package org.osmorc.manifest.lang.psi;

import jakarta.annotation.Nonnull;

import consulo.language.psi.PsiElement;
import consulo.language.psi.StubBasedPsiElement;

import jakarta.annotation.Nullable;
import org.osmorc.manifest.lang.psi.stub.HeaderValuePartStub;

/**
 * A header value part is in the case of a simple header the whole value of a header or in the
 * complex case the building block for attributes, diretives and clauses. It can the the directive or attribute name.
 * It can be the value of an attribute or directive or it can be the main part of a clause to which parameters are
 * applied in the form of directives and clauses.
 *
 * @author Robert F. Beeger (robert@beeger.net)
 */
public interface HeaderValuePart extends PsiElement, StubBasedPsiElement<HeaderValuePartStub> {

  /**
   * The text of a header value part can be broken into several parts. This method returns the unwrapped text without
   * the newlines and without the extra continuation spaces.
   *
   * @return The unwrapped text.
   */
  @Nonnull
  String getUnwrappedText();

  void setText(@Nonnull String text);

  /**
   * Returns the converted value of this header (.e.g if the header represents a version statement, this will return a {@link org.osmorc.valueobject.Version} object.
   *
   * @return the converted value. or null if no conversion for this header value could be found.
   */
  @Nullable
  Object getConvertedValue();
}
