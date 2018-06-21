/*
 * @author max
 */
package com.intellij.psi;

import static org.junit.Assert.assertEquals;

import java.text.StringCharacterIterator;

import com.intellij.psi.impl.compiled.SignatureParsing;
import junit.framework.TestCase;

public abstract class SignatureParsingTest extends TestCase {
  public void testVarianceAmbiguity() throws Exception {
    assertEquals("Psi<?,P>", SignatureParsing.parseTypeString(new StringCharacterIterator("LPsi<*TP>;"), s -> s));
    assertEquals("Psi<? extends P>", SignatureParsing.parseTypeString(new StringCharacterIterator("LPsi<+TP>;"), s -> s));
    assertEquals("Psi<? super P>", SignatureParsing.parseTypeString(new StringCharacterIterator("LPsi<-TP>;"), s -> s));
  }
}
