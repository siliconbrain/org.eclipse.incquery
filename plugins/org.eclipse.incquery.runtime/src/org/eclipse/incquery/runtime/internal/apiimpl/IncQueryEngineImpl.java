/*******************************************************************************
 * Copyright (c) 2004-2010 Gabor Bergmann and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Gabor Bergmann - initial API and implementation
 *******************************************************************************/

package org.eclipse.incquery.runtime.internal.apiimpl;

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.incquery.runtime.api.AdvancedIncQueryEngine;
import org.eclipse.incquery.runtime.api.IMatchUpdateListener;
import org.eclipse.incquery.runtime.api.IPatternMatch;
import org.eclipse.incquery.runtime.api.IQuerySpecification;
import org.eclipse.incquery.runtime.api.IncQueryEngine;
import org.eclipse.incquery.runtime.api.IncQueryEngineLifecycleListener;
import org.eclipse.incquery.runtime.api.IncQueryEngineManager;
import org.eclipse.incquery.runtime.api.IncQueryMatcher;
import org.eclipse.incquery.runtime.api.IncQueryModelUpdateListener;
import org.eclipse.incquery.runtime.api.impl.BaseMatcher;
import org.eclipse.incquery.runtime.base.api.BaseIndexOptions;
import org.eclipse.incquery.runtime.base.api.IIndexingErrorListener;
import org.eclipse.incquery.runtime.base.api.IncQueryBaseFactory;
import org.eclipse.incquery.runtime.base.api.NavigationHelper;
import org.eclipse.incquery.runtime.base.exception.IncQueryBaseException;
import org.eclipse.incquery.runtime.context.EMFPatternMatcherRuntimeContext;
import org.eclipse.incquery.runtime.exception.IncQueryException;
import org.eclipse.incquery.runtime.extensibility.QuerySpecificationRegistry;
import org.eclipse.incquery.runtime.internal.boundary.CallbackNode;
import org.eclipse.incquery.runtime.internal.engine.LifecycleProvider;
import org.eclipse.incquery.runtime.internal.engine.ModelUpdateProvider;
import org.eclipse.incquery.runtime.matchers.planning.QueryPlannerException;
import org.eclipse.incquery.runtime.matchers.tuple.Tuple;
import org.eclipse.incquery.runtime.rete.construction.plancompiler.ReteRecipeCompiler;
import org.eclipse.incquery.runtime.rete.matcher.IPatternMatcherRuntimeContext;
import org.eclipse.incquery.runtime.rete.matcher.ReteEngine;
import org.eclipse.incquery.runtime.rete.matcher.RetePatternMatcher;
import org.eclipse.incquery.runtime.rete.util.Options;
import org.eclipse.incquery.runtime.util.IncQueryLoggingUtil;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

/**
 * An EMF-IncQuery engine back-end (implementation)
 * 
 * @author Bergmann Gábor
 * 
 */
public class IncQueryEngineImpl extends AdvancedIncQueryEngine {
	
    /**
     * The engine manager responsible for this engine. Null if this engine is unmanaged.
     */
    private final IncQueryEngineManager manager;
    /**
     * The model to which the engine is attached.
     */
    private final Notifier emfRoot;
    private final Map<IQuerySpecification<? extends IncQueryMatcher<?>>, IncQueryMatcher<?>> matchers;

    /**
     * The base index keeping track of basic EMF contents of the model.
     */
    private NavigationHelper baseIndex;
    /**
     * Whether to initialize the base index in wildcard mode.
     * Whether to initialize the base index in dynamic EMF mode.
     */
	private final BaseIndexOptions options;
	/**
     * The RETE pattern matcher component of the EMF-IncQuery engine.
     */
    private ReteEngine reteEngine = null;

    private final LifecycleProvider lifecycleProvider;
    private final ModelUpdateProvider modelUpdateProvider;
    private Logger logger;
    
    /**
     * EXPERIMENTAL
     */
    private final int reteThreads = 0;
    
