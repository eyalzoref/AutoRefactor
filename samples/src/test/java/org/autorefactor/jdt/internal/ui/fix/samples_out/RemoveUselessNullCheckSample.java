/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2014 Jean-Noël Rouvignac - initial API and implementation
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
package org.autorefactor.jdt.internal.ui.fix.samples_out;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class RemoveUselessNullCheckSample {
    private final String DEFAULT = "";
    private String s;

    public String refactorLocalVariable1(String s) {
        String st;
        st = s;
        return st;
    }

    public String doNotRefactorLocalVariable(String s) {
        String st;
        if (s == null) {
            st = DEFAULT;
        } else {
            st = s;
        }
        return st;
    }

    public String doNotRefactorActiveExpression(List<String> s) {
        String result;
        if (s.remove(0) == null) {
            result = null;
        } else {
            result = s.remove(0);
        }
        return result;
    }

    public String refactorLocalVariable2(String s) {
        String st;
        st = s;
        return st;
    }

    public String refactorLocalVariable3(String s) {
        String st;
        st = s;
        return st;
    }

    public String refactorLocalVariable4(String s) {
        String st;
        st = s;
        return st;
    }

    public String refactorLocalVariable5(String s, boolean isValid) {
        String st = null;
        if (isValid)
            st = s;
        return st;
    }

    public void doNotRefactorFieldAssignXXX(String s, RemoveUselessNullCheckSample other) {
        if (s == null) {
            this.s = null;
        } else {
            other.s = s;
        }
    }

    public void doNotRefactorFieldAssign(String s) {
        if (s == null) {
            this.s = DEFAULT;
        } else {
            this.s = s;
        }
    }

    public void refactorFieldAssign1(String s) {
        this.s = s;
    }

    public void refactorFieldAssign2(String s) {
        this.s = s;
    }

    public void refactorFieldAssign3(String s) {
        this.s = s;
    }

    public void refactorFieldAssign4(String s) {
        this.s = s;
    }

    public String doNotRefactorReturn1(String s) {
        if (null != s) {
            return s;
        } else {
            return DEFAULT;
        }
    }

    public Collection<?> doNotRefactorReturn2(Collection<?> c) {
        if (c == null) {
            return Collections.emptySet();
        } else {
            return c;
        }
    }

    public String refactorReturn1(String s) {
        return s;
    }

    public String refactorReturn2(String s) {
        return s;
    }

    public String refactorReturn3(String s) {
        return s;
    }

    public String refactorReturn4(String s) {
        return s;
    }

    public String refactorReturnNoElse1(String s) {
        return s;
    }

    public String refactorReturnNoElse2(String s) {
        return s;
    }

    public String refactorReturnNoElse3(String s) {
        return s;
    }

    public String refactorReturnNoElse4(String s) {
        return s;
    }

    public Date doNotRefactorActiveExpression(Map<Integer, Date> s) {
        if (null != s.remove(0)) {
            return s.remove(0);
        }
        return null;
    }
}
