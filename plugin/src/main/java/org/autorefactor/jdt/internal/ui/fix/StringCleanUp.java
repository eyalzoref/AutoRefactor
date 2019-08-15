/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2013-2017 Jean-Noël Rouvignac - initial API and implementation
 * Copyright (C) 2016-2017 Fabrice Tiercelin - Make sure we do not visit again modified nodes
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
package org.autorefactor.jdt.internal.ui.fix;

import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.arg0;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.as;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.hasType;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.usesGivenSignature;
import static org.eclipse.jdt.core.dom.ASTNode.INFIX_EXPRESSION;

import java.util.concurrent.atomic.AtomicBoolean;

import org.autorefactor.jdt.internal.corext.dom.ASTBuilder;
import org.autorefactor.jdt.internal.corext.dom.Bindings;
import org.autorefactor.jdt.internal.corext.dom.Refactorings;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;

/** See {@link #getDescription()} method. */
public class StringCleanUp extends AbstractCleanUpRule {
    /**
     * Get the name.
     *
     * @return the name.
     */
    public String getName() {
        return MultiFixMessages.CleanUpRefactoringWizard_StringCleanUp_name;
    }

    /**
     * Get the description.
     *
     * @return the description.
     */
    public String getDescription() {
        return MultiFixMessages.CleanUpRefactoringWizard_StringCleanUp_description;
    }

    /**
     * Get the reason.
     *
     * @return the reason.
     */
    public String getReason() {
        return MultiFixMessages.CleanUpRefactoringWizard_StringCleanUp_reason;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        final Expression expr= node.getExpression();
        final ASTNode parent= node.getParent();
        final ASTBuilder b= this.ctx.getASTBuilder();
        final Refactorings r= ctx.getRefactorings();
        final boolean isStringValueOf= isStringValueOf(node);
        if (usesGivenSignature(node, Object.class.getCanonicalName(), "toString")) { //$NON-NLS-1$
            if (hasType(expr, String.class.getCanonicalName())) {
                // If node is already a String, no need to call toString()
                r.replace(node, b.move(expr));
                return false;
            } else if (parent.getNodeType() == INFIX_EXPRESSION) {
                // If node is in a String context, no need to call toString()
                final InfixExpression ie= (InfixExpression) node.getParent();
                final Expression leftOp= ie.getLeftOperand();
                final Expression rightOp= ie.getRightOperand();
                final boolean leftOpIsString= hasType(leftOp, String.class.getCanonicalName());
                final boolean rightOpIsString= hasType(rightOp, String.class.getCanonicalName());
                final MethodInvocation lmi= as(leftOp, MethodInvocation.class);
                final MethodInvocation rmi= as(rightOp, MethodInvocation.class);
                if (!node.equals(lmi) && !node.equals(rmi) && (leftOpIsString || rightOpIsString)) {
                    // Node is in the extended operands
                    r.replace(node, replaceToString(node.getExpression()));
                    return false;
                } else if (leftOpIsString && usesGivenSignature(rmi, Object.class.getCanonicalName(), "toString")) { //$NON-NLS-1$
                    r.replace(rmi, replaceToString(rmi.getExpression()));
                    return false;
                } else if (rightOpIsString && node.equals(lmi)) {
                    r.replace(lmi, replaceToString(lmi.getExpression()));
                    return false;
                }
            }
        } else if (isStringValueOf && hasType(arg0(node), String.class.getCanonicalName())) {
            if (arg0(node) instanceof StringLiteral || arg0(node) instanceof InfixExpression) {
                r.replace(node, b.parenthesizeIfNeeded(b.move(arg0(node))));
                return false;
            }
        } else if ((isToStringForPrimitive(node) || isStringValueOf) && parent.getNodeType() == INFIX_EXPRESSION) {
            // If node is in a String context, no need to call toString()
            final InfixExpression ie= (InfixExpression) node.getParent();
            final Expression lo= ie.getLeftOperand();
            final Expression ro= ie.getRightOperand();
            if (node.equals(lo)) {
                if (hasType(ro, String.class.getCanonicalName())) {
                    replaceStringValueOfByArg0(lo, node);
                    return false;
                }
            } else if (node.equals(ro)) {
                if (hasType(lo, String.class.getCanonicalName())
                        // Do not refactor left and right operand at the same time
                        // to avoid compilation errors post refactoring
                        && !r.hasBeenRefactored(lo)) {
                    replaceStringValueOfByArg0(ro, node);
                    return false;
                }
            } else {
                // Left or right operation is necessarily a string, so just replace
                replaceStringValueOfByArg0(node, node);
                return false;
            }
        } else if (usesGivenSignature(node, String.class.getCanonicalName(), "equals", Object.class.getCanonicalName())) { //$NON-NLS-1$
            final MethodInvocation leftInvocation= as(node.getExpression(), MethodInvocation.class);
            final MethodInvocation rightInvocation= as(arg0(node), MethodInvocation.class);

            if (leftInvocation != null && rightInvocation != null
                    && ((usesGivenSignature(leftInvocation, String.class.getCanonicalName(), "toLowerCase") //$NON-NLS-1$
                            && usesGivenSignature(rightInvocation, String.class.getCanonicalName(), "toLowerCase")) //$NON-NLS-1$
                            || (usesGivenSignature(leftInvocation, String.class.getCanonicalName(), "toUpperCase") //$NON-NLS-1$
                                    && usesGivenSignature(rightInvocation, String.class.getCanonicalName(), "toUpperCase")))) { //$NON-NLS-1$
                final Expression leftExpr= leftInvocation.getExpression();
                final Expression rightExpr= rightInvocation.getExpression();
                r.replace(node, b.invoke(b.copy(leftExpr), "equalsIgnoreCase", b.copy(rightExpr))); //$NON-NLS-1$
                return false;
            }
        } else if (usesGivenSignature(node, String.class.getCanonicalName(), "equalsIgnoreCase", String.class.getCanonicalName())) { //$NON-NLS-1$
            final AtomicBoolean isRefactoringNeeded= new AtomicBoolean(false);

            final Expression leftExpr= getReducedStringExpression(node.getExpression(), isRefactoringNeeded);
            final Expression rightExpr= getReducedStringExpression(arg0(node), isRefactoringNeeded);

            if (isRefactoringNeeded.get()) {
                r.replace(node, b.invoke(b.copy(leftExpr), "equalsIgnoreCase", b.copy(rightExpr))); //$NON-NLS-1$
                return false;
            }
        } else if (usesGivenSignature(node, String.class.getCanonicalName(), "indexOf", String.class.getCanonicalName()) //$NON-NLS-1$
                || usesGivenSignature(node, String.class.getCanonicalName(), "lastIndexOf", String.class.getCanonicalName()) //$NON-NLS-1$
                || usesGivenSignature(node, String.class.getCanonicalName(), "indexOf", String.class.getCanonicalName(), Integer.class.getCanonicalName()) //$NON-NLS-1$
                || usesGivenSignature(node, String.class.getCanonicalName(), "lastIndexOf", String.class.getCanonicalName(), Integer.class.getCanonicalName())) { //$NON-NLS-1$
            Expression expression= arg0(node);
            if (expression instanceof StringLiteral) {
                String value= ((StringLiteral) expression).getLiteralValue();
                if (value.length() == 1) {
                    CharacterLiteral replacement= b.charLiteral();
                    replacement.setCharValue(value.charAt(0));
                    r.replace(expression, replacement);
                    return false;
                }
            }
        }
        return true;
    }

