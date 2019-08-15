/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2013-2017 Jean-Noël Rouvignac - initial API and implementation
 * Copyright (C) 2016 Fabrice Tiercelin - Make sure we do not visit again modified nodes
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
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.arguments;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.as;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.extendedOperands;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.hasOperator;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.hasType;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.instanceOf;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.removeParentheses;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.usesGivenSignature;
import static org.eclipse.jdt.core.dom.ASTNode.FIELD_ACCESS;
import static org.eclipse.jdt.core.dom.ASTNode.QUALIFIED_NAME;
import static org.eclipse.jdt.core.dom.ASTNode.SIMPLE_NAME;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.PLUS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;

import org.autorefactor.jdt.internal.corext.dom.ASTBuilder;
import org.autorefactor.jdt.internal.corext.dom.Bindings;
import org.autorefactor.util.Pair;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;

/** See {@link #getDescription()} method. */
public class StringBuilderCleanUp extends AbstractCleanUpRule {
    /**
     * Get the name.
     *
     * @return the name.
     */
    public String getName() {
        return MultiFixMessages.CleanUpRefactoringWizard_StringBuilderCleanUp_name;
    }

    /**
     * Get the description.
     *
     * @return the description.
     */
    public String getDescription() {
        return MultiFixMessages.CleanUpRefactoringWizard_StringBuilderCleanUp_description;
    }

    /**
     * Get the reason.
     *
     * @return the reason.
     */
    public String getReason() {
        return MultiFixMessages.CleanUpRefactoringWizard_StringBuilderCleanUp_reason;
    }

    private boolean isEmptyString(final Expression expr) {
        return "".equals(expr.resolveConstantExpressionValue()) //$NON-NLS-1$
                // Due to a bug with ASTNode.resolveConstantExpressionValue()
                // in Eclipse 3.7.2 and 3.8.0, this second check is necessary
                || (expr instanceof StringLiteral && "".equals(((StringLiteral) expr).getLiteralValue())); //$NON-NLS-1$
    }

