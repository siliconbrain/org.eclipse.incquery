/*******************************************************************************
 * Copyright (c) 2010-2012, Istvan Rath, Zoltan Ujhelyi and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Istvan Rath, Zoltan Ujhelyi - initial API and implementation
 *******************************************************************************/

package org.eclipse.incquery.tooling.ui.retevis.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.text.FlowPage;
import org.eclipse.draw2d.text.TextFlow;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.gef4.zest.core.viewers.IEntityStyleProvider;
import org.eclipse.incquery.patternlanguage.patternLanguage.Pattern;
import org.eclipse.incquery.runtime.matchers.planning.SubPlan;
import org.eclipse.incquery.runtime.matchers.psystem.PConstraint;
import org.eclipse.incquery.runtime.matchers.psystem.queries.PQuery;
import org.eclipse.incquery.runtime.matchers.psystem.PVariable;
import org.eclipse.incquery.runtime.matchers.tuple.Tuple;
import org.eclipse.incquery.runtime.rete.boundary.ReteBoundary;
import org.eclipse.incquery.runtime.rete.index.Indexer;
import org.eclipse.incquery.runtime.rete.index.IndexerWithMemory;
import org.eclipse.incquery.runtime.rete.index.MemoryIdentityIndexer;
import org.eclipse.incquery.runtime.rete.index.MemoryNullIndexer;
import org.eclipse.incquery.runtime.rete.matcher.RetePatternMatcher;
import org.eclipse.incquery.runtime.rete.misc.ConstantNode;
import org.eclipse.incquery.runtime.rete.network.Node;
import org.eclipse.incquery.runtime.rete.single.UniquenessEnforcerNode;
import org.eclipse.incquery.runtime.rete.traceability.CompiledSubPlan;
import org.eclipse.incquery.runtime.rete.traceability.PlanningTrace;
import org.eclipse.incquery.runtime.rete.traceability.TraceInfo;
import org.eclipse.incquery.runtime.rete.tuple.MaskedTupleMemory;
import org.eclipse.incquery.tooling.ui.retevis.theme.ColorTheme;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Color;

public class ZestReteLabelProvider extends LabelProvider implements IEntityStyleProvider {

    private static final int INDEXER_ID = 0;
    private static final int RETEMATCHER_ID = 1;
    private static final int INPUT_ID = 2;

    private ReteBoundary rb;
    private ColorTheme theme;

    /**
     * Sets the colors of the indexer and rete matcher nodes
     *
     * @param indexerColor
     * @param reteMatcherColor
     */
    public void setColors(ColorTheme theme) {
        this.theme = theme;

    }

    public ReteBoundary getRb() {
        return rb;
    }

    public void setRb(ReteBoundary rb) {
        this.rb = rb;
        // initialize reverse traceability information
//        resetReverseMap();
//        for (Object _o : rb.getAllProductionNodes()) {
//            Node productionNode = rb.getHeadContainer().resolveLocal((Address<?>) _o);
//            if (productionNode instanceof Production) {
//                initalizeReverseMap((Production) productionNode);
//            }
//        }
    }

    @Override
    public String getText(Object element) {
        if (element instanceof Node) {
            Node n = (Node) element;
            Class<?> namedClass = n.getClass();
            String simpleName;
            do {
                simpleName = namedClass.getSimpleName();
                namedClass = namedClass.getSuperclass();
            } while (simpleName == null || simpleName.isEmpty());
            StringBuilder sb = new StringBuilder(simpleName);
            if (n instanceof UniquenessEnforcerNode) {
                // print tuplememory statistics
                UniquenessEnforcerNode un = (UniquenessEnforcerNode) n;

                if (un.getParents().isEmpty() && un.getTag() instanceof ENamedElement) {
                    sb.append(" : " + ((ENamedElement) un.getTag()).getName() + " : ");

                }
                sb.append(" [" + (un).getMemory().size() + "]");

            }
            if (n instanceof IndexerWithMemory) {
                MaskedTupleMemory mem = ((IndexerWithMemory) n).getMemory();
                sb.append(" [" + mem.getKeysetSize() + " => " + mem.getTotalSize() + "]");
            }
            if (n instanceof MemoryIdentityIndexer) {
                sb.append(" [" + ((MemoryIdentityIndexer)n).getSignatures().size() + "]");
            }
            if (n instanceof MemoryNullIndexer) {
                sb.append(" [" + ((MemoryNullIndexer)n).getSignatures().size() + "]");
            }
            if (!(n instanceof UniquenessEnforcerNode || n instanceof ConstantNode)) {
                sb.append("\n");
                for (PlanningTrace trace : getStubsForNode(n)) {
                    sb.append("<");
                    List<PVariable> variablesTuple = trace.getVariablesTuple();
                    for (PVariable var : variablesTuple) {
                        sb.append(var.getName());
                        sb.append("; ");
                    }
                    sb.append(">  ");
                }
            }
            if (n instanceof RetePatternMatcher) {
                sb.append ( " '" + ((PQuery) ((RetePatternMatcher)n).getTag()).getFullyQualifiedName() +"'");
           }
            return sb.toString();
        }
        return "!";
        // return s+super.getText(element);
    }

