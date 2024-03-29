/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.java.impl.internal;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

public class DiffFiles {
  public static void main(String[] args) throws Exception {
    String file1 = "/Users/max/IDEA/community/plugins/ant/src/com/intellij/lang/ant/config/impl/configuration/usedIcons.txt";
    String file2 = "/Users/max/IDEA/community/plugins/ant/src/com/intellij/lang/ant/config/impl/configuration/allIcons.txt";

    Set<String> used = load(file1);
    Set<String> all = load(file2);

    Set<String> missing = new HashSet<String>(used);
    missing.removeAll(all);

    Set<String> unused = new HashSet<String>(all);
    unused.removeAll(used);

    System.out.println("Missing:");
    printOrdered(missing);

    System.out.println("");

    System.out.println("Unused:");
    printOrdered(unused);
  }

  private static void printOrdered(Set<String> set) {
    List<String> ordered = new ArrayList<String>(set);

    Collections.sort(ordered);
    for (String item : ordered) {
      System.out.println(item);
    }
  }

  private static Set<String> load(String file) throws Exception {
    Set<String> answer = new HashSet<String>();
    BufferedReader reader = new BufferedReader(new FileReader(file));
    String line;
    do {
      line = reader.readLine();
      if (line == null) break;
      if (line.isEmpty()) continue;

      answer.add(line);
    }
    while (true);

    return answer;
  }
}
