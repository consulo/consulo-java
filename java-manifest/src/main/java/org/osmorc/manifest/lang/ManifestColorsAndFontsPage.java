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

import consulo.annotation.component.ExtensionImpl;
import consulo.colorScheme.TextAttributesKey;
import consulo.colorScheme.setting.AttributesDescriptor;
import consulo.colorScheme.setting.ColorDescriptor;
import consulo.java.manifest.lang.ManifestLanguageVersion;
import consulo.language.editor.colorScheme.setting.ColorSettingsPage;
import consulo.language.editor.highlight.SyntaxHighlighter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
@ExtensionImpl
public class ManifestColorsAndFontsPage implements ColorSettingsPage {

  private static final AttributesDescriptor[] ATTRIBUTE_DESCRIPTORS;
  private static final Map<String, TextAttributesKey> ADDITIONAL_HIGHLIGHTING;

  static {
    ATTRIBUTE_DESCRIPTORS = new AttributesDescriptor[]{
        new AttributesDescriptor("Header name", ManifestColorsAndFonts.HEADER_NAME_KEY),
        new AttributesDescriptor("Header assignment", ManifestColorsAndFonts.HEADER_ASSIGNMENT_KEY),
        new AttributesDescriptor("Header value", ManifestColorsAndFonts.HEADER_VALUE_KEY),
        new AttributesDescriptor("Directive name", ManifestColorsAndFonts.DIRECTIVE_NAME_KEY),
        new AttributesDescriptor("Directive assignment", ManifestColorsAndFonts.DIRECTIVE_ASSIGNMENT_KEY),
        new AttributesDescriptor("Attribute name", ManifestColorsAndFonts.ATTRIBUTE_NAME_KEY),
        new AttributesDescriptor("Attribute assignment", ManifestColorsAndFonts.ATTRIBUTE_ASSIGNMENT_KEY),
        new AttributesDescriptor("Clause separator", ManifestColorsAndFonts.CLAUSE_SEPARATOR_KEY),
        new AttributesDescriptor("Parameter separator", ManifestColorsAndFonts.PARAMETER_SEPARATOR_KEY)
    };

    ADDITIONAL_HIGHLIGHTING = new HashMap<String, TextAttributesKey>();
    ADDITIONAL_HIGHLIGHTING.put("headerAssignment", ManifestColorsAndFonts.HEADER_ASSIGNMENT_KEY);
    ADDITIONAL_HIGHLIGHTING.put("directiveName", ManifestColorsAndFonts.DIRECTIVE_NAME_KEY);
    ADDITIONAL_HIGHLIGHTING.put("directiveAssignment", ManifestColorsAndFonts.DIRECTIVE_ASSIGNMENT_KEY);
    ADDITIONAL_HIGHLIGHTING.put("attributeName", ManifestColorsAndFonts.ATTRIBUTE_NAME_KEY);
    ADDITIONAL_HIGHLIGHTING.put("attributeAssignment", ManifestColorsAndFonts.ATTRIBUTE_ASSIGNMENT_KEY);
    ADDITIONAL_HIGHLIGHTING.put("clauseSeparator", ManifestColorsAndFonts.CLAUSE_SEPARATOR_KEY);
    ADDITIONAL_HIGHLIGHTING.put("parameterSeparator", ManifestColorsAndFonts.PARAMETER_SEPARATOR_KEY);
  }

  @Nonnull
  public String getDisplayName() {
    return "Manifest & Bnd";
  }

  @Nonnull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRIBUTE_DESCRIPTORS;
  }

  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ADDITIONAL_HIGHLIGHTING;
  }

  @Nonnull
  public ColorDescriptor[] getColorDescriptors() {
    return new ColorDescriptor[0];
  }

  @Nonnull
  public SyntaxHighlighter getHighlighter() {
    return new ManifestSyntaxHighlighter(ManifestLanguageVersion.Bnd);
  }

  @NonNls
  @Nonnull
  public String getDemoText() {
    return "# Bnd comment\n" +
        "Manifest-Version<headerAssignment>:</headerAssignment> 1.0\n" +
        "Bundle-ManifestVersion<headerAssignment>:</headerAssignment> 2\n" +
        "Bundle-Name<headerAssignment>:</headerAssignment> Manifest Test\n" +
        "Bundle-SymbolicName<headerAssignment>:</headerAssignment> org.manifest.test<parameterSeparator>;</parameterSeparator> <directiveName>singleton</directiveName><directiveAssignment>:=</directiveAssignment>true\n" +
        "Bundle-Version<headerAssignment>:</headerAssignment> 0.1.0\n" +
        "Require-Bundle<headerAssignment>:</headerAssignment> some.bundle<parameterSeparator>;</parameterSeparator><attributeName>bundle-version</attributeName><attributeAssignment>=</attributeAssignment>2.0.0<clauseSeparator>,</clauseSeparator>\n" +
        " other.bundle";
  }
}
