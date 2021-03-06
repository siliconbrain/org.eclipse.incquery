/*******************************************************************************
 * Copyright (c) 2004-2008 Gabor Bergmann and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Gabor Bergmann - initial API and implementation
 *******************************************************************************/

package org.eclipse.incquery.runtime.rete.boundary;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.eclipse.incquery.runtime.matchers.IPatternMatcherContext;
import org.eclipse.incquery.runtime.matchers.IPatternMatcherContext.GeneralizationQueryDirection;
import org.eclipse.incquery.runtime.matchers.planning.QueryPlannerException;
import org.eclipse.incquery.runtime.matchers.planning.SubPlan;
import org.eclipse.incquery.runtime.matchers.psystem.queries.PQuery;
import org.eclipse.incquery.runtime.matchers.tuple.FlatTuple;
import org.eclipse.incquery.runtime.matchers.tuple.Tuple;
import org.eclipse.incquery.runtime.rete.collections.CollectionsFactory;
import org.eclipse.incquery.runtime.rete.matcher.IPatternMatcherRuntimeContext;
import org.eclipse.incquery.runtime.rete.matcher.ReteEngine;
import org.eclipse.incquery.runtime.rete.network.Direction;
import org.eclipse.incquery.runtime.rete.network.Network;
import org.eclipse.incquery.runtime.rete.network.Node;
import org.eclipse.incquery.runtime.rete.network.Production;
import org.eclipse.incquery.runtime.rete.network.Receiver;
import org.eclipse.incquery.runtime.rete.network.ReteContainer;
import org.eclipse.incquery.runtime.rete.network.Supplier;
import org.eclipse.incquery.runtime.rete.network.Tunnel;
import org.eclipse.incquery.runtime.rete.remote.Address;
import org.eclipse.incquery.runtime.rete.traceability.CompiledQuery;
import org.eclipse.incquery.runtime.rete.traceability.RecipeTraceInfo;

/**
 * Responsible for the storage, maintenance and communication of the nodes of the network that are accessible form the
 * outside for various reasons.
 *
 * @author Gabor Bergmann
 * 
 * <p> TODO: should eventually be merged into {@link InputConnector} and deleted
 *
 */
public class ReteBoundary {

    protected ReteEngine engine;
    protected Network network;
    protected ReteContainer headContainer;

    public ReteContainer getHeadContainer() {
        return headContainer;
    }

    protected IPatternMatcherRuntimeContext context;
    IPatternMatcherContext.GeneralizationQueryDirection generalizationQueryDirection;
	protected final InputConnector inputConnector;


    protected Map<SubPlan, Address<? extends Supplier>> subplanToAddressMapping;


    /**
     * SubPlans of parent nodes that have the key node as their child. For RETE --> SubPlan traceability, mainly at production
     * nodes.
     */
    protected Map<Address<? extends Receiver>, Set<SubPlan>> parentPlansOfReceiver;

    /**
     * Prerequisite: engine has its network and framework fields initialized
     *
     * @param headContainer
     */
    public ReteBoundary(ReteEngine engine) {
        super();
        this.engine = engine;
        this.network = engine.getReteNet();
        this.headContainer = network.getHeadContainer();
        inputConnector = network.getInputConnector();

        this.context = engine.getContext();
        this.generalizationQueryDirection = this.context.allowedGeneralizationQueryDirection();
        this.parentPlansOfReceiver = CollectionsFactory.getMap();//new HashMap<Address<? extends Receiver>, Set<SubPlan<Address<? extends Supplier>>>>();

        // productionsScoped = new HashMap<GTPattern, Map<Map<Integer,Scope>,Address<? extends Production>>>();
        subplanToAddressMapping = CollectionsFactory.getMap();

    }

