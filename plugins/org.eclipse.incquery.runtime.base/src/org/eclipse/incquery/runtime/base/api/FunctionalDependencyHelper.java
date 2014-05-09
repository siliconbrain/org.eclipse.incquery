/*******************************************************************************
 * Copyright (c) 2010-2013, Adam Dudas, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Adam Dudas - initial API and implementation
 *******************************************************************************/
package org.eclipse.incquery.runtime.base.api;

import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;

/**
 * Helper utility class for functional dependency analysis.
 * 
 * Throughout this class attribute sets are represented as generic sets and functional dependencies as maps from
 * attribute set (generic sets) to attribute set (generic sets)
 * 
 * @author Adam Dudas
 * 
 */
public class FunctionalDependencyHelper {
    /**
     * Get the closure of the specified attribute set relative to the specified functional dependencies.
     * 
     * @param attributes
     *            The attributes to get the closure of.
     * @param dependencies
     *            The functional dependencies of which the closure operation is relative to.
     * @return The closure of the specified attribute set relative to the specified functional dependencies.
     */
    public static <A> Set<A> closureOf(Set<A> attributes, Map<Set<A>, Set<A>> dependencies) {
        Set<A> closureSet = new HashSet<A>();

        for (Set<A> closureSet1 = new HashSet<A>(attributes); closureSet.addAll(closureSet1);) {
            closureSet1 = new HashSet<A>(closureSet);
            for (Entry<Set<A>, Set<A>> dependency : dependencies.entrySet()) {
                if (closureSet.containsAll(dependency.getKey()))
                    closureSet1.addAll(dependency.getValue());
            }
        }

        return closureSet;
    }

    /**
     * Get the closure of the specified attribute set relative to the specified functional dependencies.
     * 
     * @param attributes
     *            The attributes to get the closure of.
     * @param dependencies
     *            The functional dependencies of which the closure operation is relative to.
     * @return The closure of the specified attribute set relative to the specified functional dependencies.
     */
    public static <A> Set<A> closureOf(Set<A> attributes, Set<FunctionalDependency<A>> dependencies) {
        Set<A> closureSet = new HashSet<A>();

        for (Set<A> closureSet1 = new HashSet<A>(attributes); closureSet.addAll(closureSet1);) {
            closureSet1 = new HashSet<A>(closureSet);
            for (FunctionalDependency<A> dependency : dependencies) {
                if (closureSet.containsAll(dependency.getDeterminant())) {
                    closureSet1.add(dependency.getDependent());
                }
            }
        }

        return closureSet;
    }

    /**
     * Check whether two set of functional dependencies are equal (i.e. generate the same dependencies).
     * 
     * @param dependenciesA
     *            A set of functional dependencies (represented as a map).
     * @param dependenciesB
     *            Another set of functional dependencies (represented as a map).
     * @return True if the two set of functional dependencies are equal; otherwise, false.
     */
    public static <A> boolean areEqualDependencySets(Map<Set<A>, Set<A>> dependenciesA,
            Map<Set<A>, Set<A>> dependenciesB) {
        // for all X -> Y in F, if Y subset of closureOf(X,G) => X -> Y in G*
        for (Entry<Set<A>, Set<A>> dependency : dependenciesA.entrySet()) {
            if (!closureOf(dependency.getKey(), dependenciesB).containsAll(dependency.getValue()))
                return false;
        }
        // for all X -> Y in G, if Y subset of closureOf(X,F) => X -> Y in F*
        for (Entry<Set<A>, Set<A>> dependency : dependenciesB.entrySet()) {
            if (!closureOf(dependency.getKey(), dependenciesA).containsAll(dependency.getValue()))
                return false;
        }
        return true;
    }

    /**
     * Check whether two set of functional dependencies are equal (i.e. generate the same dependencies).
     * 
     * @param dependenciesA
     *            A set of functional dependencies.
     * @param dependenciesB
     *            Another set of functional dependencies.
     * @return True if the two set of functional dependencies are equal; otherwise, false.
     */
    public static <A> boolean areEqualDependencySets(Set<FunctionalDependency<A>> dependenciesA,
            Set<FunctionalDependency<A>> dependenciesB) {
        return areEqualDependencySets(setToMap(dependenciesA), setToMap(dependenciesB));
    }

    /**
     * Check whether a set of functional dependencies imply a specific dependency 
     * (i.e. the dependency is in the set's closure).
     * 
     * @param dependencies
     *            A set of functional dependencies.
     * @param dependency
     *            A specific functional dependency.
     * @return True if the set of dependencies imply the specified dependency; otherwise, false.
     */
    public static <A> boolean implies(Set<FunctionalDependency<A>> dependencies, FunctionalDependency<A> dependency) {
        return closureOf(dependency.getDeterminant(), dependencies).contains(dependency.getDependent());
    }

