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
package org.osmorc.manifest.lang;

import javax.annotation.Nonnull;

import org.osmorc.manifest.lang.psi.Header;
import org.osmorc.manifest.lang.psi.ManifestStubElementTypes;
import org.osmorc.manifest.lang.psi.elementtype.AbstractManifestStubElementType;
import org.osmorc.manifest.lang.psi.impl.ManifestFileImpl;
import consulo.language.ast.ASTNode;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.ast.IElementType;
import consulo.language.ast.IFileElementType;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.version.LanguageVersionableParserDefinition;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class ManifestParserDefinition extends LanguageVersionableParserDefinition
{
  @Nonnull
  @Override
  public IFileElementType getFileNodeType() {
    return ManifestStubElementTypes.FILE;
  }

  @Override
  @Nonnull
  public PsiElement createElement(ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof AbstractManifestStubElementType) {
      return ((AbstractManifestStubElementType)type).createPsi(node);
    }

    return PsiUtil.NULL_PSI_ELEMENT;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new ManifestFileImpl(viewProvider, ManifestLanguage.INSTANCE, ManifestFileType.INSTANCE);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return (left.getPsi() instanceof Header || right.getPsi() instanceof Header)
           ? SpaceRequirements.MUST_LINE_BREAK
           : SpaceRequirements.MUST_NOT;
  }
}
