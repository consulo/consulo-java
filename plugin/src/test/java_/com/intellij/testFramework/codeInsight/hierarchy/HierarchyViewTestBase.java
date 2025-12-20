package com.intellij.testFramework.codeInsight.hierarchy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.jdom.Document;
import org.jdom.Element;
import jakarta.annotation.Nullable;
import com.intellij.codeInsight.CodeInsightTestCase;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyTreeStructure;
import consulo.application.util.function.Computable;
import consulo.util.jdom.JDOMUtil;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;

/**
 * Checks tree structure for Type Hierarchy (Ctrl+H), Call Hierarchy (Ctrl+Alt+H), Method Hierarchy (Ctrl+Shift+H).
 */
public abstract class HierarchyViewTestBase extends CodeInsightTestCase {

  private static final String NODE_ELEMENT_NAME = "node";
  private static final String ANY_NODES_ELEMENT_NAME = "any";
  private static final String TEXT_ATTR_NAME = "text";
  private static final String BASE_ATTR_NAME = "base";

  protected abstract String getBasePath();

  protected void doHierarchyTest(Computable<HierarchyTreeStructure> treeStructureComputable, String... fileNames)
    throws Exception {
    String[] relFilePaths = new String[fileNames.length];
    for (int i = 0; i < fileNames.length; i++) {
      relFilePaths[i] = "/" + getBasePath() + "/" + fileNames[i];
    }
    configureByFiles(null, relFilePaths);

    String verificationFilePath = getTestDataPath() + "/" + getBasePath() + "/" + getTestName(false) + "_verification.xml";
    HierarchyTreeStructure structure = treeStructureComputable.compute();
    try {
      checkHierarchyTreeStructure(structure, JDOMUtil.loadDocument(new File(verificationFilePath)));
    } catch (Throwable e)  {
      assertEquals("XML structure comparison for your convenience, actual failure details BELOW",
                   FileUtil.loadFile(new File(verificationFilePath)), dump(structure, null, 0));
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
  }

  private static String dump(HierarchyTreeStructure treeStructure, @Nullable HierarchyNodeDescriptor descriptor, int level) {
    StringBuilder s = new StringBuilder();
    dump(treeStructure, descriptor, level, s);
    return s.toString();
  }

  private static void dump(HierarchyTreeStructure treeStructure,
                           @Nullable HierarchyNodeDescriptor descriptor,
                           int level,
                           StringBuilder b) {
    if (level > 10) {
      for(int i = 0; i<level; i++) b.append("  ");
      b.append("<Probably infinite part skipped>\n");
      return;
    }
    if(descriptor==null) descriptor = (HierarchyNodeDescriptor)treeStructure.getRootElement();
    for(int i = 0; i<level; i++) b.append("  ");
    descriptor.update();
    b.append("<node text=\"").append(descriptor.getHighlightedText().getText()).append("\"")
      .append(treeStructure.getBaseDescriptor() == descriptor ? " base=\"true\"" : "");

    Object[] children = treeStructure.getChildElements(descriptor);
    if(children.length>0) {
      b.append(">\n");
      for (Object o : children) {
        HierarchyNodeDescriptor d = (HierarchyNodeDescriptor)o;
        dump(treeStructure, d, level + 1, b);
      }
      for(int i = 0; i<level; i++) b.append("  ");
      b.append("</node>\n");
    } else {
      b.append("/>\n");
    }
  }

  private static void checkHierarchyTreeStructure(HierarchyTreeStructure treeStructure, Document document) {
    HierarchyNodeDescriptor rootNodeDescriptor = (HierarchyNodeDescriptor)treeStructure.getRootElement();
    rootNodeDescriptor.update();
    Element rootElement = document.getRootElement();
    if (rootElement == null || !NODE_ELEMENT_NAME.equals(rootElement.getName())) {
      throw new IllegalArgumentException("Incorrect root element in verification resource");
    }
    checkNodeDescriptorRecursively(treeStructure, rootNodeDescriptor, rootElement);
  }

  private static void checkNodeDescriptorRecursively(HierarchyTreeStructure treeStructure,
                                                     HierarchyNodeDescriptor descriptor,
                                                     Element expectedElement) {
    checkBaseNode(treeStructure, descriptor, expectedElement);
    checkContent(descriptor, expectedElement);
    checkChildren(treeStructure, descriptor, expectedElement);
  }

  private static void checkBaseNode(HierarchyTreeStructure treeStructure,
                                    HierarchyNodeDescriptor descriptor,
                                    Element expectedElement) {
    String baseAttrValue = expectedElement.getAttributeValue(BASE_ATTR_NAME);
    HierarchyNodeDescriptor baseDescriptor = treeStructure.getBaseDescriptor();
    boolean mustBeBase = "true".equalsIgnoreCase(baseAttrValue);
    assertTrue("Incorrect base node", mustBeBase ? baseDescriptor == descriptor : baseDescriptor != descriptor);
  }

  private static void checkContent(HierarchyNodeDescriptor descriptor, Element expectedElement) {
    assertEquals(expectedElement.getAttributeValue(TEXT_ATTR_NAME), descriptor.getHighlightedText().getText());
  }

  private static void checkChildren(HierarchyTreeStructure treeStructure,
                                    HierarchyNodeDescriptor descriptor,
                                    Element element) {
    if (element.getChild(ANY_NODES_ELEMENT_NAME) != null) {
      return;
    }

    Object[] children = treeStructure.getChildElements(descriptor);
    //noinspection unchecked
    List<Element> expectedChildren = new ArrayList<Element>(element.getChildren(NODE_ELEMENT_NAME));

    StringBuilder messageBuilder = new StringBuilder("Actual children of [" + descriptor.getHighlightedText().getText() + "]:\n");
    for (Object child : children) {
      HierarchyNodeDescriptor nodeDescriptor = (HierarchyNodeDescriptor)child;
      nodeDescriptor.update();
      messageBuilder.append("    [").append(nodeDescriptor.getHighlightedText().getText()).append("]\n");
    }
    assertEquals(messageBuilder.toString(), expectedChildren.size(), children.length);

    Arrays.sort(children, new Comparator<Object>() {
      @Override
      public int compare(Object first, Object second) {
        return ((HierarchyNodeDescriptor)first).getHighlightedText().getText()
          .compareTo(((HierarchyNodeDescriptor)second).getHighlightedText().getText());
      }
    });

    Collections.sort(expectedChildren, new Comparator<Element>() {
      @Override
      public int compare(Element first, Element second) {
        return first.getAttributeValue(TEXT_ATTR_NAME).compareTo(second.getAttributeValue(TEXT_ATTR_NAME));
      }
    });

    //noinspection unchecked
    Iterator<Element> iterator = expectedChildren.iterator();
    for (Object child : children) {
      checkNodeDescriptorRecursively(treeStructure, ((HierarchyNodeDescriptor)child), iterator.next());
    }
  }

}
