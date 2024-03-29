/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention;

import com.intellij.java.language.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

public class AddImportActionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected void tearDown() {
    IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.HIGHEST)
    super.tearDown()
  }

  public void testMap15() {
    IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.JDK_1_5)
    myFixture.configureByText 'a.java', '''\
public class Foo {
    void foo() {
        Ma<caret>p<> l;
    }
}
'''
    importClass()
    myFixture.checkResult '''import java.util.Map;

public class Foo {
    void foo() {
        Ma<caret>p<> l;
    }
}
'''
  }

  public void testMapLatestLanguageLevel() {
    myFixture.configureByText 'a.java', '''\
public class Foo {
    void foo() {
        Ma<caret>p<> l;
    }
}
'''
    importClass()
    myFixture.checkResult '''import java.util.Map;

public class Foo {
    void foo() {
        Ma<caret>p<> l;
    }
}
'''
  }

  public void testStringValue() {
    myFixture.addClass 'package java.lang; class StringValue {}'
    myFixture.addClass 'package foo; public class StringValue {}'
    myFixture.configureByText 'a.java', '''\
public class Foo {
    String<caret>Value sv;
}
'''
    importClass()
    myFixture.checkResult '''import foo.*;

public class Foo {
    String<caret>Value sv;
}
'''
  }

  public void testUseContext() {
    myFixture.addClass 'package foo; public class Log {}'
    myFixture.addClass 'package bar; public class Log {}'
    myFixture.addClass 'package bar; public class LogFactory { public static Log log(){} }'
    myFixture.configureByText 'a.java', '''\
public class Foo {
    Lo<caret>g l = bar.LogFactory.log();
}
'''
    importClass()
    myFixture.checkResult '''import bar.Log;

public class Foo {
    Lo<caret>g l = bar.LogFactory.log();
}
'''
  }

  public void testAnnotatedImport() {
    myFixture.configureByText 'a.java', '''
import java.lang.annotation.*;

@Target(ElementType.TYPE_USE) @interface TA { }

class Test {
    @TA Collection<caret> c;
}
'''
    importClass();
    myFixture.checkResult '''
import java.lang.annotation.*;
import java.util.Collection;

@Target(ElementType.TYPE_USE) @interface TA { }

class Test {
    @TA
    Collection<caret> c;
}
'''
  }

  public void testAnnotatedQualifiedImport() {
    myFixture.configureByText 'a.java', '''
import java.lang.annotation.*;

@Target(ElementType.TYPE_USE) @interface TA { }

class Test {
    java.util.@TA Collection<caret> c;
}
'''
    reimportClass();
    myFixture.checkResult '''
import java.lang.annotation.*;
import java.util.Collection;

@Target(ElementType.TYPE_USE) @interface TA { }

class Test {
    @TA
    Collection<caret> c;
}
'''
  }

  private def importClass() {
    myFixture.launchAction(myFixture.findSingleIntention("Import Class"))
  }

  private def reimportClass() {
    myFixture.launchAction(myFixture.findSingleIntention("Replace qualified name with 'import'"))
  }
}