    @Override
    public boolean visit(MethodInvocation node) {
        if (node.getExpression() != null && "append".equals(node.getName().getIdentifier()) //$NON-NLS-1$
                && arguments(node).size() == 1
                // Most expensive check comes last
                && isStringBuilderOrBuffer(node.getExpression())) {
            final MethodInvocation embeddedMI= as(arg0(node), MethodInvocation.class);

            if (usesGivenSignature(embeddedMI, String.class.getCanonicalName(), "substring", int.class.getSimpleName(), int.class.getSimpleName()) //$NON-NLS-1$
                    || usesGivenSignature(embeddedMI, CharSequence.class.getCanonicalName(), "subSequence", int.class.getSimpleName(), int.class.getSimpleName())) { //$NON-NLS-1$
                replaceWithAppendSubstring(node, embeddedMI);
                return false;
            }

            return maybeRefactorAppending(node);
        } else if (usesGivenSignature(node, StringBuilder.class.getCanonicalName(), "toString") //$NON-NLS-1$
                || usesGivenSignature(node, StringBuffer.class.getCanonicalName(), "toString")) { //$NON-NLS-1$
            final LinkedList<Pair<ITypeBinding, Expression>> allAppendedStrings= new LinkedList<Pair<ITypeBinding, Expression>>();
            final Expression lastExpr= readAppendMethod(node.getExpression(), allAppendedStrings,
                    new AtomicBoolean(false), new AtomicBoolean(false));
            if (lastExpr instanceof ClassInstanceCreation) {
                // Replace with String concatenation
                this.ctx.getRefactorings().replace(node, createStringConcats(allAppendedStrings));
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        if ((hasType(node, StringBuilder.class.getCanonicalName()) || hasType(node, StringBuffer.class.getCanonicalName()))
                && arguments(node).size() == 1) {
            final Expression arg0= arguments(node).get(0);

            if (hasType(arg0, String.class.getCanonicalName())
                    && (arg0 instanceof InfixExpression || (arg0 instanceof MethodInvocation
                            && (isToString((MethodInvocation) arg0) || isStringValueOf((MethodInvocation) arg0))))) {
                return maybeRefactorAppending(node);
            }
        }
        return true;
    }

    private boolean maybeRefactorAppending(Expression node) {
        final LinkedList<Pair<ITypeBinding, Expression>> allAppendedStrings= new LinkedList<Pair<ITypeBinding, Expression>>();
        final AtomicBoolean isRefactoringNeeded= new AtomicBoolean(false);
        final AtomicBoolean isInstanceCreationToRewrite= new AtomicBoolean(false);
        final Expression lastExpr= readAppendMethod(node, allAppendedStrings, isRefactoringNeeded,
                isInstanceCreationToRewrite);

        if (lastExpr != null) {
            removeEmptyStrings(allAppendedStrings, isRefactoringNeeded);
            removeCallsToToString(allAppendedStrings, isRefactoringNeeded, isInstanceCreationToRewrite.get());

            if (isRefactoringNeeded.get()) {
                if (allAppendedStrings.isEmpty() && isVariable(lastExpr) && node.getParent() instanceof Statement) {
                    ctx.getRefactorings().remove(node.getParent());
                } else {
                    replaceWithNewStringAppends(node, allAppendedStrings, lastExpr, isInstanceCreationToRewrite.get());
                }
                return false;
            }
        }
        return true;
    }

    private Expression readAppendMethod(final Expression expr,
            final LinkedList<Pair<ITypeBinding, Expression>> allOperands, final AtomicBoolean isRefactoringNeeded,
            final AtomicBoolean isInstanceCreationToRewrite) {
        final Expression exp= removeParentheses(expr);
        if (isStringBuilderOrBuffer(exp)) {
            if (exp instanceof MethodInvocation) {
                final MethodInvocation mi= (MethodInvocation) exp;
                if ("append".equals(mi.getName().getIdentifier()) && arguments(mi).size() == 1) { //$NON-NLS-1$
                    final Expression arg0= arguments(mi).get(0);
                    readSubExpressions(arg0, allOperands, isRefactoringNeeded);
                    return readAppendMethod(mi.getExpression(), allOperands, isRefactoringNeeded,
                            isInstanceCreationToRewrite);
                }
            } else if (exp instanceof ClassInstanceCreation) {
                final ClassInstanceCreation cic= (ClassInstanceCreation) exp;
                if (arguments(cic).size() == 1) {
                    final Expression arg0= arguments(cic).get(0);
                    if (isStringBuilderOrBuffer(cic)
                            && (hasType(arg0, String.class.getCanonicalName()) || instanceOf(arg0, CharSequence.class.getCanonicalName()))) {
                        isInstanceCreationToRewrite.set(true);
                        readSubExpressions(arg0, allOperands, isRefactoringNeeded);
                    }
                } else if (arguments(cic).isEmpty() && !allOperands.isEmpty()
                        && ((allOperands.getFirst().getFirst() != null)
                                ? hasType(allOperands.getFirst().getFirst(), String.class.getCanonicalName())
                                : hasType(allOperands.getFirst().getSecond(), String.class.getCanonicalName()))) {
                    isInstanceCreationToRewrite.set(true);
                    isRefactoringNeeded.set(true);
                }
                return cic;
            } else {
                return expr;
            }
        }
        return null;
    }

    private void readSubExpressions(final Expression arg, final LinkedList<Pair<ITypeBinding, Expression>> results,
            final AtomicBoolean isRefactoringNeeded) {
        if (arg instanceof InfixExpression) {
            final InfixExpression ie= (InfixExpression) arg;
            if (isStringConcat(ie)) {
                if (ie.hasExtendedOperands()) {
                    final List<Expression> reversed= new ArrayList<Expression>(extendedOperands(ie));
                    Collections.reverse(reversed);

                    if (isValuedStringLiteralOrConstant(reversed.get(0)) && !results.isEmpty()
                            && isValuedStringLiteralOrConstant(results.get(0).getSecond())) {
                        isRefactoringNeeded.set(true);
                    }
                    for (final Expression op : reversed) {
                        if (!isValuedStringLiteralOrConstant(reversed.get(0))) {
                            isRefactoringNeeded.set(true);
                        }
                        readSubExpressions(op, results, new AtomicBoolean(false));
                    }
                }
                if (!isValuedStringLiteralOrConstant(ie.getRightOperand())
                        || !isValuedStringLiteralOrConstant(ie.getLeftOperand())) {
                    isRefactoringNeeded.set(true);
                }
                readSubExpressions(ie.getRightOperand(), results, new AtomicBoolean(false));
                readSubExpressions(ie.getLeftOperand(), results, new AtomicBoolean(false));
                return;
            }
        }
        if (isValuedStringLiteralOrConstant(arg) && !results.isEmpty()
                && isValuedStringLiteralOrConstant(results.get(0).getSecond())) {
            isRefactoringNeeded.set(true);
        }
        results.addFirst(Pair.<ITypeBinding, Expression>of(null, arg));
    }

    private boolean isStringConcat(final InfixExpression node) {
        if (!hasOperator(node, PLUS) || !hasType(node, String.class.getCanonicalName())) {
            return false;
        }
        if (!isValuedStringLiteralOrConstant(node.getLeftOperand())
                || !isValuedStringLiteralOrConstant(node.getRightOperand())) {
            return true;
        }
        for (Expression expr : extendedOperands(node)) {
            if (!isValuedStringLiteralOrConstant(expr)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValuedStringLiteralOrConstant(Expression expr) {
        if (expr instanceof StringLiteral) {
            return !isEmptyString(expr);
        } else if (expr instanceof Name && hasType(expr, String.class.getCanonicalName())) {
            Name name= (Name) expr;
            return name.resolveConstantExpressionValue() != null;
        }

        return false;
    }

    private void removeEmptyStrings(final List<Pair<ITypeBinding, Expression>> allExprs,
            final AtomicBoolean isRefactoringNeeded) {
        for (Iterator<Pair<ITypeBinding, Expression>> iter= allExprs.iterator(); iter.hasNext();) {
            Pair<ITypeBinding, Expression> expr= iter.next();
            if (expr.getFirst() == null && isEmptyString(expr.getSecond())) {
                iter.remove();
                isRefactoringNeeded.set(true);
            }
        }
    }

    private void removeCallsToToString(final List<Pair<ITypeBinding, Expression>> allExprs,
            final AtomicBoolean isRefactoringNeeded, boolean isInstanceCreationToRewrite) {
        for (ListIterator<Pair<ITypeBinding, Expression>> iter= allExprs.listIterator(); iter.hasNext();) {
            final Pair<ITypeBinding, Expression> expr= iter.next();
            if (expr.getSecond().getNodeType() == ASTNode.METHOD_INVOCATION) {
                final MethodInvocation mi= (MethodInvocation) expr.getSecond();
                if (usesGivenSignature(mi, Object.class.getCanonicalName(), "toString")) { //$NON-NLS-1$
                    if (mi.getExpression() != null) {
                        iter.set(Pair.<ITypeBinding, Expression>of(null, mi.getExpression()));
                    } else {
                        iter.set(Pair.<ITypeBinding, Expression>of(null, this.ctx.getAST().newThisExpression()));
                    }
                    isRefactoringNeeded.set(true);
                } else if (isToString(mi) || isStringValueOf(mi)) {
                    iter.set(getTypeAndValue(mi));
                    isRefactoringNeeded.set(true);
                }
            }
        }
    }

    private Pair<ITypeBinding, Expression> getTypeAndValue(final MethodInvocation mi) {
        final ITypeBinding expectedType= mi.resolveMethodBinding().getParameterTypes()[0];
        if (hasType(arg0(mi), expectedType.getQualifiedName(),
                Bindings.getBoxedTypeBinding(expectedType, mi.getAST()).getQualifiedName())) {
            return Pair.<ITypeBinding, Expression>of(null, arg0(mi));
        } else {
            return Pair.<ITypeBinding, Expression>of(expectedType, arg0(mi));
        }
    }

    /**
     * Rewrite the successive calls to append()
     *
     * @param node                        The node to replace.
     * @param allAppendedStrings          All appended strings.
     * @param lastExpr                    The expression on which the methods are
     *                                    called.
     * @param isInstanceCreationToRewrite
     */
    private void replaceWithNewStringAppends(final Expression node,
            final LinkedList<Pair<ITypeBinding, Expression>> allAppendedStrings, final Expression lastExpr,
            final boolean isInstanceCreationToRewrite) {
        final ASTBuilder b= this.ctx.getASTBuilder();

        Expression result= null;
        final List<Expression> tempStringLiterals= new ArrayList<Expression>();
        final List<Expression> finalStrings= new ArrayList<Expression>();
        final AtomicBoolean isFirst= new AtomicBoolean(true);

        for (final Pair<ITypeBinding, Expression> appendedString : allAppendedStrings) {
            if (isValuedStringLiteralOrConstant(appendedString.getSecond())) {
                tempStringLiterals.add(b.copy(appendedString.getSecond()));
            } else {
                result= handleTempStringLiterals(b, lastExpr, isInstanceCreationToRewrite, result, tempStringLiterals,
                        finalStrings, isFirst);

                if (isFirst.get()) {
                    isFirst.set(false);

                    if (!isInstanceCreationToRewrite) {
                        result= b.copy(lastExpr);
                        finalStrings.add(getTypedExpression(b, appendedString));
                    } else if ((appendedString.getFirst() != null)
                            ? hasType(appendedString.getFirst(), String.class.getCanonicalName())
                            : hasType(appendedString.getSecond(), String.class.getCanonicalName())) {
                        result= b.new0(b.copy(((ClassInstanceCreation) lastExpr).getType()),
                                getTypedExpression(b, appendedString));
                    } else {
                        result= b.new0(b.copy(((ClassInstanceCreation) lastExpr).getType()));
                        finalStrings.add(getTypedExpression(b, appendedString));
                    }
                } else {
                    finalStrings.add(getTypedExpression(b, appendedString));
                }
            }
        }

        result= handleTempStringLiterals(b, lastExpr, isInstanceCreationToRewrite, result, tempStringLiterals,
                finalStrings, isFirst);

        for (final Expression finalString : finalStrings) {
            if (result == null) {
                result= finalString;
            } else {
                result= b.invoke(result, "append", finalString); //$NON-NLS-1$
            }
        }

        ctx.getRefactorings().replace(node, result);
    }

    private Expression handleTempStringLiterals(final ASTBuilder b, final Expression lastExpr,
            final boolean isInstanceCreationToRewrite, Expression result, final List<Expression> tempStringLiterals,
            final List<Expression> finalStrings, final AtomicBoolean isFirst) {
        if (!tempStringLiterals.isEmpty()) {
            final Expression newExpr= getString(b, tempStringLiterals);

            if (isFirst.get()) {
                isFirst.set(false);
                if (isInstanceCreationToRewrite) {
                    result= b.new0(b.copy(((ClassInstanceCreation) lastExpr).getType()), newExpr);
                } else {
                    result= b.copy(lastExpr);
                    finalStrings.add(newExpr);
                }
            } else {
                finalStrings.add(newExpr);
            }

            tempStringLiterals.clear();
        }
        return result;
    }

    private Expression getString(final ASTBuilder b, final List<Expression> tempStringLiterals) {
        final Expression newExpr;
        if (tempStringLiterals.size() == 1) {
            newExpr= tempStringLiterals.get(0);
        } else {
            newExpr= b.infixExpr(PLUS, tempStringLiterals);
        }
        return newExpr;
    }

    private void replaceWithAppendSubstring(final MethodInvocation node, final MethodInvocation embeddedMI) {
        final ASTBuilder b= this.ctx.getASTBuilder();
        final Expression stringVar= b.copy(embeddedMI.getExpression());
        final List<Expression> args= arguments(embeddedMI);
        final Expression arg0= b.copy(args.get(0));
        final Expression arg1= b.copy(args.get(1));
        final Expression lastExpr= b.copy(node.getExpression());
        MethodInvocation newAppendSubstring= null;
        if (arg1 == null) {
            newAppendSubstring= b.invoke(lastExpr, "append", stringVar, arg0); //$NON-NLS-1$
        } else {
            newAppendSubstring= b.invoke(lastExpr, "append", stringVar, arg0, arg1); //$NON-NLS-1$
        }

        this.ctx.getRefactorings().replace(node, newAppendSubstring);
    }

    private boolean isStringBuilderOrBuffer(final Expression expr) {
        return hasType(expr, StringBuffer.class.getCanonicalName(), StringBuilder.class.getCanonicalName());
    }

    private boolean isVariable(final Expression expr) {
        switch (removeParentheses(expr).getNodeType()) {
        case SIMPLE_NAME:
        case QUALIFIED_NAME:
        case FIELD_ACCESS:
            return true;

        default:
            return false;
        }
    }

    private boolean isToString(final MethodInvocation mi) {
        return usesGivenSignature(mi, Boolean.class.getCanonicalName(), "toString", boolean.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Byte.class.getCanonicalName(), "toString", byte.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Character.class.getCanonicalName(), "toString", char.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Short.class.getCanonicalName(), "toString", short.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Integer.class.getCanonicalName(), "toString", int.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Long.class.getCanonicalName(), "toString", long.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Float.class.getCanonicalName(), "toString", float.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Double.class.getCanonicalName(), "toString", double.class.getSimpleName()); //$NON-NLS-1$
    }

    private boolean isStringValueOf(final MethodInvocation mi) {
        return usesGivenSignature(mi, String.class.getCanonicalName(), "valueOf", Object.class.getCanonicalName()) //$NON-NLS-1$
                || usesGivenSignature(mi, String.class.getCanonicalName(), "valueOf", boolean.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Boolean.class.getCanonicalName(), "valueOf", boolean.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, String.class.getCanonicalName(), "valueOf", char.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Character.class.getCanonicalName(), "valueOf", char.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, String.class.getCanonicalName(), "valueOf", int.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Integer.class.getCanonicalName(), "valueOf", int.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, String.class.getCanonicalName(), "valueOf", long.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Long.class.getCanonicalName(), "valueOf", long.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, String.class.getCanonicalName(), "valueOf", float.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Float.class.getCanonicalName(), "valueOf", float.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, String.class.getCanonicalName(), "valueOf", double.class.getSimpleName()) //$NON-NLS-1$
                || usesGivenSignature(mi, Double.class.getCanonicalName(), "valueOf", double.class.getSimpleName()); //$NON-NLS-1$
    }

    private Expression getTypedExpression(final ASTBuilder b, final Pair<ITypeBinding, Expression> typeAndValue) {
        Expression expression= null;
        if (typeAndValue.getFirst() != null) {
            expression= b.cast(b.type(typeAndValue.getFirst().getQualifiedName()), b.copy(typeAndValue.getSecond()));
        } else if (typeAndValue.getFirst() == null) {
            expression= b.copy(typeAndValue.getSecond());
        }
        return expression;
    }

    @Override
    public boolean visit(InfixExpression node) {
        if (isStringConcat(node)) {
            final LinkedList<Pair<ITypeBinding, Expression>> allOperands= new LinkedList<Pair<ITypeBinding, Expression>>();
            readSubExpressions(node, allOperands, new AtomicBoolean(false));
            boolean replaceNeeded= filterOutEmptyStringsFromStringConcat(allOperands);
            if (replaceNeeded) {
                this.ctx.getRefactorings().replace(node, createStringConcats(allOperands));
                return false;
            }
        }
        return true;
    }

    private boolean filterOutEmptyStringsFromStringConcat(final List<Pair<ITypeBinding, Expression>> allOperands) {
        boolean replaceNeeded= false;
        boolean canRemoveEmptyStrings= false;
        for (int i= 0; i < allOperands.size(); i++) {
            Pair<ITypeBinding, Expression> expr= allOperands.get(i);
            boolean canNowRemoveEmptyStrings= canRemoveEmptyStrings || hasType(expr.getSecond(), String.class.getCanonicalName());
            if (isEmptyString(expr.getSecond())) {
                boolean removeExpr= false;
                if (canRemoveEmptyStrings) {
                    removeExpr= true;
                } else if (canNowRemoveEmptyStrings && i + 1 < allOperands.size()
                        && hasType(allOperands.get(i + 1).getSecond(), String.class.getCanonicalName())) {
                    removeExpr= true;
                }

                if (removeExpr) {
                    allOperands.remove(i);
                    replaceNeeded= true;
                }
            }
            canRemoveEmptyStrings= canNowRemoveEmptyStrings;
        }
        return replaceNeeded;
    }

    private Expression createStringConcats(final List<Pair<ITypeBinding, Expression>> appendedStrings) {
        final ASTBuilder b= this.ctx.getASTBuilder();
        switch (appendedStrings.size()) {
        case 0:
            return b.string(""); //$NON-NLS-1$

        case 1:
            final Pair<ITypeBinding, Expression> expr= appendedStrings.get(0);
            if (hasType(expr.getSecond(), String.class.getCanonicalName())) {
                return b.copy(expr.getSecond());
            }
            return b.invoke("String", "valueOf", getTypedExpression(b, expr)); //$NON-NLS-1$ $NON-NLS-2$

        default: // >= 2
            boolean isFirstAndNotAString= isFirstAndNotAString(appendedStrings);

            List<Expression> concatenateStrings= new ArrayList<Expression>(appendedStrings.size());
            for (final Pair<ITypeBinding, Expression> typeAndValue : appendedStrings) {
                if (isFirstAndNotAString) {
                    concatenateStrings.add(b.invoke("String", "valueOf", getTypedExpression(b, typeAndValue))); //$NON-NLS-1$ $NON-NLS-2$
                    isFirstAndNotAString= false;
                } else {
                    concatenateStrings.add(b.parenthesizeIfNeeded(getTypedExpression(b, typeAndValue)));
                }
            }
            return b.infixExpr(PLUS, concatenateStrings);
        }
    }

    private boolean isFirstAndNotAString(final List<Pair<ITypeBinding, Expression>> appendedStrings) {
        final Pair<ITypeBinding, Expression> arg0= appendedStrings.get(0);
        final Pair<ITypeBinding, Expression> arg1= appendedStrings.get(1);

        return !hasType(arg0.getSecond(), String.class.getCanonicalName()) && !hasType(arg1.getSecond(), String.class.getCanonicalName());
    }
}
