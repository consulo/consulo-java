/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 20, 2002
 */
package com.intellij.psi.controlFlow;

import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;

import java.util.ArrayList;

public class ControlFlowStack {
  private final IntList myIpStack = IntLists.newArrayList();
  private final ArrayList<CallInstruction> myCallInstructionStack = new ArrayList<CallInstruction>();

  public void push(int ip, CallInstruction callInstruction) {
    myIpStack.add(ip);
    myCallInstructionStack.add(callInstruction);
  }

  public int pop(boolean pushBack) {
    final int i = myIpStack.get(myIpStack.size() - 1);
    if (!pushBack) {
      myIpStack.remove(myIpStack.size()-1);
      myCallInstructionStack.remove(myCallInstructionStack.size()-1);
    }
    return i;
  }
  public int peekReturnOffset() {
    return myIpStack.get(myIpStack.size() - 1);
  }
  public int size() {
    return myIpStack.size();
  }

}
