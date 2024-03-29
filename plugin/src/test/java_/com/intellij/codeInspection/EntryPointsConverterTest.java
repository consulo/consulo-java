package com.intellij.codeInspection;

import com.intellij.java.impl.codeInspection.ex.EntryPointsManagerImpl;
import consulo.ide.impl.idea.codeInspection.reference.SmartRefElementPointer;
import consulo.util.xml.serializer.JDOMExternalizableStringList;
import consulo.util.jdom.JDOMUtil;
import java.util.HashMap;
import junit.framework.TestCase;
import org.jdom.Element;

public abstract class EntryPointsConverterTest extends TestCase {
  public void testMethodConverter1() throws Exception {
    doTest("method", "String java.lang.String.replace(char oldChar, char newChar)", "java.lang.String String replace(char oldChar, char newChar)");
  }

  public void testMethodConverter2() throws Exception {
    doTest("method", "void java.lang.String.replace(char oldChar, char newChar)", "java.lang.String void replace(char oldChar, char newChar)");
  }

  public void testMethodConverter3() throws Exception {
    doTest("method", "java.lang.String.String(char oldChar)", "java.lang.String String(char oldChar)");
  }

  public void testFieldConverter() throws Exception {
    doTest("field", "java.lang.String.myFakeField", "java.lang.String myFakeField");
  }

  private static void doTest(String type, String fqName, String expectedFQName) throws Exception {
    final Element entryPoints = setUpEntryPoint(type, fqName);

    final HashMap<String, SmartRefElementPointer> persistentEntryPoints = new HashMap<String, SmartRefElementPointer>();
    EntryPointsManagerImpl.convert(entryPoints, persistentEntryPoints);

    final Element testElement = new Element("comp");
    EntryPointsManagerImpl.writeExternal(testElement, persistentEntryPoints, new JDOMExternalizableStringList());

    final Element expectedEntryPoints = setUpEntryPoint(type, expectedFQName);
    expectedEntryPoints.setAttribute("version", "2.0");
    final Element expected = new Element("comp");
    expected.addContent(expectedEntryPoints);

    assertTrue(JDOMUtil.areElementsEqual(testElement, expected));
  }

  private static Element setUpEntryPoint(String type, String fqName) {
    Element entryPoints = new Element("entry_points");
    Element entryPoint = new Element("entry_point");
    entryPoint.setAttribute("TYPE", type);
    entryPoint.setAttribute("FQNAME", fqName);
    entryPoints.addContent(entryPoint);
    return entryPoints;
  }

}
