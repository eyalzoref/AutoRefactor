/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2020 Fabrice Tiercelin - initial API and implementation
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.autorefactor.jdt.internal.corext.dom.ASTNodeFactory;
import org.autorefactor.jdt.internal.corext.dom.ASTNodes;
import org.autorefactor.util.Pair;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.Statement;

/**
 * See {@link #getDescription()} method.
 */
public class AssertJCleanUp extends AbstractUnitTestCleanUp {
	private static final String FAIL_CLASS= "org.assertj.core.api.Fail"; //$NON-NLS-1$
	private static final String ASSERTIONS_CLASS= "org.assertj.core.api.Assertions"; //$NON-NLS-1$
	private static final String DESCRIPTABLE_INTERFACE= "org.assertj.core.api.Descriptable"; //$NON-NLS-1$
	private static final String ABSTRACT_ASSERT_CLASS= "org.assertj.core.api.AbstractAssert"; //$NON-NLS-1$
	private static final String BOOLEAN_ASSERT_CLASS= "org.assertj.core.api.AbstractBooleanAssert"; //$NON-NLS-1$
	private static final String OFFSET_CLASS= "org.assertj.core.data.Offset"; //$NON-NLS-1$

	private static final String FAIL_METHOD= "fail"; //$NON-NLS-1$
	private static final String IS_NOT_EQUAL_TO_METHOD= "isNotEqualTo"; //$NON-NLS-1$
	private static final String DESCRIBED_AS_METHOD= "describedAs"; //$NON-NLS-1$
	private static final String AS_METHOD= "as"; //$NON-NLS-1$
	private static final String EQUALS_METHOD= "equals"; //$NON-NLS-1$
	private static final String IS_EQUAL_TO_METHOD= "isEqualTo"; //$NON-NLS-1$
	private static final String IS_FALSE_METHOD= "isFalse"; //$NON-NLS-1$
	private static final String IS_TRUE_METHOD= "isTrue"; //$NON-NLS-1$
	private static final String ASSERT_THAT_METHOD= "assertThat"; //$NON-NLS-1$

	/**
	 * Init canUseAssertNotEquals.
	 */
	public AssertJCleanUp() {
		canUseAssertNotEquals= true;
	}

	@Override
	public String getName() {
		return MultiFixMessages.CleanUpRefactoringWizard_AssertJCleanUp_name;
	}

	@Override
	public String getDescription() {
		return MultiFixMessages.CleanUpRefactoringWizard_AssertJCleanUp_description;
	}

	@Override
	public String getReason() {
		return MultiFixMessages.CleanUpRefactoringWizard_AssertJCleanUp_reason;
	}

	@Override
	protected Pair<Expression, Expression> getActualAndExpected(final Expression leftValue,
			final Expression rightValue) {
		return Pair.of(rightValue, leftValue);
	}

	@Override
	public Set<String> getClassesToImport() {
		return new HashSet<>(Arrays.asList(OFFSET_CLASS));
	}

