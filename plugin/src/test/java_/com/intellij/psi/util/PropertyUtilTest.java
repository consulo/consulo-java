package com.intellij.psi.util;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author mike
 * @since 2002-06-14
 */
public abstract class PropertyUtilTest extends LightCodeInsightTestCase {
  public void testSuggestGetterName() throws Exception {
    assertEquals("isValid", PropertyUtil.suggestGetterName("valid", getType("boolean")));
    assertEquals("getValid", PropertyUtil.suggestGetterName("valid", getType("Object")));
    assertEquals("isValid", PropertyUtil.suggestGetterName(createField("valid", "boolean")));
    assertEquals("getValid", PropertyUtil.suggestGetterName(createField("valid", "Object")));

    assertEquals("getURL", PropertyUtil.suggestGetterName("URL", getType("Object")));
    assertEquals("getURL", PropertyUtil.suggestGetterName(createField("URL", "Object")));

    assertEquals("isaURL", PropertyUtil.suggestGetterName(createField("aURL", "boolean")));
    assertEquals("getaURL", PropertyUtil.suggestGetterName(createField("aURL", "Object")));
    assertEquals("getBool", PropertyUtil.suggestGetterName(createField("bool", CommonClassNames.JAVA_LANG_BOOLEAN)));
  }

  public void testSuggestSetterName() throws Exception {
    assertEquals("setValid", PropertyUtil.suggestSetterName("valid"));
    assertEquals("setValid", PropertyUtil.suggestSetterName(createField("valid", "Object")));

    assertEquals("setURL", PropertyUtil.suggestSetterName("URL"));
    assertEquals("setURL", PropertyUtil.suggestSetterName(createField("URL", "Object")));

    assertEquals("setaURL", PropertyUtil.suggestSetterName("aURL"));
    assertEquals("setaURL", PropertyUtil.suggestSetterName(createField("aURL", "Object")));
    assertEquals("setBool", PropertyUtil.suggestSetterName(createField("bool", CommonClassNames.JAVA_LANG_BOOLEAN)));
  }

  public void testSuggestPropertyName() throws Exception {
    assertEquals("valid", PropertyUtil.getPropertyName("isValid"));
    assertEquals("valid", PropertyUtil.getPropertyName("getValid"));
    assertEquals("valid", PropertyUtil.getPropertyName("setValid"));

    assertEquals("URL", PropertyUtil.getPropertyName("isURL"));
    assertEquals("URL", PropertyUtil.getPropertyName("getURL"));
    assertEquals("URL", PropertyUtil.getPropertyName("setURL"));

    assertEquals("aURL", PropertyUtil.getPropertyName("isaURL"));
    assertEquals("aURL", PropertyUtil.getPropertyName("getaURL"));
    assertEquals("aURL", PropertyUtil.getPropertyName("setaURL"));
  }

  public void testBooleanPropertyGetters() {
    assertTrue(PropertyUtil.hasGetterName(createMethod("isOk", "boolean")));
    assertFalse(PropertyUtil.hasGetterName(createMethod("isOk", CommonClassNames.JAVA_LANG_BOOLEAN)));
    assertTrue(PropertyUtil.hasGetterName(createMethod("getOk", CommonClassNames.JAVA_LANG_BOOLEAN)));
    assertFalse(PropertyUtil.hasGetterName(createMethod("isOk", "int")));
  }

  private static PsiType getType(@NonNls String type) throws IncorrectOperationException {
    return PsiElementFactory.SERVICE.getInstance(ourProject).createTypeFromText(type, null);
  }

  private static PsiField createField(@NonNls String name, @NonNls String type) throws IncorrectOperationException {
    return PsiElementFactory.SERVICE.getInstance(ourProject).createField(name, getType(type));
  }

  private static PsiMethod createMethod(@NonNls String name, String type) throws IncorrectOperationException {
    return PsiElementFactory.SERVICE.getInstance(ourProject).createMethod(name, getType(type));
  }
}
