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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.Nonnull;

import org.osmorc.manifest.lang.ManifestFileType;
import org.osmorc.manifest.lang.ManifestTokenType;
import org.osmorc.manifest.lang.psi.Clause;
import org.osmorc.manifest.lang.psi.Header;
import org.osmorc.manifest.lang.psi.ManifestFile;
import org.osmorc.manifest.lang.psi.Section;
import consulo.language.impl.psi.PsiFileBase;
import consulo.language.Language;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.util.PsiTreeUtil;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class ManifestFileImpl extends PsiFileBase implements ManifestFile {
  private final FileType myFileType;

  public ManifestFileImpl(@Nonnull FileViewProvider viewProvider, @Nonnull Language language, @Nonnull FileType fileType) {
    super(viewProvider, language);
    myFileType = fileType;
  }

  @Nonnull
  public FileType getFileType() {
    return myFileType;
  }

  @Override
  public String toString() {
    return "ManifestFile:" + getName();
  }

  @Nonnull
  @Override
  public Header[] getHeaders() {
    Header[] childrenOfType = PsiTreeUtil.getChildrenOfType(getFirstChild(), Header.class);
    return childrenOfType == null ? Header.EMPTY_ARRAY : childrenOfType;
  }

  @Override
  public Header getHeaderByName(@Nonnull String name) {

    Header childOfType = PsiTreeUtil.findChildOfType(getFirstChild(), Header.class);
    while (childOfType != null) {
      if (name.equals(childOfType.getName())) {
        return childOfType;
      }
      childOfType = PsiTreeUtil.getNextSiblingOfType(childOfType, Header.class);
    }
    return null;
  }

  @Override
  public Object getValueByKey(@Nonnull String key) {
    Header header = getHeaderByName(key);
    if (header == null) {
      return null;
    }
    return header.getSimpleConvertedValue();
  }

  @Override
  public String getStringValueByKey(@Nonnull String key) {
    Header header = getHeaderByName(key);
    if (header == null) {
      return null;
    }
    final Object value = header.getSimpleConvertedValue();
    return value instanceof String ? (String)value : null;
  }

  @Nonnull
  @Override
  public List<String> getValuesByKey(@Nonnull String key) {
    Header header = getHeaderByName(key);
    if (header == null) {
      return Collections.emptyList();
    }
    Clause[] clauses = header.getClauses();
    if(clauses.length == 0) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<String>(clauses.length);
    for (Clause clause : clauses) {
      result.add(clause.getClauseText());
    }
    return result;
  }

  @Override
  public void setHeaderValue(@Nonnull String key, @Nonnull String value) {
    PsiFile fromText = PsiFileFactory.getInstance(getProject())
      .createFileFromText("DUMMY.MF", ManifestFileType.INSTANCE, String.format("%s: %s\n", key, value));

    Header newHeader = PsiTreeUtil.getChildOfType(fromText.getFirstChild(), Header.class);

    assert newHeader != null;

    Header headerByName = getHeaderByName(key);
    if (headerByName == null) {
      Section section = (Section)getFirstChild();

      String sectionText = section.getText();
      if (sectionText.charAt(sectionText.length() - 1) != '\n') {
        PsiElement lastChild = section.getLastChild();
        if (lastChild instanceof Header) {
          Header header = (Header)lastChild;
          header.getNode().addLeaf(ManifestTokenType.NEWLINE, "\n", null);
        }
      }

      section.add(newHeader);
    }
    else {
      headerByName.replace(newHeader);
    }
  }
}