	@Override
	public boolean maybeRefactorMethodInvocation(final MethodInvocation node, final Set<String> classesToUseWithImport,
			final Set<String> importsToAdd) {
		MethodInvocation actual= ASTNodes.as(node.getExpression(), MethodInvocation.class);
		Expression message= null;

		if (actual != null
				&& (ASTNodes.usesGivenSignature(actual, DESCRIPTABLE_INTERFACE, AS_METHOD, String.class.getCanonicalName(), Object[].class.getCanonicalName())
						|| ASTNodes.usesGivenSignature(actual, DESCRIPTABLE_INTERFACE, DESCRIBED_AS_METHOD, String.class.getCanonicalName(), Object[].class.getCanonicalName()))) {
			message= actual;
			actual= ASTNodes.as(actual.getExpression(), MethodInvocation.class);
		}

		if (actual != null
				&& ASSERT_THAT_METHOD.equals(actual.getName().getIdentifier())
				&& actual.resolveMethodBinding() != null
				&& ASTNodes.hasType(actual.resolveMethodBinding().getDeclaringClass(), ASSERTIONS_CLASS)
				&& actual.arguments().size() == 1) {
			Expression actualExpression= (Expression) actual.arguments().get(0);

			if (ASTNodes.usesGivenSignature(node, BOOLEAN_ASSERT_CLASS, IS_TRUE_METHOD)) {
				return maybeRefactorStatement(classesToUseWithImport, importsToAdd,
						node, actual, true,
						actualExpression, message, false);
			}

			if (ASTNodes.usesGivenSignature(node, BOOLEAN_ASSERT_CLASS, IS_FALSE_METHOD)) {
				return maybeRefactorStatement(classesToUseWithImport, importsToAdd,
						node, actual, false,
						actualExpression, message, false);
			}

			if (ASTNodes.usesGivenSignature(node, BOOLEAN_ASSERT_CLASS, IS_EQUAL_TO_METHOD, boolean.class.getCanonicalName())) {
				final Boolean booleanConstant= ASTNodes.booleanConstant((Expression) node.arguments().get(0));

				if (booleanConstant != null) {
					return maybeRefactorStatement(classesToUseWithImport, importsToAdd,
							node, actual, booleanConstant,
							actualExpression, message, true);
				}

				final Boolean actualConstant= ASTNodes.booleanConstant(actualExpression);

				if (actualConstant != null) {
					return maybeRefactorStatement(classesToUseWithImport, importsToAdd,
							node, actual, actualConstant,
							(Expression) node.arguments().get(0), message, true);
				}
			} else if (ASTNodes.usesGivenSignature(node, BOOLEAN_ASSERT_CLASS, IS_NOT_EQUAL_TO_METHOD, boolean.class.getCanonicalName())) {
				final Boolean booleanConstant= ASTNodes.booleanConstant((Expression) node.arguments().get(0));

				if (booleanConstant != null) {
					return maybeRefactorStatement(classesToUseWithImport, importsToAdd,
							node, actual, !booleanConstant,
							actualExpression, message, true);
				}

				final Boolean actualConstant= ASTNodes.booleanConstant(actualExpression);

				if (actualConstant != null) {
					return maybeRefactorStatement(classesToUseWithImport, importsToAdd,
							node, actual, !actualConstant,
							(Expression) node.arguments().get(0), message, true);
				}
			}

			if (ASTNodes.usesGivenSignature(node, ABSTRACT_ASSERT_CLASS, EQUALS_METHOD, Object.class.getCanonicalName())
					|| ASTNodes.usesGivenSignature(node, ABSTRACT_ASSERT_CLASS, IS_EQUAL_TO_METHOD, Object.class.getCanonicalName())
					|| ASTNodes.usesGivenSignature(node, BOOLEAN_ASSERT_CLASS, IS_EQUAL_TO_METHOD, boolean.class.getCanonicalName())
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractIntegerAssert", IS_EQUAL_TO_METHOD, int.class.getCanonicalName()) //$NON-NLS-1$
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractLongAssert", IS_EQUAL_TO_METHOD, long.class.getCanonicalName()) //$NON-NLS-1$
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractDoubleAssert", IS_EQUAL_TO_METHOD, double.class.getCanonicalName()) //$NON-NLS-1$
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractFloatAssert", IS_EQUAL_TO_METHOD, float.class.getCanonicalName()) //$NON-NLS-1$
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractShortAssert", IS_EQUAL_TO_METHOD, short.class.getCanonicalName()) //$NON-NLS-1$
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractCharacterAssert", IS_EQUAL_TO_METHOD, char.class.getCanonicalName()) //$NON-NLS-1$
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractByteAssert", IS_EQUAL_TO_METHOD, byte.class.getCanonicalName())) { //$NON-NLS-1$
				return maybeRefactorToEquality(classesToUseWithImport, importsToAdd, node,
						actual, true, actualExpression, (Expression) node.arguments().get(0), message);
			}

			if (ASTNodes.usesGivenSignature(node, ABSTRACT_ASSERT_CLASS, IS_NOT_EQUAL_TO_METHOD, Object.class.getCanonicalName())
					|| ASTNodes.usesGivenSignature(node, BOOLEAN_ASSERT_CLASS, IS_NOT_EQUAL_TO_METHOD, boolean.class.getCanonicalName())
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractIntegerAssert", IS_NOT_EQUAL_TO_METHOD, int.class.getCanonicalName()) //$NON-NLS-1$
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractLongAssert", IS_NOT_EQUAL_TO_METHOD, long.class.getCanonicalName()) //$NON-NLS-1$
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractDoubleAssert", IS_NOT_EQUAL_TO_METHOD, double.class.getCanonicalName()) //$NON-NLS-1$
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractFloatAssert", IS_NOT_EQUAL_TO_METHOD, float.class.getCanonicalName()) //$NON-NLS-1$
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractShortAssert", IS_NOT_EQUAL_TO_METHOD, short.class.getCanonicalName()) //$NON-NLS-1$
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractCharacterAssert", IS_NOT_EQUAL_TO_METHOD, char.class.getCanonicalName()) //$NON-NLS-1$
					|| ASTNodes.usesGivenSignature(node, "org.assertj.core.api.AbstractByteAssert", IS_NOT_EQUAL_TO_METHOD, byte.class.getCanonicalName())) { //$NON-NLS-1$
				return maybeRefactorToEquality(classesToUseWithImport, importsToAdd, node,
						actual, false, actualExpression, (Expression) node.arguments().get(0), message);
			}
		}

		return true;
	}