    public Collection<? extends RecipeTraceInfo> getAllProductionNodes() {
        return engine.getCompiler().getCachedCompiledQueries().values();
    }

//    /**
//     * accesses the entity Root node under specified label; creates the node if it doesn't exist yet
//     */
//    public Address<? extends Tunnel> accessUnaryRoot(Object typeObject) {
//        Address<? extends Tunnel> tn;
//        tn = unaryRoots.get(typeObject);
//        if (tn == null) {
//            tn = headContainer.getProvisioner().newUniquenessEnforcerNode(1, typeObject);
//            unaryRoots.put(typeObject, tn);
//
//            new EntityFeeder(tn, context, network, this, typeObject).feed();
//
//            if (typeObject != null && generalizationQueryDirection == GeneralizationQueryDirection.BOTH) {
//                Collection<? extends Object> subTypes = context.enumerateDirectUnarySubtypes(typeObject);
//
//                for (Object subType : subTypes) {
//                    Address<? extends Tunnel> subRoot = accessUnaryRoot(subType);
//                    network.connectRemoteNodes(subRoot, tn, true);
//                }
//            }
//
//        }
//        return tn;
//    }
//
//    /**
//     * accesses the relation Root node under specified label; creates the node if it doesn't exist yet
//     */
//    public Address<? extends Tunnel> accessTernaryEdgeRoot(Object typeObject) {
//        Address<? extends Tunnel> tn;
//        tn = ternaryEdgeRoots.get(typeObject);
//        if (tn == null) {
//            tn = headContainer.getProvisioner().newUniquenessEnforcerNode(3, typeObject);
//            ternaryEdgeRoots.put(typeObject, tn);
//
//            new RelationFeeder(tn, context, network, this, typeObject).feed();
//
//            if (typeObject != null && generalizationQueryDirection == GeneralizationQueryDirection.BOTH) {
//                Collection<? extends Object> subTypes = context.enumerateDirectTernaryEdgeSubtypes(typeObject);
//
//                for (Object subType : subTypes) {
//                    Address<? extends Tunnel> subRoot = accessTernaryEdgeRoot(subType);
//                    network.connectRemoteNodes(subRoot, tn, true);
//                }
//            }
//        }
//        return tn;
//    }
//
//    /**
//     * accesses the reference Root node under specified label; creates the node if it doesn't exist yet
//     */
//    public Address<? extends Tunnel> accessBinaryEdgeRoot(Object typeObject) {
//        Address<? extends Tunnel> tn;
//        tn = binaryEdgeRoots.get(typeObject);
//        if (tn == null) {
//            tn = headContainer.getProvisioner().newUniquenessEnforcerNode(2, typeObject);
//            binaryEdgeRoots.put(typeObject, tn);
//
//            new ReferenceFeeder(tn, context, network, this, typeObject).feed();
//
//            if (typeObject != null && generalizationQueryDirection == GeneralizationQueryDirection.BOTH) {
//                Collection<? extends Object> subTypes = context.enumerateDirectBinaryEdgeSubtypes(typeObject);
//
//                for (Object subType : subTypes) {
//                    Address<? extends Tunnel> subRoot = accessBinaryEdgeRoot(subType);
//                    network.connectRemoteNodes(subRoot, tn, true);
//                }
//            }
//        }
//        return tn;
//    }
//
//    /**
//     * accesses the special direct containment relation Root node; creates the node if it doesn't exist yet
//     */
//    public Address<? extends Tunnel> accessContainmentRoot() {
//        if (containmentRoot == null) {
//            // containment: relation quasi-type
//            containmentRoot = headContainer.getProvisioner().newUniquenessEnforcerNode(2, "$containment");
//
//            new ContainmentFeeder(containmentRoot, context, network, this).feed();
//        }
//        return containmentRoot;
//    }
//
//    /**
//     * accesses the special transitive containment relation Root node; creates the node if it doesn't exist yet
//     */
//    public Address<? extends Supplier> accessContainmentTransitiveRoot() {
//        if (containmentTransitiveRoot == null) {
//            // transitive containment: derived
//            Address<? extends Tunnel> containmentTransitiveRoot = headContainer.getProvisioner().newUniquenessEnforcerNode(
//                    2, "$containmentTransitive");
//            network.connectRemoteNodes(accessContainmentRoot(), containmentTransitiveRoot, true);
//
//            final int[] actLI = { 1 };
//            final int arcLIw = 2;
//            final int[] actRI = { 0 };
//            final int arcRIw = 2;
//            Address<? extends IterableIndexer> jPrimarySlot = headContainer.getProvisioner().accessProjectionIndexer(
//                    accessContainmentRoot(), new TupleMask(actLI, arcLIw));
//            Address<? extends IterableIndexer> jSecondarySlot = headContainer.getProvisioner().accessProjectionIndexer(
//                    containmentTransitiveRoot, new TupleMask(actRI, arcRIw));
//
//            final int[] actRIcomp = { 1 };
//            final int arcRIwcomp = 2;
//            TupleMask complementerMask = new TupleMask(actRIcomp, arcRIwcomp);
//
//            Address<? extends Supplier> andCT = headContainer.getProvisioner().accessJoinNode(jPrimarySlot, jSecondarySlot,
//                    complementerMask);
//
//            final int[] mask = { 0, 2 };
//            final int maskw = 3;
//            Address<? extends Supplier> tr = headContainer.getProvisioner().accessTrimmerNode(andCT, new TupleMask(mask, maskw));
//            network.connectRemoteNodes(tr, containmentTransitiveRoot, true);
//
//            this.containmentTransitiveRoot = containmentTransitiveRoot; // cast
//                                                                        // back
//                                                                        // to
//                                                                        // Supplier
//        }
//        return containmentTransitiveRoot;
//    }
//
//    /**
//     * accesses the special instantiation relation Root node; creates the node if it doesn't exist yet
//     */
//    public Address<? extends Tunnel> accessInstantiationRoot() {
//        if (instantiationRoot == null) {
//            // instantiation: relation quasi-type
//            instantiationRoot = headContainer.getProvisioner().newUniquenessEnforcerNode(2, "$instantiation");
//
//            new InstantiationFeeder(instantiationRoot, context, network, this).feed();
//        }
//        return instantiationRoot;
//    }
//
//    /**
//     * accesses the special transitive instantiation relation Root node; creates the node if it doesn't exist yet
//     * InstantiationTransitive = Instantiation o (Generalization)^*
//     */
//    public Address<? extends Supplier> accessInstantiationTransitiveRoot() {
//        if (instantiationTransitiveRoot == null) {
//            // transitive instantiation: derived
//            Address<? extends Tunnel> instantiationTransitiveRoot = headContainer.getProvisioner()
//                    .newUniquenessEnforcerNode(2, "$instantiationTransitive");
//            network.connectRemoteNodes(accessInstantiationRoot(), instantiationTransitiveRoot, true);
//
//            final int[] actLI = { 1 };
//            final int arcLIw = 2;
//            final int[] actRI = { 0 };
//            final int arcRIw = 2;
//            Address<? extends IterableIndexer> jPrimarySlot = headContainer.getProvisioner().accessProjectionIndexer(
//                    accessGeneralizationRoot(), new TupleMask(actLI, arcLIw));
//            Address<? extends Indexer> jSecondarySlot = headContainer.getProvisioner().accessProjectionIndexer(
//                    instantiationTransitiveRoot, new TupleMask(actRI, arcRIw));
//
//            final int[] actRIcomp = { 1 };
//            final int arcRIwcomp = 2;
//            TupleMask complementerMask = new TupleMask(actRIcomp, arcRIwcomp);
//
//            Address<? extends Supplier> andCT = headContainer.getProvisioner().accessJoinNode(jPrimarySlot, jSecondarySlot,
//                    complementerMask);
//
//            final int[] mask = { 0, 2 };
//            final int maskw = 3;
//            Address<? extends Supplier> tr = headContainer.getProvisioner().accessTrimmerNode(andCT,
//                    new TupleMask(mask, maskw));
//            network.connectRemoteNodes(tr, instantiationTransitiveRoot, true);
//
//            this.instantiationTransitiveRoot = instantiationTransitiveRoot; // cast
//                                                                            // back
//                                                                            // to
//                                                                            // Supplier
//        }
//        return instantiationTransitiveRoot;
//    }
//
//    /**
//     * accesses the special generalization relation Root node; creates the node if it doesn't exist yet
//     */
//    public Address<? extends Tunnel> accessGeneralizationRoot() {
//        if (generalizationRoot == null) {
//            // generalization: relation quasi-type
//            generalizationRoot = headContainer.getProvisioner().newUniquenessEnforcerNode(2, "$generalization");
//
//            new GeneralizationFeeder(generalizationRoot, context, network, this).feed();
//        }
//        return generalizationRoot;
//    }
//
//    /**
//     * accesses the special transitive containment relation Root node; creates the node if it doesn't exist yet
//     */
//    public Address<? extends Supplier> accessGeneralizationTransitiveRoot() {
//        if (generalizationTransitiveRoot == null) {
//            // transitive generalization: derived
//            Address<? extends Tunnel> generalizationTransitiveRoot = headContainer.getProvisioner()
//                    .newUniquenessEnforcerNode(2, "$generalizationTransitive");
//            network.connectRemoteNodes(accessGeneralizationRoot(), generalizationTransitiveRoot, true);
//
//            final int[] actLI = { 1 };
//            final int arcLIw = 2;
//            final int[] actRI = { 0 };
//            final int arcRIw = 2;
//            Address<? extends IterableIndexer> jPrimarySlot = headContainer.getProvisioner().accessProjectionIndexer(
//                    accessGeneralizationRoot(), new TupleMask(actLI, arcLIw));
//            Address<? extends Indexer> jSecondarySlot = headContainer.getProvisioner().accessProjectionIndexer(
//                    generalizationTransitiveRoot, new TupleMask(actRI, arcRIw));
//
//            final int[] actRIcomp = { 1 };
//            final int arcRIwcomp = 2;
//            TupleMask complementerMask = new TupleMask(actRIcomp, arcRIwcomp);
//
//            Address<? extends Supplier> andCT = headContainer.getProvisioner().accessJoinNode(jPrimarySlot, jSecondarySlot,
//                    complementerMask);
//
//            final int[] mask = { 0, 2 };
//            final int maskw = 3;
//            Address<? extends Supplier> tr = headContainer.getProvisioner().accessTrimmerNode(andCT, new TupleMask(mask, maskw));
//            network.connectRemoteNodes(tr, generalizationTransitiveRoot, true);
//
//            this.generalizationTransitiveRoot = generalizationTransitiveRoot; // cast
//                                                                              // back
//                                                                              // to
//                                                                              // Supplier
//        }
//        return generalizationTransitiveRoot;
//    }

