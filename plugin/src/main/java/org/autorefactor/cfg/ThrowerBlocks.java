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
package org.autorefactor.cfg;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.autorefactor.util.Utils;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;

/** Holds all the basic blocks that can potentially throw. */
class ThrowerBlocks {
	private final Map<CFGBasicBlock, Set<ITypeBinding>> potentialThrowingBlocks= new HashMap<>();
	private final Map<CFGEdgeBuilder, Set<ITypeBinding>> potentialThrowingEdges= new HashMap<>();

	public void addThrow(final CFGBasicBlock basicBlock, final ITypeBinding... exceptionTypes) {
		if (exceptionTypes != null && exceptionTypes.length > 0) {
			potentialThrowingBlocks.put(basicBlock, newSet(exceptionTypes));
		}
	}

	public void addThrow(final CFGEdgeBuilder liveEdge, final Set<ITypeBinding> exceptionTypes) {
		if (!Utils.isEmpty(exceptionTypes)) {
			potentialThrowingEdges.put(liveEdge, exceptionTypes);
		}
	}

	public void addThrow(final Expression e, final ITypeBinding... newExceptions) {
		// TODO JNR remove
	}

	private HashSet<ITypeBinding> newSet(final ITypeBinding... exceptionTypes) {
		return new HashSet<>(Arrays.asList(exceptionTypes));
	}

	public List<CFGBasicBlock> selectBlocksThrowing(final ITypeBinding exceptionTypeToFind) {
		List<CFGBasicBlock> results= new LinkedList<>();
		for (Entry<CFGBasicBlock, Set<ITypeBinding>> entry : potentialThrowingBlocks.entrySet()) {
			Set<ITypeBinding> thrownTypes= entry.getValue();
			if (exceptionTypeToFind == null || thrownTypes.contains(exceptionTypeToFind)) {
				results.add(entry.getKey());
			}
		}

		return results;
	}

	public Map<CFGBasicBlock, Set<ITypeBinding>> selectBlocksThrowingOtherThan(
			final Set<ITypeBinding> exceptionTypesToReject) {
		Map<CFGBasicBlock, Set<ITypeBinding>> results= new HashMap<>();
		for (Entry<CFGBasicBlock, Set<ITypeBinding>> entry : potentialThrowingBlocks.entrySet()) {
			Set<ITypeBinding> bindings= getNonMatching(exceptionTypesToReject, entry.getValue());
			if (!bindings.isEmpty()) {
				results.put(entry.getKey(), bindings);
			}
		}

		return results;
	}

	public List<CFGEdgeBuilder> selectEdgesThrowing(final ITypeBinding exceptionTypeToFind) {
		List<CFGEdgeBuilder> results= new LinkedList<>();
		for (Entry<CFGEdgeBuilder, Set<ITypeBinding>> entry : potentialThrowingEdges.entrySet()) {
			Set<ITypeBinding> thrownTypes= entry.getValue();
			if (exceptionTypeToFind == null || thrownTypes.contains(exceptionTypeToFind)) {
				results.add(entry.getKey());
			}
		}

		return results;
	}

	private Set<ITypeBinding> getNonMatching(final Set<ITypeBinding> exceptionTypes, final Set<ITypeBinding> thrownTypes) {
		Set<ITypeBinding> results= new HashSet<>();
		for (ITypeBinding thrownType : thrownTypes) {
			if (!exceptionTypes.contains(thrownType)) {
				results.add(thrownType);
			}
		}

		return results;
	}

	@Override
	public String toString() {
		return "potentialThrowingBlocks=" + potentialThrowingBlocks + " potentialThrowingEdges=" //$NON-NLS-1$ //$NON-NLS-2$
				+ potentialThrowingEdges;
	}
}