	@Override
	public boolean maybeRefactorIfStatement(final IfStatement node, final Set<String> classesToUseWithImport,
			final Set<String> importsToAdd) {
		List<Statement> statements= ASTNodes.asList(node.getThenStatement());

		if (node.getElseStatement() == null && statements.size() == 1) {
			MethodInvocation mi= ASTNodes.asExpression(statements.get(0), MethodInvocation.class);

			if (ASTNodes.usesGivenSignature(mi, ASSERTIONS_CLASS, FAIL_METHOD, String.class.getCanonicalName())
					|| ASTNodes.usesGivenSignature(mi, FAIL_CLASS, FAIL_METHOD, String.class.getCanonicalName())
					|| ASTNodes.usesGivenSignature(mi, ASSERTIONS_CLASS, FAIL_METHOD, String.class.getCanonicalName(), Object[].class.getCanonicalName())
					|| ASTNodes.usesGivenSignature(mi, FAIL_CLASS, FAIL_METHOD, String.class.getCanonicalName(), Object[].class.getCanonicalName())) {
				if (mi.arguments() == null
						|| mi.arguments().size() == 1 && ASTNodes.as((Expression) mi.arguments().get(0), NullLiteral.class) != null) {
					return maybeRefactorStatement(classesToUseWithImport, importsToAdd, node, mi, false, node.getExpression(), null, true);
				}

				return maybeRefactorIfObjectsAreNotUsed(classesToUseWithImport, importsToAdd, node, mi, false, node.getExpression(), mi);
			}
		}

		return true;
	}

	@Override
	protected MethodInvocation invokeMethod(final Set<String> classesToUseWithImport, final Set<String> importsToAdd,
			final MethodInvocation originalMethod, final String methodName, final Expression copyOfActual,
			final Expression copyOfExpected, final Expression delta, final Expression failureMessage) {
		ASTNodeFactory ast= cuRewrite.getASTBuilder();

		String qualifiedClassName= originalMethod.resolveMethodBinding().getDeclaringClass().getQualifiedName();

		Expression qualifiedClass;
		if (!FAIL_METHOD.equals(methodName) && FAIL_CLASS.equals(qualifiedClassName)) {
			qualifiedClassName= ASSERTIONS_CLASS;
			qualifiedClass= null;
		} else if (originalMethod.getExpression() != null) {
			qualifiedClass= ASTNodes.createMoveTarget(cuRewrite.getASTRewrite(), originalMethod.getExpression());
		} else {
			qualifiedClass= null;
		}

		if (originalMethod.getExpression() == null && !staticImports.contains(qualifiedClassName + "." + methodName) //$NON-NLS-1$
				&& !staticImports.contains(qualifiedClassName + ".*")) { //$NON-NLS-1$
			qualifiedClass= ast.name(qualifiedClassName);
		}

		if (FAIL_METHOD.equals(methodName)) {
			return invokeFail(failureMessage, qualifiedClass);
		}

		return invokeQualifiedMethod(classesToUseWithImport, importsToAdd, qualifiedClass, methodName, copyOfActual, copyOfExpected, delta, failureMessage);
	}

