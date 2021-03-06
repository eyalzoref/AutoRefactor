/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2013-2016 Jean-Noël Rouvignac - initial API and implementation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program under LICENSE-GNUGPL.  If not, see
 * <http://www.gnu.org/licenses/>.
 *
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution under LICENSE-ECLIPSE, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.autorefactor.jdt.internal.ui.fix.samples_in;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class InlineCodeRatherThanPeremptoryConditionSample {
    public int removeImpossibleIfClauses() {
        int i = 0;
        int j = 0;
        if (true) {
            // Keep this comment
            i++;
        } else {
            j++;
        }

        if (true)
            // Keep this comment
            i++;
        else
            j++;

        if (false) {
            i++;
        }

        if (false) {
            i++;
        } else {
            // Keep this comment
            j++;
        }

        if (false)
            i++;
        else
            // Keep this comment
            j++;

        return i + j;
    }

    public int replaceIfByBlock(int i) {
        if (i < 0)
            if (false) {
                i++;
            }

        return i;
    }

    public int removeElse(int i) {
        if (i < 0)
            i--;
        else if (false) {
            i++;
        }

        return i;
    }

    public int doNotRefactorWithVariableConflict() {
        if (true) {
            int j = 0;
        }
        int j = 1;
        return j;
    }

    public int removeConditionWithCompatibleVariables(int i) {
        if (i == 0) {
            int j = 0;
        }
        // Keep this comment
        if (true) {
            // Keep this comment too
            int j = 1;
        }
        return 1;
    }

    public int removeDeadCodeAfterIfTrueWithReturn(int i) {
        if (true) {
            System.out.println(i);
            return 1;
        }
        return 2;
    }

    public int removePeremptoryTest(int increment, int j, byte by, boolean b, Long longObject,
            List<Date> anotherObject) {

        // Keep this comment 1
        if (j == j) {
            increment++;
        } else {
            increment--;
        }

        // Keep this comment 2
        if (j != j) {
            increment++;
        } else {
            increment--;
        }

        // Keep this comment 3
        if (by == by) {
            increment++;
        } else {
            increment--;
        }

        // Keep this comment 4
        if (by != by) {
            increment++;
        } else {
            increment--;
        }

        // Keep this comment 5
        if (b == !b) {
            increment++;
        } else {
            increment--;
        }

        // Keep this comment 6
        if (longObject == longObject) {
            increment++;
        } else {
            increment--;
        }

        // Keep this comment 7
        if (longObject != longObject) {
            increment++;
        } else {
            increment--;
        }

        // Keep this comment 8
        if (anotherObject == anotherObject) {
            increment++;
        } else {
            increment--;
        }

        // Keep this comment 9
        if (anotherObject != anotherObject) {
            increment++;
        } else {
            increment--;
        }

        return increment;
    }

    public int doNotRemoveRealTest(int increment, int i, int j) {

        // Keep this comment
        if (i == j) {
            increment++;
        } else {
            increment--;
        }

        return increment;
    }

    public int removeDeadCodeAfterEmbeddedIfTrueWithThrow(int i) {
        if (true) {
            if (true) {
                System.out.println(i);
                throw new RuntimeException();
            }
        }
        return 2;
    }

    public int removeDeadCodeAfterIfFalseWithThrow(int i) {
        if (false) {
            i++;
        } else {
            System.out.println(i);
            throw new RuntimeException();
        }
        return 2;
    }

    public int doNotRemoveDeadCodeAfterEmbeddedIfTrueNoThrowOrReturn(int i) {
        if (true) {
            if (true) {
                System.out.println(i);
            }
        }
        return 2;
    }

    public int doNotRemoveAfterIfFalseNoThrowOrReturn(int i) {
        if (false) {
            i++;
        } else {
            System.out.println(i);
        }
        return 2;
    }

    public int removeDeadCodeAfterEmbeddedIfThrowOrReturn(boolean b, int i) {
        // Keep this comment
        if (true) {
            if (b) {
                toString();
                return 1;
            } else {
                System.out.println(i);
                throw new RuntimeException();
            }
        }
        return 2;
    }

    public int removeOnlyConstantConditionWithEmbeddedIf(boolean b, int i) {
        // Keep this comment
        if (true) {
            if (b) {
                toString();
            } else {
                System.out.println(i);
            }
        }
        return 2;
    }

    public int removeOnlyConstantConditionWithEmbeddedIfReturn(boolean b) {
        // Keep this comment
        if (true) {
            if (b) {
                toString();
                return 1;
            }
        }
        return 2;
    }

    public int removeEmptyTryEmptyFinally() {
        int i = 0;
        try {
        } catch (Exception e) {
            i++;
        } finally {
        }
        return i;
    }

    public int removeEmptyTryNonEmptyFinally() {
        int i = 0;
        try {
        } catch (Exception e) {
            i++;
        } finally {
            // Keep this comment
            i++;
        }
        return i;
    }

    public int doNotRemoveEmptyTryWithVariableConflict() {
        try {
        } finally {
            int i = 0;
            i++;
        }
        int i = 0;
        return i;
    }

    public void doNotRemoveTryWithResources() throws IOException {
        try (FileInputStream f = new FileInputStream("file.txt")) {
        }
    }

    public void doNotRemoveTryWithResourcesAndFinally() throws IOException {
        int i = 0;
        try (FileInputStream f = new FileInputStream("file.txt")) {
        } finally {
            i++;
        }
    }

    public void inlineAlwaysTrueCondition() {
        if (true)
            toString();
    }

    public void inlineBlockAlwaysTrueCondition() {
        if (true) {
            // Keep this comment
            toString();
        }
    }

    public void removeAlwaysFalseCondition() {
        if (false) {
            toString();
        }
    }

    public void inlineAlwaysTrueConditionInStatement(List<String> aList) {
        if (!aList.isEmpty())
            if (true)
                aList.remove("foo");
    }

    public void inlineBlockAlwaysTrueConditionInStatement(List<String> aList) {
        if (!aList.isEmpty())
            if (true) {
                // Keep this comment
                String forbiddenValue = "foo";
                aList.remove(forbiddenValue);
            }
    }

    public int inlineAlwaysTrueConditionAndRemoveCodeAfterReturn() {
        if (true) {
            // Keep this comment
            toString();
            return 0;
        }
        int i = 0;
        i = i + 10;
        return i;
    }

    public int inlineAlwaysTrueConditionAndRemoveCodeAfterReturnOnSeveralBlock() {
        int i = 0;
        {
            if (true) {
                // Keep this comment
                toString();
                return 0;
            }
            i = i + 10;
        }
        return i;
    }

    public int inlineAlwaysTrueConditionAndRemoveCodeAfterReturnToTheFirstIf(int i) {
        int j = 0;
        if (i == 0) {
            if (true) {
                // Keep this comment
                toString();
                return 0;
            }
            j = j + 10;
        }
        return j;
    }

    public int inlineAlwaysTrueConditionAndRemoveCodeAfterReturnBeyondTryBlock(int i) {
        int j = 0;
        try {
            if (true) {
                // Keep this comment
                toString();
                return 0;
            }
            j = j + 10;
        } finally {

        }
        return j;
    }

    public int inlineAlwaysTrueConditionAndRemoveCodeAfterReturnToTheFirstCatchBlock(int i) {
        int j = 0;
        try {
            j = j + 10;
        } catch (Exception e) {
            if (true) {
                // Keep this comment
                toString();
                return 0;
            }
            j = j + 20;
        }
        return j;
    }

    public int inlineAlwaysTrueConditionAndRemoveCodeAfterReturnBeyondFinallyBlock(int i) {
        int j = 0;
        try {
            j = j + 10;
        } finally {
            if (true) {
                // Keep this comment
                toString();
                return 0;
            }
            j = j + 20;
        }
        return j;
    }

    public int inlineAlwaysTrueConditionAndRemoveCodeAfterThrow() {
        if (true) {
            // Keep this comment
            toString();
            throw new NullPointerException();
        }
        int i = 0;
        i = i + 10;
        return i;
    }

    public int removeAlwaysFalseConditionAndKeepCodeAfterThrow() {
        if (false) {
            toString();
            throw new NullPointerException();
        }
        int i = 0;
        i = i + 10;
        return i;
    }

    public List<String> inlineBlockAlwaysTrueConditionInStatement(List<String> aList, int discriminant) {
        switch (discriminant) {
        case 0:
            if (true) {
                // Keep this comment
                String forbiddenValue = "foo";
                aList.remove(forbiddenValue);
            }
            return new ArrayList<>(0);
        case 1:
            aList.add("foo");
        }
        return aList;
    }
}