    /**
     * @param manager
     *            null if unmanaged
     * @param emfRoot
     * @throws IncQueryException
     *             if the emf root is invalid
     */
    public IncQueryEngineImpl(IncQueryEngineManager manager, Notifier emfRoot, BaseIndexOptions options) throws IncQueryException {
        super();
        this.manager = manager;
        this.emfRoot = emfRoot;
        this.options = options.copy();
        this.matchers = Maps.newHashMap();
        this.lifecycleProvider = new LifecycleProvider(this, getLogger());
        this.modelUpdateProvider = new ModelUpdateProvider(this, getLogger());
        if (!(emfRoot instanceof EObject || emfRoot instanceof Resource || emfRoot instanceof ResourceSet))
            throw new IncQueryException(IncQueryException.INVALID_EMFROOT
                    + (emfRoot == null ? "(null)" : emfRoot.getClass().getName()),
                    IncQueryException.INVALID_EMFROOT_SHORT);
    }

    @Override
	public Notifier getScope() {
        return emfRoot;
    }
    
    @Override
	public Set<? extends IncQueryMatcher<? extends IPatternMatch>> getCurrentMatchers(){
        return ImmutableSet.copyOf(matchers.values());
    }
    
    @Override
	public <Matcher extends IncQueryMatcher<? extends IPatternMatch>> Matcher getMatcher(IQuerySpecification<Matcher> querySpecification) throws IncQueryException {
        return querySpecification.getMatcher(this);
    }

	@Override
	@SuppressWarnings("unchecked")
	public <Matcher extends IncQueryMatcher<? extends IPatternMatch>> Matcher getExistingMatcher(IQuerySpecification<Matcher> querySpecification) {
		return (Matcher) matchers.get(querySpecification);
	}
    
    @Override
    public IncQueryMatcher<? extends IPatternMatch> getMatcher(String patternFQN) throws IncQueryException {
        IQuerySpecification<? extends IncQueryMatcher<? extends IPatternMatch>> querySpecification = QuerySpecificationRegistry
                .getQuerySpecification(patternFQN);
        if (querySpecification != null) {
            return getMatcher(querySpecification);
        } else {
            throw new IncQueryException(
                    String.format(
                            "No matcher could be constructed for the pattern with FQN %s; if the generated matcher class is not available, please access for the first time using getMatcher(IQuerySpecification)",
                            patternFQN), "No matcher could be constructed for given pattern FQN.");
        }
    }

    /**
     * Internal accessor for the base index.
     * 
     * @return the baseIndex the NavigationHelper maintaining the base index
     * @throws IncQueryException
     *             if the base index could not be constructed
     */
    protected NavigationHelper getBaseIndexInternal() throws IncQueryException {
        return getBaseIndexInternal(true);
    }

    /**
     * Internal accessor for the base index.
     * 
     * @return the baseIndex the NavigationHelper maintaining the base index
     * @throws IncQueryException
     *             if the base index could not be initialized
     * @throws IncQueryBaseException
     *             if the base index could not be constructed
     */
    protected NavigationHelper getBaseIndexInternal(boolean initNow) throws IncQueryException {
        if (baseIndex == null) {
            try {
                // sync to avoid crazy compiler reordering which would matter if derived features use eIQ and call this
                // reentrantly
                synchronized (this) {
                    baseIndex = IncQueryBaseFactory.getInstance().createNavigationHelper(null, options,
                            getLogger());
                    baseIndex.addIndexingErrorListener(taintListener);
                }
            } catch (IncQueryBaseException e) {
                throw new IncQueryException("Could not create EMF-IncQuery base index", "Could not create base index",
                        e);
            }

            if (initNow) {
                initBaseIndex();
            }

        }
        return baseIndex;
    }

    /**
     * @throws IncQueryException
     */
    private synchronized void initBaseIndex() throws IncQueryException {
        try {
            baseIndex.addRoot(getScope());
        } catch (IncQueryBaseException e) {
            throw new IncQueryException("Could not initialize EMF-IncQuery base index",
                    "Could not initialize base index", e);
        }
    }

    @Override
	public NavigationHelper getBaseIndex() throws IncQueryException {
        return getBaseIndexInternal();
    }

	public final Logger getLogger() {
        if (logger == null) {
            final int hash = System.identityHashCode(this);
            logger = Logger.getLogger(IncQueryLoggingUtil.getLogger(IncQueryEngine.class).getName() + "." + hash);
            if (logger == null)
                throw new AssertionError(
                        "Configuration error: unable to create EMF-IncQuery runtime logger for engine " + hash);
        }
        return logger;
    }
    
    ///////////////// internal stuff //////////////

