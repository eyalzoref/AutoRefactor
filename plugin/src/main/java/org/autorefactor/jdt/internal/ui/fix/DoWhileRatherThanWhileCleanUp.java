/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2017 Fabrice Tiercelin - Split the code
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
import java.util.Collections;
import java.util.List;

import org.autorefactor.jdt.core.dom.ASTRewrite;
import org.autorefactor.jdt.internal.corext.dom.ASTNodeFactory;
import org.autorefactor.jdt.internal.corext.dom.ASTNodes;
import org.autorefactor.jdt.internal.corext.dom.VarDefinitionsUsesVisitor;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;

/**
 * DoWhileRatherThanWhileCleanUp.
 *
 * @see #getDescription()
 */
public class DoWhileRatherThanWhileCleanUp extends AbstractCleanUpRule {
	@Override
	public String getName() {
		return MultiFixMessages.CleanUpRefactoringWizard_DoWhileRatherThanWhileCleanUp_name;
	}

	@Override
	public String getDescription() {
		return MultiFixMessages.CleanUpRefactoringWizard_DoWhileRatherThanWhileCleanUp_description;
	}

	@Override
	public String getReason() {
		return MultiFixMessages.CleanUpRefactoringWizard_DoWhileRatherThanWhileCleanUp_reason;
	}

	@Override
	public boolean visit(final WhileStatement node) {
		if (ASTNodes.isPassiveWithoutFallingThrough(node.getExpression()) && Boolean.TRUE.equals(hasAlwaysValue(node, node.getExpression()))) {
			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			ASTNodeFactory ast= cuRewrite.getASTBuilder();

			rewrite.replace(node, ast.doWhile(ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(node.getExpression())), ASTNodes.createMoveTarget(rewrite, node.getBody())), null);
			return false;
		}

		return true;
	}

	private Object hasAlwaysValue(final Statement node, final Expression condition) {
		Object constantCondition= condition.resolveConstantExpressionValue();

		if (constantCondition != null) {
			return constantCondition;
		}

		Long integerLiteral= ASTNodes.getIntegerLiteral(condition);

		if (integerLiteral != null) {
			return integerLiteral;
		}

		SimpleName variable= ASTNodes.as(condition, SimpleName.class);

		if (variable != null && variable.resolveBinding() != null && variable.resolveBinding().getKind() == IBinding.VARIABLE) {
			List<Statement> previousSiblings= new ArrayList<>(ASTNodes.getPreviousSiblings(node));

			Collections.reverse(previousSiblings);

			for (Statement previousSibling : previousSiblings) {
				VarDefinitionsUsesVisitor visitor= new VarDefinitionsUsesVisitor((IVariableBinding) variable.resolveBinding(), previousSibling, true).find();

				if (!visitor.getReads().isEmpty() || visitor.getWrites().size() > 1) {
					return null;
				}

				if (!visitor.getWrites().isEmpty()) {
					SimpleName write= visitor.getWrites().get(0);

					switch (write.getParent().getNodeType()) {
					case ASTNode.ASSIGNMENT:
						Assignment assignment= (Assignment) write.getParent();

						if (ASTNodes.hasOperator(assignment, Assignment.Operator.ASSIGN)) {
							return hasAlwaysValue(previousSibling, assignment.getRightHandSide());
						}

						break;

					case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
						VariableDeclarationFragment fragment= (VariableDeclarationFragment) write.getParent();

						if (fragment.getInitializer() != null) {
							return hasAlwaysValue(previousSibling, fragment.getInitializer());
						}

						break;

					case ASTNode.SINGLE_VARIABLE_DECLARATION:
						SingleVariableDeclaration singleVariableDeclaration= (SingleVariableDeclaration) write.getParent();

						if (singleVariableDeclaration.getInitializer() != null) {
							return hasAlwaysValue(previousSibling, singleVariableDeclaration.getInitializer());
						}

						break;

					default:
						break;
					}

					return null;
				}
			}

			return null;
		}

		InfixExpression infixExpression= ASTNodes.as(condition, InfixExpression.class);

		if (infixExpression != null) {
			if (!infixExpression.hasExtendedOperands()
					&& ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.EQUALS,
							InfixExpression.Operator.NOT_EQUALS,
							InfixExpression.Operator.GREATER,
							InfixExpression.Operator.GREATER_EQUALS,
							InfixExpression.Operator.LESS,
							InfixExpression.Operator.LESS_EQUALS)) {
				Object leftOperand= hasAlwaysValue(node, infixExpression.getLeftOperand());
				Object rightOperand= hasAlwaysValue(node, infixExpression.getRightOperand());

				if (leftOperand instanceof Number && rightOperand instanceof Number) {
					Number leftNumber= (Number) leftOperand;
					Number rightNumber= (Number) rightOperand;

					if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.EQUALS)) {
						return leftNumber.longValue() == rightNumber.longValue();
					}

					if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.NOT_EQUALS)) {
						return leftNumber.longValue() != rightNumber.longValue();
					}

					if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.GREATER)) {
						return leftNumber.longValue() > rightNumber.longValue();
					}

					if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.GREATER_EQUALS)) {
						return leftNumber.longValue() >= rightNumber.longValue();
					}

					if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.LESS)) {
						return leftNumber.longValue() < rightNumber.longValue();
					}

					if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.LESS_EQUALS)) {
						return leftNumber.longValue() <= rightNumber.longValue();
					}
				}

				return null;
			}

			if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.CONDITIONAL_AND,
							InfixExpression.Operator.AND)) {
				for (Expression operand : ASTNodes.getAllOperands(infixExpression)) {
					final Object hasAlwaysValue= hasAlwaysValue(node, operand);

					if (!Boolean.TRUE.equals(hasAlwaysValue)) {
						return hasAlwaysValue;
					}
				}

				return Boolean.TRUE;
			}

			if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.CONDITIONAL_OR,
							InfixExpression.Operator.OR)) {
				for (Expression operand : ASTNodes.getAllOperands(infixExpression)) {
					final Object hasAlwaysValue= hasAlwaysValue(node, operand);

					if (!Boolean.FALSE.equals(hasAlwaysValue)) {
						return hasAlwaysValue;
					}
				}

				return Boolean.FALSE;
			}
		}

		return false;
	}
}