    // /**
    // * Registers and publishes a supplier under specified label.
    // */
    // public void publishSupplier(Supplier s, Object label)
    // {
    // publishedSuppliers.put(label, s);
    // }
    //
    // /**
    // * fetches the production node under specified label;
    // * returns null if it doesn't exist yet
    // */
    // public Production getProductionNode(Object label)
    // {
    // return productions.get(label);
    // }
    //
    // /**
    // * fetches the published supplier under specified label;
    // * returns null if it doesn't exist yet
    // */
    // public Supplier getPublishedSupplier(Object label)
    // {
    // return publishedSuppliers.get(label);
    // }

    /**
     * accesses the production node for specified pattern; builds pattern matcher if it doesn't exist yet
     */
    public synchronized RecipeTraceInfo accessProductionTrace(PQuery query)
            throws QueryPlannerException 
    {
    	final CompiledQuery compiled = engine.getCompiler().getCompiledForm(query);
    	return compiled;
//    	RecipeTraceInfo pn;
//        pn = queryPlans.get(query);
//        if (pn == null) {
//            pn = construct(query);
//            TODO handle recursion by reinterpret-RecipeTrace
//            queryPlans.put(query, pn);
//            if (pn == null) {
//                String[] args = { query.toString() };
//                throw new RetePatternBuildException("Unsuccessful planning of RETE construction recipe for query {1}",
//                        args, "Could not create RETE recipe plan.", query);
//            }
//        }
//        return pn;
    }
    /**
     * accesses the production node for specified pattern; builds pattern matcher if it doesn't exist yet
     */
    public synchronized Address<? extends Production> accessProductionNode(PQuery query)
            throws QueryPlannerException {
    	return (Address<? extends Production>) headContainer.getProvisioner().getOrCreateNodeByRecipe(accessProductionTrace(query));
    }

//    /**
//     * creates the production node for the specified pattern Contract: only call from the builder (through Buildable)
//     * responsible for building this pattern
//     *
//     * @throws PatternMatcherCompileTimeException
//     *             if production node is already created
//     */
//    public synchronized Address<? extends Production> createProductionInternal(PQuery gtPattern)
//            throws QueryPlannerException {
//        if (queryPlans.containsKey(gtPattern)) {
//            String[] args = { gtPattern.toString() };
//            throw new RetePatternBuildException("Multiple creation attempts of production node for {1}", args,
//                    "Duplicate RETE production node.", gtPattern);
//        }
//
//        Map<String, Integer> posMapping = engine.getBuilder().getPosMapping(gtPattern);
//        Address<? extends Production> pn = headContainer.getProvisioner().newProductionNode(posMapping, gtPattern);
//        queryPlans.put(gtPattern, pn);
//        context.reportPatternDependency(gtPattern);
//
//        return pn;
//    }