    /**
     * Report when a pattern matcher has been completely initialized, so that it can be registered into the engine.
     * @param querySpecification the {@link IQuerySpecification} that corresponds to the matcher
     * @param matcher the {@link IncQueryMatcher} that has finished its initialization process
     * 
     * TODO make it package-only visible when implementation class is moved to impl package
     */
    public void reportMatcherInitialized(IQuerySpecification<?> querySpecification, IncQueryMatcher<?> matcher) {
        if(matchers.containsKey(querySpecification)) {
            // TODO simply dropping the matcher can cause problems
            logger.debug("Query " + 
                    querySpecification.getFullyQualifiedName() + 
                    " already initialized in IncQueryEngine!");
        } else {
            matchers.put(querySpecification, matcher);
            lifecycleProvider.matcherInstantiated(matcher);
        }
    }

    /**
     * Provides access to the internal RETE pattern matcher component of the EMF-IncQuery engine.
     * 
     * @noreference A typical user would not need to call this method.
     * TODO make it package visible only
     */
    @Override
	public ReteEngine getReteEngine() throws IncQueryException {
        if (reteEngine == null) {
            // if uninitialized, don't initialize yet
            getBaseIndexInternal(false);

            EMFPatternMatcherRuntimeContext context = new EMFPatternMatcherRuntimeContext(this, baseIndex);
            // if (emfRoot instanceof EObject)
            // context = new EMFPatternMatcherRuntimeContext.ForEObject<Pattern>((EObject)emfRoot, this);
            // else if (emfRoot instanceof Resource)
            // context = new EMFPatternMatcherRuntimeContext.ForResource<Pattern>((Resource)emfRoot, this);
            // else if (emfRoot instanceof ResourceSet)
            // context = new EMFPatternMatcherRuntimeContext.ForResourceSet<Pattern>((ResourceSet)emfRoot, this);
            // else throw new IncQueryRuntimeException(IncQueryRuntimeException.INVALID_EMFROOT);

            synchronized (this) {
                reteEngine = buildReteEngineInternal(context);
            }

            // lazy initialization now,
            initBaseIndex();

            // if (reteEngine != null) engines.put(emfRoot, new WeakReference<ReteEngine<String>>(engine));
        }
        return reteEngine;

    }

    

    private ReteEngine buildReteEngineInternal(IPatternMatcherRuntimeContext context) {
        ReteEngine engine;
        engine = new ReteEngine(context, reteThreads);
        ReteRecipeCompiler compiler = new ReteRecipeCompiler(Options.builderMethod.layoutStrategy(), context);
        //EPMBuilder builder = new EPMBuilder(buildable, context);
        engine.setCompiler(compiler);
        return engine;
    }
    
    ///////////////// advanced stuff /////////////
    
    @Override
    public void dispose() {
        if (manager != null) {
        	throw new UnsupportedOperationException(
        			String.format("Cannot dispose() managed EMF-IncQuery engine. Attempted for notifier %s.", emfRoot));
        }
        wipe();
        
        // called before base index disposal to allow removal of base listeners
        lifecycleProvider.engineDisposed();
        
        try{
	        if (baseIndex != null) {
	            baseIndex.dispose();
	        }
        } catch (IllegalStateException ex) {
        	getLogger().warn(
        			"The base index could not be disposed along with the EMF-InQuery engine, as there are still active listeners on it.");
        }
    }

    @Override
    public void wipe() {
        if (manager != null) {
        	throw new UnsupportedOperationException(
        			String.format("Cannot wipe() managed EMF-IncQuery engine. Attempted for notifier %s.", emfRoot));
        }
        if (reteEngine != null) {
            reteEngine.killEngine();
            reteEngine = null;
        }
        matchers.clear();
        lifecycleProvider.engineWiped();
    }

    
    
    /**
     * Indicates whether the engine is in a tainted, inconsistent state.
     */
    private boolean tainted = false;
    private IIndexingErrorListener taintListener = new SelfTaintListener(this);

    private static class SelfTaintListener implements IIndexingErrorListener {
        WeakReference<IncQueryEngineImpl> iqEngRef;

        public SelfTaintListener(IncQueryEngineImpl iqEngine) {
            this.iqEngRef = new WeakReference<IncQueryEngineImpl>(iqEngine);
        }

        public void engineBecameTainted(String description, Throwable t) {
            final IncQueryEngineImpl iqEngine = iqEngRef.get();
            if (iqEngine != null) {
                iqEngine.tainted = true;
                iqEngine.lifecycleProvider.engineBecameTainted(description, t);
            }
        }
        
        private boolean noTaintDetectedYet = true;

