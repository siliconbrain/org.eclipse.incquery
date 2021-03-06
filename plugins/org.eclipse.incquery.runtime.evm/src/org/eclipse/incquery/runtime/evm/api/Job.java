/*******************************************************************************
 * Copyright (c) 2010-2013, Abel Hegedus, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Abel Hegedus - initial API and implementation
 *******************************************************************************/
package org.eclipse.incquery.runtime.evm.api;

import static com.google.common.base.Preconditions.checkNotNull;

import org.eclipse.incquery.runtime.evm.api.event.ActivationState;

/**
 * A job represents an action that can be executed on an activation
 * if it is in the state defined by the job.
 * 
 * @author Abel Hegedus
 *
 */
public abstract class Job<EventAtom> {

    private ActivationState activationState;
    
    /**
     * @return the activationState
     */
    public ActivationState getActivationState() {
        return activationState;
    }
    
    /**
     * Creates a new job corresponding to the given state.
     */
    protected Job(final ActivationState activationState) {
        this.activationState = checkNotNull(activationState, "Cannot create job with null activation state!");
    }
    
    /**
     * Executes the action on the activation using the supplied context.
     * 
     * @param activation
     * @param context
     */
    protected abstract void execute(final Activation<? extends EventAtom> activation, final Context context);
    
    /**
     * Called if the {@link #execute} method has thrown an exception to allow jobs to handle their own errors.
     *  
     * @param activation
     * @param exception
     * @param context
     */
    protected abstract void handleError(final Activation<? extends EventAtom> activation, final Exception exception, final Context context);
    
}
