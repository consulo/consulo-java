package org.jetbrains.manifest;

import com.intellij.testFramework.ParsingTestCase;
import org.osmorc.manifest.lang.ManifestParserDefinition;
import consulo.java.manifest.lang.headerparser.HeaderParserEP;
import org.osmorc.manifest.lang.headerparser.impl.GenericComplexHeaderParser;

/**
 * @author VISTALL
 * @since 9:14/04.05.13
 */
public class ManifestParsingTest extends ParsingTestCase {
  public ManifestParsingTest() {
    super("parsing", "MF", new ManifestParserDefinition());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    registerExtensionPoint(HeaderParserEP.EP_NAME, HeaderParserEP.class);

    registerExtension(HeaderParserEP.EP_NAME, new HeaderParserEP("", GenericComplexHeaderParser.class));
  }

  public void testColonEq() {
    doTest(true);
  }

  @Override
  protected String getTestDataPath() {
    return "testData";
  }

  @Override
  protected boolean shouldContainTempFiles() {
    return false;
  }
}
