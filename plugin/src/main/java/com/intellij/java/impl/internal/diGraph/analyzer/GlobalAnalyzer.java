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
package com.intellij.java.impl.internal.diGraph.analyzer;

import consulo.util.lang.Pair;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author db
 * @since 2003-06-21
 */
public class GlobalAnalyzer {
    @SuppressWarnings("unchecked")
    private static <T extends MarkedNode> boolean stepOneEnd(MarkedNode currNode, List<T> worklist, OneEndFunctor functor) {
        boolean result = false;

        for (Iterator<MarkedEdge> i = currNode.outIterator(); i.hasNext(); ) {
            MarkedEdge currEdge = i.next();
            MarkedNode nextNode = (MarkedNode)currEdge.end();
            Mark theMark = functor.compute(currNode.getMark(), currEdge.getMark(), nextNode.getMark());
            if (!theMark.coincidesWith(nextNode.getMark())) {
                result = true;
                nextNode.setMark(theMark);
                worklist.addFirst((T)nextNode);
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T extends MarkedNode> boolean stepTwoEnds(MarkedNode currNode, List<T> worklist, TwoEndsFunctor functor) {
        boolean result = false;

        for (Iterator i = currNode.outIterator(); i.hasNext(); ) {
            MarkedEdge currEdge = (MarkedEdge)i.next();
            MarkedNode nextNode = (MarkedNode)currEdge.end();
            Pair<Mark, Mark> markPair = functor.compute(currNode.getMark(), currEdge.getMark(), nextNode.getMark());

            Mark leftMark = markPair.getFirst();
            Mark rightMark = markPair.getSecond();

            if (!leftMark.coincidesWith(currNode.getMark())) {
                result = true;
                currNode.setMark(leftMark);
                worklist.addFirst((T)currNode);
            }

            if (!rightMark.coincidesWith(nextNode.getMark())) {
                result = true;
                nextNode.setMark(rightMark);
                worklist.addFirst((T)nextNode);
            }
        }

        return result;
    }

    public static <T extends MarkedNode> boolean doOneEnd(List<T> init, OneEndFunctor functor) {
        boolean result = false;

        List<T> worklist = new LinkedList<>();

        for (T anInit : init) {
            result = stepOneEnd(anInit, worklist, functor) || result;
        }

        while (worklist.size() > 0) {
            result = stepOneEnd(worklist.removeFirst(), worklist, functor) || result;
        }

        return result;
    }

    public static <T extends MarkedNode> boolean doTwoEnds(LinkedList<T> init, TwoEndsFunctor functor) {
        boolean result = false;

        LinkedList<T> worklist = new LinkedList<>();

        for (T anInit : init) {
            result = stepTwoEnds(anInit, worklist, functor) || result;
        }

        while (worklist.size() > 0) {
            result = stepTwoEnds(worklist.removeFirst(), worklist, functor) || result;
        }

        return result;
    }
}