    // /**
    // * accesses the production node for specified pattern and scope map; creates the node if
    // * it doesn't exist yet
    // */
    // public synchronized Address<? extends Production> accessProductionScoped(
    // GTPattern gtPattern, Map<Integer, Scope> additionalScopeMap) throws PatternMatcherCompileTimeException {
    // if (additionalScopeMap.isEmpty()) return accessProduction(gtPattern);
    //
    // Address<? extends Production> pn;
    //
    // Map<Map<Integer, Scope>, Address<? extends Production>> scopes = productionsScoped.get(gtPattern);
    // if (scopes == null) {
    // scopes = new HashMap<Map<Integer, Scope>, Address<? extends Production>>();
    // productionsScoped.put(gtPattern, scopes);
    // }
    //
    // pn = scopes.get(additionalScopeMap);
    // if (pn == null) {
    // Address<? extends Production> unscopedProduction = accessProduction(gtPattern);
    //
    // HashMap<Object, Integer> posMapping = headContainer.resolveLocal(unscopedProduction).getPosMapping();
    // pn = headContainer.getLibrary().newProductionNode(posMapping);
    // scopes.put(additionalScopeMap, pn);
    //
    // constructScoper(unscopedProduction, additionalScopeMap, pn);
    // }
    // return pn;
    // }

