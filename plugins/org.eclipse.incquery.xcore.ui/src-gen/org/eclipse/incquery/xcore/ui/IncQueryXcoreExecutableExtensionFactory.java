/*
 * generated by Xtext
 */
package org.eclipse.incquery.xcore.ui;

import org.eclipse.xtext.ui.guice.AbstractGuiceAwareExecutableExtensionFactory;
import org.osgi.framework.Bundle;

import com.google.inject.Injector;

import org.eclipse.incquery.xcore.ui.internal.IncQueryXcoreActivator;

/**
 * This class was generated. Customizations should only happen in a newly
 * introduced subclass. 
 */
public class IncQueryXcoreExecutableExtensionFactory extends AbstractGuiceAwareExecutableExtensionFactory {

	@Override
	protected Bundle getBundle() {
		return IncQueryXcoreActivator.getInstance().getBundle();
	}
	
	@Override
	protected Injector getInjector() {
		return IncQueryXcoreActivator.getInstance().getInjector(IncQueryXcoreActivator.ORG_ECLIPSE_INCQUERY_XCORE_INCQUERYXCORE);
	}
	
}