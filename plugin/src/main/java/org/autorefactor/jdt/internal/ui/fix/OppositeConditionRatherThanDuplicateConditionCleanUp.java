/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2018 Fabrice Tiercelin - initial API and implementation
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

import org.autorefactor.jdt.core.dom.ASTRewrite;
import org.autorefactor.jdt.internal.corext.dom.ASTNodeFactory;
import org.autorefactor.jdt.internal.corext.dom.ASTNodes;
import org.autorefactor.jdt.internal.corext.dom.ASTSemanticMatcher;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.Statement;

/**
 * Refactors:
 *
 * <pre>
 * if (a && ast) {
 *   {{code 1}}
 * } if (a) {
 *   {{code 2}}
 * } else {
 *   {{code 3}}
 * }
 * </pre>
 *
 * into
 *
 * <pre>
 * if (!a) {
 *   {{code 3}}
 * } if (ast) {
 *   {{code 1}}
 * } else {
 *   {{code 2}}
 * }
 * </pre>
 *
 * @see #getDescription()
 */
public class OppositeConditionRatherThanDuplicateConditionCleanUp extends AbstractCleanUpRule {
	@Override
	public String getName() {
		return MultiFixMessages.CleanUpRefactoringWizard_OppositeConditionRatherThanDuplicateConditionCleanUp_name;
	}

	@Override
	public String getDescription() {
		return MultiFixMessages.CleanUpRefactoringWizard_OppositeConditionRatherThanDuplicateConditionCleanUp_description;
	}

	@Override
	public String getReason() {
		return MultiFixMessages.CleanUpRefactoringWizard_OppositeConditionRatherThanDuplicateConditionCleanUp_reason;
	}

	@Override
	public boolean visit(final IfStatement node) {
		InfixExpression firstCondition= ASTNodes.as(node.getExpression(), InfixExpression.class);
		IfStatement secondIf= ASTNodes.as(node.getElseStatement(), IfStatement.class);

		if (firstCondition != null
				&& secondIf != null
				&& secondIf.getElseStatement() != null
				&& !firstCondition.hasExtendedOperands()
				&& ASTNodes.hasOperator(firstCondition, InfixExpression.Operator.AND, InfixExpression.Operator.CONDITIONAL_AND)
				&& ASTNodes.isPassive(firstCondition.getLeftOperand()) && ASTNodes.isPassive(firstCondition.getRightOperand())) {
			return maybeRefactorCondition(node, secondIf, firstCondition.getLeftOperand(),
					firstCondition.getRightOperand())
					&& maybeRefactorCondition(node, secondIf, firstCondition.getRightOperand(),
							firstCondition.getLeftOperand());
		}

		return true;
	}

	private boolean maybeRefactorCondition(final IfStatement node, final IfStatement secondIf,
			final Expression duplicateExpression, final Expression notDuplicateExpression) {
		if (ASTNodes.match(duplicateExpression, secondIf.getExpression())) {
			refactorCondition(node, duplicateExpression, notDuplicateExpression, secondIf.getThenStatement(),
					secondIf.getElseStatement());
			return false;
		}

		if (ASTSemanticMatcher.INSTANCE.matchOpposite(duplicateExpression, secondIf.getExpression())) {
			refactorCondition(node, duplicateExpression, notDuplicateExpression, secondIf.getElseStatement(),
					secondIf.getThenStatement());
			return false;
		}

		return true;
	}

	private void refactorCondition(final IfStatement node, final Expression duplicateExpression,
			final Expression notDuplicateExpression, final Statement positiveStatement, final Statement negativeStatement) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		ASTNodeFactory ast= cuRewrite.getASTBuilder();

		Statement negativeStmtCopy;
		if (negativeStatement instanceof IfStatement) {
			negativeStmtCopy= ast.block(ASTNodes.createMoveTarget(rewrite, negativeStatement));
		} else {
			negativeStmtCopy= ASTNodes.createMoveTarget(rewrite, negativeStatement);
		}

		Expression secondCond;
		Statement secondStmtCopy;
		Statement thirdStmtCopy;
		PrefixExpression negativeCond= ASTNodes.as(notDuplicateExpression, PrefixExpression.class);

		if (negativeCond != null && ASTNodes.hasOperator(negativeCond, PrefixExpression.Operator.NOT)) {
			secondCond= negativeCond.getOperand();
			secondStmtCopy= ASTNodes.createMoveTarget(rewrite, positiveStatement);
			thirdStmtCopy= ASTNodes.createMoveTarget(rewrite, node.getThenStatement());
		} else {
			secondCond= notDuplicateExpression;
			secondStmtCopy= ASTNodes.createMoveTarget(rewrite, node.getThenStatement());
			thirdStmtCopy= ASTNodes.createMoveTarget(rewrite, positiveStatement);
		}

		rewrite.replace(node.getExpression(), ast.negate(duplicateExpression), null);
		rewrite.replace(node.getThenStatement(), negativeStmtCopy, null);
		rewrite.replace(node.getElseStatement(), ast.if0(ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(secondCond)), secondStmtCopy, thirdStmtCopy), null);
	}
}
