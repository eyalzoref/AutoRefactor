/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2016 Luis Cruz - Android Refactoring
 * Copyright (C) 2016 Jean-Noël Rouvignac - code cleanups
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
import org.autorefactor.jdt.internal.corext.dom.FinderVisitor;
import org.autorefactor.preferences.Preferences;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/** See {@link #getDescription()} method. */
public class AndroidWakeLockCleanUp extends AbstractCleanUpRule {
	@Override
	public String getName() {
		return MultiFixMessages.CleanUpRefactoringWizard_AndroidWakeLockCleanUp_name;
	}

	@Override
	public String getDescription() {
		return MultiFixMessages.CleanUpRefactoringWizard_AndroidWakeLockCleanUp_description;
	}

	@Override
	public String getReason() {
		return MultiFixMessages.CleanUpRefactoringWizard_AndroidWakeLockCleanUp_reason;
	}

	@Override
	public boolean isEnabled(final Preferences preferences) {
		// FIXME enable only when android libraries are detected
		return super.isEnabled(preferences);
	}

	@Override
	public boolean visit(final MethodInvocation node) {
		if (ASTNodes.usesGivenSignature(node, "android.os.PowerManager.WakeLock", "release")) { //$NON-NLS-1$ //$NON-NLS-2$
			// Check whether it is being called in onDestroy()
			MethodDeclaration enclosingMethod= ASTNodes.getAncestor(node, MethodDeclaration.class);
			if (ASTNodes.usesGivenSignature(enclosingMethod, "android.app.Activity", "onDestroy")) { //$NON-NLS-1$ //$NON-NLS-2$
				ASTRewrite rewrite= cuRewrite.getASTRewrite();

				TypeDeclaration typeDeclaration= ASTNodes.getAncestor(enclosingMethod, TypeDeclaration.class);
				MethodDeclaration onPauseMethod= findMethod(typeDeclaration, "onPause"); //$NON-NLS-1$
				if (onPauseMethod != null && node.getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
					rewrite.remove(node.getParent(), null);
					rewrite.insertLast(onPauseMethod.getBody(), Block.STATEMENTS_PROPERTY, createWakelockReleaseStatement(node), null);
				} else {
					// Add the missing onPause() method to the class.
					rewrite.insertAfter(createOnPauseMethodDeclaration(), enclosingMethod, null);
				}

				return false;
			}
		} else if (ASTNodes.usesGivenSignature(node, "android.os.PowerManager.WakeLock", "acquire")) { //$NON-NLS-1$ //$NON-NLS-2$
			ASTRewrite rewrite= cuRewrite.getASTRewrite();

			TypeDeclaration typeDeclaration= ASTNodes.getAncestor(node, TypeDeclaration.class);
			ReleasePresenceChecker releasePresenceChecker= new ReleasePresenceChecker();
			if (!releasePresenceChecker.findOrDefault(typeDeclaration, false)) {
				MethodDeclaration onPauseMethod= findMethod(typeDeclaration, "onPause"); //$NON-NLS-1$
				if (onPauseMethod != null && node.getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
					rewrite.insertLast(onPauseMethod.getBody(), Block.STATEMENTS_PROPERTY, createWakelockReleaseStatement(node), null);
				} else {
					rewrite.insertLast(typeDeclaration, typeDeclaration.getBodyDeclarationsProperty(),
							createOnPauseMethodDeclaration(), null);
				}

				return false;
			}
		}

		return true;
	}

	private Statement createWakelockReleaseStatement(final MethodInvocation methodInvocation) {
		ASTNodeFactory ast= cuRewrite.getASTBuilder();

		return ast.if0(ast.not(ast.newMethodInvocation(ast.copyExpression(methodInvocation), "isHeld")), //$NON-NLS-1$
				ast.block(ast.toStatement(ast.newMethodInvocation(ast.copyExpression(methodInvocation), "release")))); //$NON-NLS-1$
	}

	private MethodDeclaration createOnPauseMethodDeclaration() {
		ASTNodeFactory ast= cuRewrite.getASTBuilder();

		return ast.method(ast.extendedModifiers(ast.annotation("Override"), ast.protected0()), "onPause", ast.parameters(), //$NON-NLS-1$ //$NON-NLS-2$
				ast.block(ast.toStatement(ast.superInvoke("onPause")))); //$NON-NLS-1$
	}

	private MethodDeclaration findMethod(final TypeDeclaration typeDeclaration, final String methodToFind) {
		if (typeDeclaration != null) {
			for (MethodDeclaration method : typeDeclaration.getMethods()) {
				IMethodBinding methodBinding= method.resolveBinding();
				if (methodBinding != null && methodToFind.equals(methodBinding.getName())
						&& method.parameters().isEmpty()) {
					return method;
				}
			}
		}

		return null;
	}

	private static class ReleasePresenceChecker extends FinderVisitor<Boolean> {
		@Override
		public boolean visit(final MethodInvocation node) {
			if (ASTNodes.usesGivenSignature(node, "android.os.PowerManager.WakeLock", "release")) { //$NON-NLS-1$ //$NON-NLS-2$
				setResult(true);
				return false;
			}

			return true;
		}
	}
}