    private Expression getReducedStringExpression(Expression stringExpr, AtomicBoolean isRefactoringNeeded) {
        final MethodInvocation casingInvocation= as(stringExpr, MethodInvocation.class);
        if (casingInvocation != null && (usesGivenSignature(casingInvocation, String.class.getCanonicalName(), "toLowerCase") //$NON-NLS-1$
                || usesGivenSignature(casingInvocation, String.class.getCanonicalName(), "toUpperCase"))) { //$NON-NLS-1$
            isRefactoringNeeded.set(true);
            return casingInvocation.getExpression();
        }
        return stringExpr;
    }

    private void replaceStringValueOfByArg0(final Expression toReplace, final MethodInvocation mi) {
        final ASTBuilder b= this.ctx.getASTBuilder();

        final ITypeBinding expectedType= mi.resolveMethodBinding().getParameterTypes()[0];
        final ITypeBinding actualType= arg0(mi).resolveTypeBinding();
        if (!expectedType.equals(actualType) && !Bindings.getBoxedTypeBinding(expectedType, mi.getAST()).equals(actualType)) {
            ctx.getRefactorings().replace(toReplace, b.cast(b.type(expectedType.getQualifiedName()), b.move(arg0(mi))));
        } else {
            ctx.getRefactorings().replace(toReplace, b.parenthesizeIfNeeded(b.move(arg0(mi))));
        }
    }

    private Expression replaceToString(final Expression expr) {
        final ASTBuilder b= ctx.getASTBuilder();
        if (expr != null) {
            return b.move(expr);
        } else {
            return b.this0();
        }
    }

    private boolean isToStringForPrimitive(final MethodInvocation node) {
        return "toString".equals(node.getName().getIdentifier()) // fast-path $NON-NLS-1$
                && (usesGivenSignature(node, Boolean.class.getCanonicalName(), "toString", boolean.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, Character.class.getCanonicalName(), "toString", char.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, Byte.class.getCanonicalName(), "toString", byte.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, Short.class.getCanonicalName(), "toString", short.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, Integer.class.getCanonicalName(), "toString", int.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, Long.class.getCanonicalName(), "toString", long.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, Float.class.getCanonicalName(), "toString", float.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, Double.class.getCanonicalName(), "toString", double.class.getSimpleName())); //$NON-NLS-1$
    }

    private boolean isStringValueOf(final MethodInvocation node) {
        return hasType(node.getExpression(), String.class.getCanonicalName()) // fast-path
                && (usesGivenSignature(node, String.class.getCanonicalName(), "valueOf", boolean.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, String.class.getCanonicalName(), "valueOf", char.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, String.class.getCanonicalName(), "valueOf", byte.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, String.class.getCanonicalName(), "valueOf", short.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, String.class.getCanonicalName(), "valueOf", int.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, String.class.getCanonicalName(), "valueOf", long.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, String.class.getCanonicalName(), "valueOf", float.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, String.class.getCanonicalName(), "valueOf", double.class.getSimpleName()) //$NON-NLS-1$
                        || usesGivenSignature(node, String.class.getCanonicalName(), "valueOf", Object.class.getCanonicalName())); //$NON-NLS-1$
    }
}
