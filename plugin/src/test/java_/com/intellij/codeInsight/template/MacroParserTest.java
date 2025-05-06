package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.*;
import com.intellij.java.impl.codeInsight.template.macro.VariableOfTypeMacro;
import com.intellij.testFramework.LightIdeaTestCase;
import consulo.java.language.module.util.JavaClassNames;

/**
 * @author yole
 */
public abstract class MacroParserTest extends LightIdeaTestCase {

  public void testEmpty() {
    Expression e = TemplateImplUtil.parseTemplate("");
    assertTrue(e instanceof ConstantNode);
    assertEquals("", e.calculateResult(null).toString());
  }

  public void testFunction() {
    Expression e = TemplateImplUtil.parseTemplate("  variableOfType(  \"java.util.Collection\"  )   ");
    assertTrue(e instanceof MacroCallNode);
    MacroCallNode n = (MacroCallNode) e;
    assertTrue(n.getMacro() instanceof VariableOfTypeMacro);
    Expression[] parameters = n.getParameters();
    assertEquals(1, parameters.length);
    assertTrue(parameters [0] instanceof ConstantNode);
    ConstantNode cn = (ConstantNode) parameters [0];
    assertEquals(JavaClassNames.JAVA_UTIL_COLLECTION, cn.calculateResult(null).toString());
  }

  public void testVariable() {
    Expression e = TemplateImplUtil.parseTemplate("variableOfType(E=\"t\")");
    Expression[] parameters = ((MacroCallNode) e).getParameters();
    assertEquals(1, parameters.length);
    assertTrue(parameters [0] instanceof VariableNode);
    VariableNode vn = (VariableNode) parameters [0];
    assertEquals("E", vn.getName());
    assertTrue(vn.getInitialValue() instanceof ConstantNode);
  }

  public void testEnd() {
    Expression e = TemplateImplUtil.parseTemplate("END");
    assertTrue(e instanceof EmptyNode);
  }

  public void testMultipleParams() {
    Expression e = TemplateImplUtil.parseTemplate("variableOfType(\"A\", \"B\")");
    assertTrue(e instanceof MacroCallNode);
    MacroCallNode n = (MacroCallNode) e;
    assertTrue(n.getMacro() instanceof VariableOfTypeMacro);
    Expression[] parameters = n.getParameters();
    assertEquals(2, parameters.length);
    assertTrue(parameters [0] instanceof ConstantNode);
    assertTrue(parameters [1] instanceof ConstantNode);
  }
}