        protected void notifyTainted(String description, Throwable t) {
            if (noTaintDetectedYet) {
                noTaintDetectedYet = false;
                engineBecameTainted(description, t);
            }
        }

        @Override
        public void error(String description, Throwable t) {
            //Errors does not mean tainting        
        }

        @Override
        public void fatal(String description, Throwable t) {
            notifyTainted(description, t);
        }
    }
    
    @Override
	public boolean isTainted() {
        return tainted;
    }

    @Override
	public boolean isManaged() {
        return manager != null;
        // return isAdvanced; ???
    }


    @Override
	public <Match extends IPatternMatch> void addMatchUpdateListener(IncQueryMatcher<Match> matcher,
            IMatchUpdateListener<? super Match> listener, boolean fireNow) {
        checkArgument(listener != null, "Cannot add null listener!");
        checkArgument(matcher.getEngine() == this, "Cannot register listener for matcher of different engine!");
        checkArgument(reteEngine != null, "Cannot register listener on matcher of disposed engine!");
        //((BaseMatcher<Match>)matcher).addCallbackOnMatchUpdate(listener, fireNow);
        final BaseMatcher<Match> bm = (BaseMatcher<Match>)matcher;
        
        final CallbackNode<Match> callbackNode = new CallbackNode<Match>(reteEngine.getReteNet().getHeadContainer(),
                this, logger, listener) {
            @Override
            public Match statelessConvert(Tuple t) {
                //return bm.tupleToMatch(t);
                return bm.newMatch(t.getElements());
            }
        };
        try {
            RetePatternMatcher patternMatcher = reteEngine.accessMatcher(matcher.getSpecification());
            patternMatcher.connect(callbackNode, listener, fireNow);
        } catch (QueryPlannerException e) {
            logger.error("Could not access matcher " + matcher.getPatternName(), e);
        }
    }
    
    @Override
	public <Match extends IPatternMatch> void removeMatchUpdateListener(IncQueryMatcher<Match> matcher,
            IMatchUpdateListener<? super Match> listener) {
        checkArgument(listener != null, "Cannot remove null listener!");
        checkArgument(matcher.getEngine() == this, "Cannot remove listener from matcher of different engine!");
        checkArgument(reteEngine != null, "Cannot remove listener from matcher of disposed engine!");
        //((BaseMatcher<Match>)matcher).removeCallbackOnMatchUpdate(listener);
        try {
            RetePatternMatcher patternMatcher = reteEngine.accessMatcher(matcher.getSpecification());
            patternMatcher.disconnectByTag(listener);
        } catch (Exception e) {
            logger.error("Could not access matcher " + matcher.getPatternName(), e);
        }
    }
    
    @Override
	public void addModelUpdateListener(IncQueryModelUpdateListener listener) {
        modelUpdateProvider.addListener(listener);
    }
    
    @Override
	public void removeModelUpdateListener(IncQueryModelUpdateListener listener) {
        modelUpdateProvider.removeListener(listener);
    }
    
    @Override
	public void addLifecycleListener(IncQueryEngineLifecycleListener listener) {
        lifecycleProvider.addListener(listener);
    }
    
    @Override
	public void removeLifecycleListener(IncQueryEngineLifecycleListener listener) {
        lifecycleProvider.removeListener(listener);
    }

    // /**
    // * EXPERIMENTAL: Creates an EMF-IncQuery engine that executes post-commit, or retrieves an already existing one.
    // * @param emfRoot the EMF root where this engine should operate
    // * @param reteThreads experimental feature; 0 is recommended
    // * @return a new or previously existing engine
    // * @throws IncQueryRuntimeException
    // */
    // public ReteEngine<String> getReteEngine(final TransactionalEditingDomain editingDomain, int reteThreads) throws
    // IncQueryRuntimeException {
    // final ResourceSet resourceSet = editingDomain.getResourceSet();
    // WeakReference<ReteEngine<String>> weakReference = engines.get(resourceSet);
    // ReteEngine<String> engine = weakReference != null ? weakReference.get() : null;
    // if (engine == null) {
    // IPatternMatcherRuntimeContext<String> context = new
    // EMFPatternMatcherRuntimeContext.ForTransactionalEditingDomain<String>(editingDomain);
    // engine = buildReteEngine(context, reteThreads);
    // if (engine != null) engines.put(resourceSet, new WeakReference<ReteEngine<String>>(engine));
    // }
    // return engine;
    // }

    
}