    // protected void constructScoper(
    // Address<? extends Production> unscopedProduction,
    // Map<Integer, Scope> additionalScopeMap,
    // Address<? extends Production> production)
    // throws PatternMatcherCompileTimeException {
    // engine.reteNet.waitForReteTermination();
    // engine.builder.constructScoper(unscopedProduction, additionalScopeMap, production);
    // }

    // /**
    // * Invalidates the subnet constructed for the recognition of a given
    // pattern.
    // * The pattern matcher will have to be rebuilt.
    // * @param gtPattern the pattern whose matcher subnet should be invalidated
    // */
    // public void invalidatePattern(GTPattern gtPattern) {
    // Production production = null;
    // try {
    // production = accessProduction(gtPattern);
    // } catch (PatternMatcherCompileTimeException e) {
    // // this should not occur here, since we already have a production node
    // e.printStackTrace();
    // }
    //
    // production.tearOff();
    // //production.setDirty(true);
    // }

    // updaters for change notification
    // if the corresponding rete input isn't created yet, call is ignored
    public void updateUnary(Direction direction, Object entity, Object typeObject) {
        Address<? extends Tunnel> root = inputConnector.getUnaryRoot(typeObject);
        if (root != null) {
            network.sendExternalUpdate(root, direction, new FlatTuple(inputConnector.wrapElement(entity)));
            if (!engine.isParallelExecutionEnabled())
                network.waitForReteTermination();
        }
        if (typeObject != null && generalizationQueryDirection == GeneralizationQueryDirection.SUPERTYPE_ONLY) {
            for (Object superType : context.enumerateDirectUnarySupertypes(typeObject)) {
                updateUnary(direction, entity, superType);
            }
        }
    }

