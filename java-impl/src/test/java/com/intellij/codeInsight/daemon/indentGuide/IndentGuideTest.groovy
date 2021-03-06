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
package com.intellij.codeInsight.daemon.indentGuide

/**
 * @author Denis Zhdanov
 * @since 2/7/13 4:01 PM
 */
class IndentGuideTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp()
  }

  void "test indent guides which cross commented code between comment mark and comment text"() {
    // IDEA-99572.
    doTest(
"""\
class Test {
  void test() {
  |  //test();
  |  if (true) {
  |  |  int i = 1;
  |  |  
//|  |    int k;
  |  |  int j = 1;
  |  }
  }
}
"""
    )
  }

  void "test that inner comment indents do not exceed"() {
    // IDEA-89471.
    doTest(
"""\
class Test {
  void test() {
  |  return;
//|  |  if (true) {
//|  |  |  int i1 = 1;
//|  |  |  int i2 = 2;
//|  |  |  if (true) {
//|  |  |  |  int j1 = 1;
//|  |  |  |  int j2 = 2;
//|  |  |  }
//|  |  }
//|  int k = 1;
  }
}
"""
    )
  }

  void "test indent guide which starts on comment line"() {
    // IDEA-62184
    doTest(
"""\
class Test {
  void test(int i) {
  |  switch (i) {
  |  //
  |  |  case 1:
  |  |  case 2:
  |  }
  }
}
"""
    )
  }
  
  void "test no indent guide for javadoc"() {
    doTest(
"""\
class Test {
  /**
   * doc
   */
  int i;
}
"""
    )
  }
  
  void "test no unnecessary guide for non-first line comments"() {
    doTest(
      """\
class Test {
  void test() {
  |  //int i1;
  |  //int i2;
  |  return;
  }
}
"""
    )
  }
  
  void "test block comment and inner indents"() {
    doTest(
      """\
class Test {
  int test() {
  |  return 1 /*{
  |  |  int test2() {
  |  |  |  int i1;
  |  |  }
  |  |  int i2;
  |  }*/;
  }
}
"""
    )
  }
  
  private void doTest(@NotNull String text) {
    IndentGuideTestData testData = parse(text)
    myFixture.configureByText("${getTestName(false)}.java", testData.documentText)
    CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.file, myFixture.editor, ArrayUtilRt.EMPTY_INT_ARRAY, false)
    IndentsModelImpl model = myFixture.editor.indentsModel as IndentsModelImpl
    assertEquals(
      "expected to find ${testData.guides.size()} indent guides (" +
      "${testData.guides.collect { startLine, endLine, level -> "$level ($startLine-$endLine)"}}) " +
      "but got ${model.indents.size()} (${model.indents})",
      testData.guides.size(), model.indents.size()
    )

    testData.guides.each {
      def descriptor = model.getDescriptor(it[0], it[1])
      assertNotNull("expected to find an indent guide at lines ${it[0]}-${it[1]}", descriptor)
      assertEquals(
        "expected that indent guide descriptor at lines ${it[0]}-${it[1]} has indent ${it[2]} but got ${descriptor.indentLevel}",
        it[2], descriptor.indentLevel
      )
    }
  }
  
  @NotNull
  private static IndentGuideTestData parse(@NotNull String text) {
    def buffer = new StringBuilder()
    def indentGuides = []
    def prevLineIndents = [:] // indent level -> start line
    int shift, i, textStart
    text.eachLine { lineText, line ->
      shift = textStart = 0
      def endedGuides = prevLineIndents.clone() as Map
      for (i = lineText.indexOf('|', 0); i >= 0; i = lineText.indexOf('|', textStart)) {
        def indentLevel = i - shift
        if (prevLineIndents[indentLevel]) {
          endedGuides.remove(indentLevel)
        }
        else {
          prevLineIndents[indentLevel] = line - 1
        }
        shift++
        buffer << lineText[textStart..<i]
        textStart = i + 1
      }
      endedGuides.each { level, startLine ->
        indentGuides << [startLine, line, level]
        prevLineIndents.remove(level)
      }
      if (textStart < lineText.length()) {
        buffer << lineText[textStart..-1]
      }
      buffer << '\n'
    }
    new IndentGuideTestData(documentText: buffer[0..-2], guides: indentGuides)
  }
}

class IndentGuideTestData {
  String documentText
  def guides // List of three element lists: [start indent line; end indent line; indent level]
}