	private MethodInvocation invokeFail(final Expression failureMessage, final Expression qualifiedClass) {
		ASTNodeFactory ast= cuRewrite.getASTBuilder();

		if (failureMessage != null) {
			MethodInvocation failureMethod= (MethodInvocation) failureMessage;
			List<Expression> copyOfMessages= new ArrayList<>(failureMethod.arguments().size());

			for (Object message : failureMethod.arguments()) {
				copyOfMessages.add(ASTNodes.createMoveTarget(cuRewrite.getASTRewrite(), ASTNodes.getUnparenthesedExpression((Expression) message)));
			}

			return ast.newMethodInvocation(qualifiedClass, FAIL_METHOD, copyOfMessages);
		}

		return ast.newMethodInvocation(qualifiedClass, FAIL_METHOD, ast.null0());
	}

	@Override
	protected MethodInvocation invokeQualifiedMethod(final Set<String> classesToUseWithImport, final Set<String> importsToAdd,
			final Expression copyOfClass, final String methodName, final Expression copyOfActual,
			final Expression copyOfExpected, final Expression delta, final Expression failureMessage) {
		ASTNodeFactory ast= cuRewrite.getASTBuilder();

		String finalMethodName= getFinalMethodName(methodName);

		Expression assertionMethod= ast.newMethodInvocation(copyOfClass, ASSERT_THAT_METHOD, copyOfActual);

		if (failureMessage != null) {
			MethodInvocation failureMethod= (MethodInvocation) failureMessage;
			String method= failureMethod.getName().getIdentifier();
			List<Expression> copyOfMessages= new ArrayList<>(failureMethod.arguments().size());

			for (Object message : failureMethod.arguments()) {
				copyOfMessages.add(ASTNodes.createMoveTarget(cuRewrite.getASTRewrite(), ASTNodes.getUnparenthesedExpression((Expression) message)));
			}

			assertionMethod= ast.newMethodInvocation(assertionMethod, DESCRIBED_AS_METHOD.equals(method) ? method : AS_METHOD, copyOfMessages);
		}

		if (copyOfExpected != null) {
			if (delta != null && IS_EQUAL_TO_METHOD.equals(finalMethodName)) {
				importsToAdd.add(OFFSET_CLASS);
				final String offsetClassname= classesToUseWithImport.contains(OFFSET_CLASS) ? "Offset" : OFFSET_CLASS; //$NON-NLS-1$
				return ast.newMethodInvocation(assertionMethod, finalMethodName, copyOfExpected, ast.newMethodInvocation(offsetClassname, "offset", ast.createCopyTarget(delta))); //$NON-NLS-1$
			}

			return ast.newMethodInvocation(assertionMethod, finalMethodName, copyOfExpected);
		}

		return ast.newMethodInvocation(assertionMethod, finalMethodName);
	}

	private String getFinalMethodName(final String methodName) {
		switch (methodName) {
		case "assertTrue": //$NON-NLS-1$
			return IS_TRUE_METHOD;

		case "assertFalse": //$NON-NLS-1$
			return IS_FALSE_METHOD;

		case "assertNull": //$NON-NLS-1$
			return "isNull"; //$NON-NLS-1$

		case "assertNotNull": //$NON-NLS-1$
			return "isNotNull"; //$NON-NLS-1$

		case "assertEquals": //$NON-NLS-1$
			return IS_EQUAL_TO_METHOD;

		case "assertNotEquals": //$NON-NLS-1$
			return IS_NOT_EQUAL_TO_METHOD;

		case "assertSame": //$NON-NLS-1$
			return "isSameAs"; //$NON-NLS-1$

		case "assertNotSame": //$NON-NLS-1$
			return "isNotSameAs"; //$NON-NLS-1$
		}

		return null;
	}
}