    public void updateTernaryEdge(Direction direction, Object relation, Object from, Object to, Object typeObject) {
        Address<? extends Tunnel> root = inputConnector.getTernaryEdgeRoot(typeObject);
        if (root != null) {
            network.sendExternalUpdate(root, direction, new FlatTuple(inputConnector.wrapElement(relation), inputConnector.wrapElement(from),
                    inputConnector.wrapElement(to)));
            if (!engine.isParallelExecutionEnabled())
                network.waitForReteTermination();
        }
        if (typeObject != null && generalizationQueryDirection == GeneralizationQueryDirection.SUPERTYPE_ONLY) {
            for (Object superType : context.enumerateDirectTernaryEdgeSupertypes(typeObject)) {
                updateTernaryEdge(direction, relation, from, to, superType);
            }
        }
    }

    public void updateBinaryEdge(Direction direction, Object from, Object to, Object typeObject) {
        Address<? extends Tunnel> root = inputConnector.getBinaryEdgeRoot(typeObject);
        if (root != null) {
            network.sendExternalUpdate(root, direction, new FlatTuple(inputConnector.wrapElement(from), inputConnector.wrapElement(to)));
            if (!engine.isParallelExecutionEnabled())
                network.waitForReteTermination();
        }
        if (typeObject != null && generalizationQueryDirection == GeneralizationQueryDirection.SUPERTYPE_ONLY) {
            for (Object superType : context.enumerateDirectBinaryEdgeSupertypes(typeObject)) {
                updateBinaryEdge(direction, from, to, superType);
            }
        }
    }

    public void updateContainment(Direction direction, Object container, Object element) {
        final Address<? extends Tunnel> containmentRoot = inputConnector.getContainmentRoot();
		if (containmentRoot != null) {
            network.sendExternalUpdate(containmentRoot, direction, new FlatTuple(inputConnector.wrapElement(container),
                    inputConnector.wrapElement(element)));
            if (!engine.isParallelExecutionEnabled())
                network.waitForReteTermination();
        }
    }

    public void updateInstantiation(Direction direction, Object parent, Object child) {
        final Address<? extends Tunnel> instantiationRoot = inputConnector.getInstantiationRoot();
       if (instantiationRoot != null) {
            network.sendExternalUpdate(instantiationRoot, direction, new FlatTuple(inputConnector.wrapElement(parent),
                    inputConnector.wrapElement(child)));
            if (!engine.isParallelExecutionEnabled())
                network.waitForReteTermination();
        }
    }

    public void updateGeneralization(Direction direction, Object parent, Object child) {
       final Address<? extends Tunnel> generalizationRoot = inputConnector.getGeneralizationRoot();
       if (generalizationRoot != null) {
            network.sendExternalUpdate(generalizationRoot, direction, new FlatTuple(inputConnector.wrapElement(parent),
                    inputConnector.wrapElement(child)));
            if (!engine.isParallelExecutionEnabled())
                network.waitForReteTermination();
        }
    }

    // no wrapping needed!
    public void notifyEvaluator(Address<? extends Receiver> receiver, Tuple tuple) {
        network.sendExternalUpdate(receiver, Direction.INSERT, tuple);
        if (!engine.isParallelExecutionEnabled())
            network.waitForReteTermination();
    }

    /**
     * @deprecated Use {@link Node#assignTraceInfo(org.eclipse.incquery.runtime.rete.network.Node.TraceInfo)}
     */
    @Deprecated
    public void registerParentPlanForReceiver(Address<? extends Receiver> receiver,
            SubPlan parentPlan) {
        Set<SubPlan> parents = parentPlansOfReceiver.get(receiver);
        if (parents == null) {
            parents = CollectionsFactory.getSet();//new HashSet<SubPlan<Address<? extends Supplier>>>();
            parentPlansOfReceiver.put(receiver, parents);
        }
        parents.add(parentPlan);
    }

    /**
     * @deprecated Use {@link Node#getTraceInfos()}
     */
    @Deprecated
    public Set<SubPlan> getParentPlansOfReceiver(Address<? extends Receiver> receiver) {
        Set<SubPlan> parents = parentPlansOfReceiver.get(receiver);
        if (parents == null)
            parents = Collections.emptySet();
        return parents;
    }

    public void mapPlanToAddress(SubPlan plan, Address<? extends Supplier> handle) {
        subplanToAddressMapping.put(plan, handle);
    }

    public Address<? extends Supplier> getAddress(SubPlan plan) {
        return subplanToAddressMapping.get(plan);
    }
}