    @Override
    public IFigure getTooltip(Object entity) {
        if (entity instanceof Node) {
            Node n = (Node) entity;
//            String s = "";
            StringBuilder infoBuilder = new StringBuilder("Stubs:\n");
            for (PlanningTrace trace : getStubsForNode(n)) {
                if (trace instanceof CompiledSubPlan)
                	infoBuilder.append(getEnforcedConstraints(trace.getSubPlan()));
            }

            FlowPage fp = new FlowPage();

            TextFlow nameTf = new TextFlow();
            // nameTf.setFont(fontRegistry.get("default"));
            TextFlow infoTf = new TextFlow();
            // infoTf.setFont(fontRegistry.get("code"));

            nameTf.setText(n.toString());
            infoTf.setText(infoBuilder.toString());
            if (entity instanceof RetePatternMatcher) {
                if (((Node) entity).getTag() instanceof Pattern) {
                    Pattern pattern = (Pattern) ((Node) entity).getTag();
                    nameTf.setText(pattern.getName());
                    fp.add(nameTf);
                }
            } else if (entity instanceof ConstantNode) {
                ConstantNode node = (ConstantNode) entity;
                List<Tuple> arrayList = new ArrayList<Tuple>();
                node.pullInto(arrayList);
                StringBuilder sb = new StringBuilder();
                for (Tuple tuple : arrayList) {
                    sb.append(tuple.toString() + "\n");
                }
                nameTf.setText(sb.toString());
                fp.add(nameTf);
            }
            fp.add(infoTf);
            return fp;
        }
        return null;
    }

    // useful only for production nodes
    private static String getEnforcedConstraints(SubPlan st) {
        StringBuilder sb = new StringBuilder();
        for (Object _pc : st.getAllEnforcedConstraints()) {
            PConstraint pc = (PConstraint) _pc;
            sb.append("\t[" + pc.getClass().getSimpleName() + "]:");
            for (PVariable v : pc.getAffectedVariables()) {
                sb.append("{" + v.getName() + "}");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private Collection<PlanningTrace> getStubsForNode(Node n) {
    	Collection<PlanningTrace> result = new HashSet<PlanningTrace>();
        if (n!=null) {
        	Set<TraceInfo> traceInfos = n.getTraceInfos();
        	for (TraceInfo traceInfo : traceInfos) {
				if (traceInfo instanceof PlanningTrace)
					result.add((PlanningTrace) traceInfo);
			}
        }
        return result;
    }

//    private Map<Node, Collection<SubPlan>> reverseMap;// = new HashMap<Node, Collection<Stub<Address<?>>>>();
//
//    private void resetReverseMap() {
//        reverseMap = new HashMap<Node, Collection<SubPlan>>();
//    }
//
//    private void initalizeReverseMap(Production prod) {
////        for (Object _stubOfProd : rb.getParentPlansOfReceiver(new Address<Receiver>(prod))) {
////            SubPlan stubOfProd = (SubPlan) _stubOfProd;
////            for (SubPlan s : getAllParentStubs(stubOfProd)) {
////                Address<? extends Node> address = rb.getAddress(s);
////                Node n = rb.getHeadContainer().resolveLocal(address);
////                Collection<SubPlan> t = reverseMap.get(n);
////                if (t == null) {
////                    t = new HashSet<SubPlan>();
////                }
////                t.add(s);
////                reverseMap.put(n, t);
////            }
////        }
//    }

//    private static Collection<SubPlan> getAllParentStubs(SubPlan st) {
//        if (st != null) {
//            List<SubPlan> v = new ArrayList<SubPlan>();
//            v.add(st);
//            v.addAll(getAllParentStubs(st.getPrimaryParentPlan()));
//            v.addAll(getAllParentStubs(st.getSecondaryParentPlan()));
//            return v;
//        } else
//            return Collections.emptyList();
//    }

    @Override
    public Color getNodeHighlightColor(Object entity) {
        return null;
    }

    @Override
    public Color getBorderColor(Object entity) {
        return null;
    }

    @Override
    public Color getBorderHighlightColor(Object entity) {
        return null;
    }

    @Override
    public int getBorderWidth(Object entity) {
        return 0;
    }

    @Override
    public Color getBackgroundColour(Object entity) {
        if (entity instanceof Indexer) {
            return theme.getNodeColor(INDEXER_ID);
        } else if (entity instanceof RetePatternMatcher) {
            return theme.getNodeColor(RETEMATCHER_ID);
        } else if (entity instanceof UniquenessEnforcerNode) {
            UniquenessEnforcerNode inputNode = (UniquenessEnforcerNode) entity;
            if (inputNode.getParents().isEmpty()) {
                return theme.getNodeColor(INPUT_ID);
            }
        }
        return null;
    }

    @Override
    public Color getForegroundColour(Object entity) {
        if (entity instanceof Indexer) {
            return theme.getTextColor(INDEXER_ID);
        } else if (entity instanceof RetePatternMatcher) {
            return theme.getTextColor(RETEMATCHER_ID);
        } else if (entity instanceof UniquenessEnforcerNode) {
            UniquenessEnforcerNode inputNode = (UniquenessEnforcerNode) entity;
            if (inputNode.getParents().isEmpty()) {
                return theme.getTextColor(INPUT_ID);
            }
        }
        return null;
    }

    @Override
    public boolean fisheyeNode(Object entity) {
        return false;
    }

}