    /**
     * Convert functional dependencies from set representation to map representation.
     * 
     * @param dependencies
     *            A set of functional dependencies.
     * @return A map representing functional dependencies.
     */
    public static <A> Map<Set<A>, Set<A>> setToMap(Set<FunctionalDependency<A>> dependencies) {
        Map<Set<A>, Set<A>> result = new HashMap<Set<A>, Set<A>>();

        for (FunctionalDependency<A> dependency : dependencies) {
            if (!result.containsKey(dependency.getDeterminant()))
                result.put(dependency.getDeterminant(), new HashSet<A>());

            result.get(dependency.getDeterminant()).add(dependency.getDependent());
        }

        return result;
    }

    /**
     * Convert functional dependencies from map representation to set representation.
     * 
     * @param dependencies
     *            A map representing functional dependencies.
     * @return A set of functional dependencies.
     */
    public static <A> Set<FunctionalDependency<A>> mapToSet(Map<Set<A>, Set<A>> dependencies) {
        Set<FunctionalDependency<A>> result = new HashSet<FunctionalDependency<A>>();

        for (Map.Entry<Set<A>, Set<A>> dependency : dependencies.entrySet()) {
            for (A a : dependency.getValue()) {
                result.add(new FunctionalDependency<A>(dependency.getKey(), a));
            }
        }

        return result;
    }

    /**
     * Project a set of functional dependencies onto a set of attributes.
     * 
     * @param dependencies
     *            The functional dependencies to project.
     * @param attributes
     *            The set of attributes to project onto.
     * @return The projection of the specified functional dependencies onto the set of attributes.
     */
    public static <A> Map<Set<A>, Set<A>> project(Map<Set<A>, Set<A>> dependencies, Set<A> attributes) {
        // remove trivial dependencies
        dependencies = setToMap(Sets.filter(mapToSet(dependencies), new IsNonTrivialDependencyPredicate<A>()));

        Map<Set<A>, Set<A>> projectedDependencies = new HashMap<Set<A>, Set<A>>();

        for (Set<A> subset : Sets.powerSet(attributes)) {
            Set<A> closureSet = closureOf(subset, dependencies);
            projectedDependencies.put(subset, Sets.intersection(closureSet, attributes));
        }

        return minimalBasis(projectedDependencies);
    }

    public static <A> Set<FunctionalDependency<A>> project(Set<FunctionalDependency<A>> dependencies, Set<A> attributes) {
        return mapToSet(project(setToMap(dependencies), attributes));
    }

    /**
     * Compute a minimal basis of the specified functional dependencies.
     * 
     * @param dependencies
     *            The functional dependencies to compute a minimal basis for.
     * @return A minimal basis of the specified functional dependencies.
     */
    public static <A> Map<Set<A>, Set<A>> minimalBasis(Map<Set<A>, Set<A>> dependencies) {
        return setToMap(minimalBasis(mapToSet(dependencies)));
    }

    /**
     * Compute a minimal basis of the specified functional dependencies.
     * 
     * @param dependencies
     *            The functional dependencies to compute a minimal basis for.
     * @return A minimal basis of the specified functional dependencies.
     */
    public static <A> Set<FunctionalDependency<A>> minimalBasis(Set<FunctionalDependency<A>> dependencies) {
        // remove trivial dependencies
        dependencies = Sets.filter(dependencies, new IsNonTrivialDependencyPredicate<A>());

        Map<Set<A>, Set<A>> deps = setToMap(dependencies);

        // remove redundant dependencies
        Set<FunctionalDependency<A>> tested = new HashSet<FunctionalDependency<A>>();
        Deque<FunctionalDependency<A>> untested = new LinkedList<FunctionalDependency<A>>(dependencies);

        while (!untested.isEmpty()) {
            FunctionalDependency<A> dependency = untested.pop();

            Set<FunctionalDependency<A>> rest = new HashSet<FunctionalDependency<A>>(tested);
            rest.addAll(untested);

            if (!areEqualDependencySets(setToMap(rest), deps)) {
                // remove redundant elements of determinant
                Set<A> checked = new HashSet<A>();
                Deque<A> unchecked = new LinkedList<A>(dependency.getDeterminant());

                while (!unchecked.isEmpty()) {
                    A attribute = unchecked.pop();

                    Set<A> tmp1 = new HashSet<A>(checked);
                    tmp1.addAll(unchecked);
                    FunctionalDependency<A> rest1 = new FunctionalDependency<A>(tmp1, dependency.getDependent());
                    rest.add(rest1);

                    if (!areEqualDependencySets(setToMap(rest), deps)) {
                        checked.add(attribute);
                    }

                    rest.remove(rest1);
                }

                tested.add(new FunctionalDependency<A>(checked, dependency.getDependent()));
            }
        }

        return tested;
    }

    private static class IsNonTrivialDependencyPredicate<A> implements Predicate<FunctionalDependency<A>> {
        @Override
        public boolean apply(FunctionalDependency<A> input) {
            return !input.isTrivial();
        }
    }
}
