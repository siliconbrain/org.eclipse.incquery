/*******************************************************************************
 * Copyright (c) 2010-2012, Andras Okros, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Andras Okros - initial API and implementation
 *******************************************************************************/

package org.eclipse.incquery.patternlanguage.emf.types;

import static com.google.common.base.Objects.equal;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.incquery.patternlanguage.emf.eMFPatternLanguage.ClassType;
import org.eclipse.incquery.patternlanguage.emf.eMFPatternLanguage.EClassifierConstraint;
import org.eclipse.incquery.patternlanguage.emf.eMFPatternLanguage.EnumValue;
import org.eclipse.incquery.patternlanguage.emf.eMFPatternLanguage.ReferenceType;
import org.eclipse.incquery.patternlanguage.patternLanguage.AggregatedValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.BoolValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.CompareConstraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.CompareFeature;
import org.eclipse.incquery.patternlanguage.patternLanguage.ComputationValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.Constraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.CountAggregator;
import org.eclipse.incquery.patternlanguage.patternLanguage.DoubleValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.FunctionEvaluationValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.IntValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.ListValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.LiteralValueReference;
import org.eclipse.incquery.patternlanguage.patternLanguage.ParameterRef;
import org.eclipse.incquery.patternlanguage.patternLanguage.PathExpressionConstraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.PathExpressionHead;
import org.eclipse.incquery.patternlanguage.patternLanguage.PathExpressionTail;
import org.eclipse.incquery.patternlanguage.patternLanguage.Pattern;
import org.eclipse.incquery.patternlanguage.patternLanguage.PatternBody;
import org.eclipse.incquery.patternlanguage.patternLanguage.PatternCall;
import org.eclipse.incquery.patternlanguage.patternLanguage.PatternCompositionConstraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.StringValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.Type;
import org.eclipse.incquery.patternlanguage.patternLanguage.ValueReference;
import org.eclipse.incquery.patternlanguage.patternLanguage.Variable;
import org.eclipse.incquery.patternlanguage.patternLanguage.VariableReference;
import org.eclipse.incquery.patternlanguage.patternLanguage.VariableValue;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.common.types.JvmIdentifiableElement;
import org.eclipse.xtext.common.types.JvmTypeReference;
import org.eclipse.xtext.common.types.util.Primitives;
import org.eclipse.xtext.common.types.util.TypeReferences;
import org.eclipse.xtext.resource.CompilerPhases;
import org.eclipse.xtext.xbase.XExpression;
import org.eclipse.xtext.xbase.typing.XbaseTypeProvider;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * An extension of the {@link XbaseTypeProvider} for infering the correct types for the pattern variables. It handles
 * all constraints in the model which can modify the outcome of the type, but it has some practical limitations, as the
 * calculation of the proper type can be time consuming in some cases.
 */
@Singleton
@SuppressWarnings("restriction")
public class EMFPatternTypeProvider extends XbaseTypeProvider implements IEMFTypeProvider {

    @Inject
    private TypeReferences typeReferences;

    @Inject
    private Primitives primitives;
    
    @Inject
    private CompilerPhases compilerPhases;

    private static final int RECURSION_CALLING_LEVEL_LIMIT = 5;

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.xtext.xbase.typing.XbaseTypeProvider#typeForIdentifiable(
     * org.eclipse.xtext.common.types.JvmIdentifiableElement, boolean)
     */
    @Override
    protected JvmTypeReference typeForIdentifiable(JvmIdentifiableElement identifiable, boolean rawType) {
        if (identifiable instanceof Variable) {
            Variable variable = (Variable) identifiable;
            return getVariableType(variable);
        }
        return super.typeForIdentifiable(identifiable, rawType);
    }

    @Override
    public JvmTypeReference getVariableType(Variable variable) {
        EClassifier classifier = getClassifierForVariable(variable);
        JvmTypeReference typeReference = null;
        if (classifier != null) {
            typeReference = getTypeReferenceForVariableWithEClassifier(classifier, variable);
        }
        if (typeReference == null) {
            final Class<?> clazz = (classifier instanceof EClass) ? EObject.class : Object.class;
            typeReference = typeReferences.getTypeForName(clazz, variable);
        }
        return typeReference;
    }

    /**
	 * 
	 */
//	public EMFPatternTypeProvider() {
//		System.out.println("EMFTypeProvider instantiated");
//	}

    
    /**
     * internal class cache, introduced to speed up calls to EClassifier.getInstanceClass
     * significantly.
     */
    private Map<String, Class<?>> classCache = Maps.newHashMap();
    
    /**
     * Returns the {@link JvmTypeReference} for a given {@link EClassifier} and {@link Variable} combination.
     * 
     * @param classifier
     * @param variable
     * @return
     */
    protected JvmTypeReference getTypeReferenceForVariableWithEClassifier(EClassifier classifier, Variable variable) {
        if (classifier == null || "void".equals(classifier.getInstanceClassName())) {
            // hack to speed up things quite a bit
            return null;
        }

        String key = classifier.getInstanceClassName();

        Class<?> c = null;
        if (classCache.containsKey(key)) {
            c = classCache.get(key);
        } else {
            // System.out.println("cc miss for "+classifier.getInstanceClassName());
            // Long start = System.nanoTime();
            Class<?> newC = classifier.getInstanceClass();
            // Long stop = System.nanoTime();
            // System.out.println("getInstClass for '"+key+"' took " + (stop-start)/(1000*1000)
            // +" ms, returning '"+newC+"' as result");
            if (newC != null) {
                classCache.put(key, newC);
                c = newC;
            }
        }

        if (c != null) {
            JvmTypeReference typeReference = typeReferences.getTypeForName(c, variable);
            return primitives.asWrapperTypeIfPrimitive(typeReference);
        }
        return null;
    }

    @Override
    public EClassifier getClassifierForVariable(Variable variable) {
        EcoreUtil2.resolveAll(variable);
        EObject container = variable.eContainer();
        if (container instanceof Pattern) {
            return getClassifierForVariableWithPattern((Pattern) container, variable, 0);
        } else if (container instanceof PatternBody) {
            return getClassifierForVariableWithPatternBody((PatternBody) container, variable, 0, null);
        }
        return null;
    }

    private Set<EClassifier> minimizeClassifiersList(Set<EClassifier> classifierList) {
        final Set<EClassifier> resultList = new HashSet<EClassifier>(classifierList);
        if (resultList.size() > 1) {
            for (EClassifier classifier : classifierList) {
                if ("EObject".equals(classifier.getName())) {
                    resultList.remove(classifier);
                } else if (classifier instanceof EClass) {
                    for (EClass eClass : ((EClass) classifier).getEAllSuperTypes()) {
                        if (resultList.contains(eClass)) {
                            resultList.remove(eClass);
                        }
                    }
                } else if (classifier instanceof EDataType) {
                    final EDataType eDataType = (EDataType) classifier;
                    if (Iterables.any(Iterables.filter(resultList, EDataType.class), new Predicate<EDataType>() {

                        @Override
                        public boolean apply(EDataType dataType) {
                        	if (dataType == null) {
                        		return false;
                        	} else if (dataType.equals(eDataType)){
                        		return false;
                        	} else if (dataType.getInstanceClassName() != null && eDataType.getInstanceClassName() != null) {
                        		return dataType.getInstanceClassName().equals(eDataType.getInstanceClassName()) && resultList.contains(eDataType);                        		
                        	}
                        	return false;
                        }
                    })) {
                       resultList.remove(eDataType); 
                    }
                }
            }
        }

        return resultList;
    }

    private EClassifier getClassifierForVariableWithPattern(Pattern pattern, Variable variable,
            int recursionCallingLevel) {
        Set<EClassifier> intermediateResultList = new HashSet<EClassifier>();
        for (PatternBody body : pattern.getBodies()) {
            EClassifier classifier = getClassifierForVariableWithPatternBody(body, variable, recursionCallingLevel,
                    null);
            if (classifier != null) {
                intermediateResultList.add(classifier);
            }
        }

        if (!intermediateResultList.isEmpty()) {
            if (intermediateResultList.size() == 1) {
                return (EClassifier) intermediateResultList.toArray()[0];
            } else {
                Set<EClassifier> resultSuperTypes = null;
                for (EClassifier classifier : intermediateResultList) {
                    if (classifier instanceof EClass) {
                        if (resultSuperTypes == null) {
                            resultSuperTypes = new LinkedHashSet<EClassifier>();
                            resultSuperTypes.addAll(((EClass) classifier).getEAllSuperTypes());
                            resultSuperTypes.add(classifier);
                        } else {
                            Set<EClassifier> nextSet = new LinkedHashSet<EClassifier>();
                            nextSet.addAll(((EClass) classifier).getEAllSuperTypes());
                            nextSet.add(classifier);
                            resultSuperTypes.retainAll(nextSet);
                        }
                    } else {
                        return null;
                    }
                }
                if (!resultSuperTypes.isEmpty()) {
                    Object[] result = resultSuperTypes.toArray();
                    return (EClassifier) result[result.length - 1];
                }
            }
        }
        return null;
    }

    @Override
    public Set<EClassifier> getPossibleClassifiersForVariableInBody(PatternBody patternBody, Variable variable) {
        Set<EClassifier> possibleClassifiersList = getClassifiersForVariableWithPatternBody(patternBody, variable, 0,
                null);
        if (possibleClassifiersList.size() <= 1) {
            return possibleClassifiersList;
        } else {
            return minimizeClassifiersList(possibleClassifiersList);
        }
    }

    @Override
    public EClassifier getClassifierForPatternParameterVariable(Variable variable) {
        if (variable instanceof ParameterRef) {
            Variable referredParameter = ((ParameterRef) variable).getReferredParam();
            return getClassifierForType(referredParameter.getType());
        } else {
            return getClassifierForType(variable.getType());
        }
    }

    private EClassifier getClassifierForVariableWithPatternBody(PatternBody patternBody, Variable variable,
            int recursionCallingLevel, Variable injectiveVariablePair) {
        Set<EClassifier> possibleClassifiers = getClassifiersForVariableWithPatternBody(patternBody, variable,
                recursionCallingLevel, injectiveVariablePair);
        if (possibleClassifiers.isEmpty()) {
            return null;
        } else if (possibleClassifiers.size() == 1) {
            return (EClassifier) possibleClassifiers.toArray()[0];
        } else {
            Set<EClassifier> minimizedClassifiers = minimizeClassifiersList(possibleClassifiers);
            EClassifier classifier = getClassifierForPatternParameterVariable(variable);
            if (classifier != null && minimizedClassifiers.contains(classifier)) {
                return classifier;
            } else {
                return minimizedClassifiers.iterator().next();
            }
        }
    }

    private Set<EClassifier> getClassifiersForVariableWithPatternBody(PatternBody patternBody, Variable variable,
            int recursionCallingLevel, Variable injectiveVariablePair) {
        Set<EClassifier> possibleClassifiersList = new HashSet<EClassifier>();
        EClassifier classifier = null;

        // Calculate it with just the variable only (works only for parameters)
        classifier = getClassifierForPatternParameterVariable(variable);
        if (classifier != null) {
            possibleClassifiersList.add(classifier);
        }

        // Calculate it from the constraints
        for (Constraint constraint : patternBody.getConstraints()) {
            if (constraint instanceof EClassifierConstraint) {
                EClassifierConstraint eClassifierConstraint = (EClassifierConstraint) constraint;
                if (isEqualVariables(variable, eClassifierConstraint.getVar())) {
                    Type type = eClassifierConstraint.getType();
                    classifier = getClassifierForType(type);
                    if (classifier != null) {
                        possibleClassifiersList.add(classifier);
                    }
                }
            } else if (constraint instanceof PathExpressionConstraint) {
                final PathExpressionHead pathExpressionHead = ((PathExpressionConstraint) constraint).getHead();
                // Src is the first parameter (example: E in EClass.name(E, N))
                final VariableReference firstvariableReference = pathExpressionHead.getSrc();
                if (isEqualVariables(variable, firstvariableReference)) {
                    Type type = pathExpressionHead.getType();
                    classifier = getClassifierForType(type);
                    if (classifier != null) {
                        possibleClassifiersList.add(classifier);
                    }
                }
                final ValueReference valueReference = pathExpressionHead.getDst();
                if (valueReference instanceof VariableValue) {
                    final VariableReference secondVariableReference = ((VariableValue) valueReference).getValue();
                    if (isEqualVariables(variable, secondVariableReference)) {
                        Type type = getTypeFromPathExpressionTail(pathExpressionHead.getTail());
                        classifier = getClassifierForType(type);
                        if (classifier != null) {
                            possibleClassifiersList.add(classifier);
                        }
                    }
                }
            } else if (constraint instanceof CompareConstraint) {
                CompareConstraint compareConstraint = (CompareConstraint) constraint;
                if (CompareFeature.EQUALITY.equals(compareConstraint.getFeature())) {
                    ValueReference leftValueReference = compareConstraint.getLeftOperand();
                    ValueReference rightValueReference = compareConstraint.getRightOperand();
                    if (leftValueReference instanceof VariableValue) {
                        VariableValue leftVariableValue = (VariableValue) leftValueReference;
                        if (isEqualVariables(variable, leftVariableValue.getValue())) {
                            classifier = getClassifierForValueReference(rightValueReference, patternBody, variable,
                                    recursionCallingLevel, injectiveVariablePair);
                            if (classifier != null) {
                                possibleClassifiersList.add(classifier);
                            }
                        }
                    }
                    if (rightValueReference instanceof VariableValue) {
                        VariableValue rightVariableValue = (VariableValue) rightValueReference;
                        if (isEqualVariables(variable, rightVariableValue.getValue())) {
                            classifier = getClassifierForValueReference(leftValueReference, patternBody, variable,
                                    recursionCallingLevel, injectiveVariablePair);
                            if (classifier != null) {
                                possibleClassifiersList.add(classifier);
                            }
                        }
                    }
                }
            } else if (constraint instanceof PatternCompositionConstraint
                    && recursionCallingLevel < RECURSION_CALLING_LEVEL_LIMIT) {
                PatternCompositionConstraint patternCompositionConstraint = (PatternCompositionConstraint) constraint;
                boolean isNegative = patternCompositionConstraint.isNegative();
                if (!isNegative) {
                    PatternCall patternCall = patternCompositionConstraint.getCall();
                    int parameterIndex = 0;
                    for (ValueReference valueReference : patternCall.getParameters()) {
                        if (valueReference instanceof VariableValue) {
                            VariableValue variableValue = (VariableValue) valueReference;
                            VariableReference variableReference = variableValue.getValue();
                            if (isEqualVariables(variable, variableReference)) {
                                Pattern pattern = patternCall.getPatternRef();
                                EList<Variable> parameters = pattern.getParameters();
                                // In case of incorrect number of parameters we might check for non-existing parameters
                                if (parameters.size() > parameterIndex) {
                                    Variable variableInCalledPattern = parameters.get(parameterIndex);
                                    EClassifier variableClassifier = getClassifierForVariableWithPattern(pattern,
                                            variableInCalledPattern, recursionCallingLevel + 1);
                                    if (variableClassifier != null) {
                                        possibleClassifiersList.add(variableClassifier);
                                    }
                                }
                            }
                        }
                        parameterIndex++;
                    }
                }
            }
        }

        return possibleClassifiersList;
    }

    private EClassifier getClassifierForValueReference(ValueReference valueReference, PatternBody patternBody,
            Variable variable, int recursionCallingLevel, Variable injectiveVariablePair) {
        if (valueReference instanceof LiteralValueReference || valueReference instanceof ComputationValue
                || valueReference instanceof EnumValue) {
            return getClassifierForLiteralComputationEnumValueReference(valueReference);
        } else if (valueReference instanceof VariableValue) {
            VariableValue variableValue = (VariableValue) valueReference;
            Variable newPossibleInjectPair = variableValue.getValue().getVariable();
            if (!newPossibleInjectPair.equals(injectiveVariablePair)) {
                return getClassifierForVariableWithPatternBody(patternBody, newPossibleInjectPair,
                        recursionCallingLevel, variable);
            }
        }
        return null;
    }

    @Override
    public EClassifier getClassifierForType(Type type) {
        EClassifier result = null;
        if (type != null) {
            if (type instanceof ClassType) {
                result = ((ClassType) type).getClassname();
            } else if (type instanceof ReferenceType) {
                EStructuralFeature feature = ((ReferenceType) type).getRefname();
                if (feature instanceof EAttribute) {
                    EAttribute attribute = (EAttribute) feature;
                    result = attribute.getEAttributeType();
                } else if (feature instanceof EReference) {
                    EReference reference = (EReference) feature;
                    result = reference.getEReferenceType();
                }
            }
        }
        return result;
    }

    private static boolean isEqualVariables(Variable variable, VariableReference variableReference) {
        if (variable != null && variableReference != null) {
            final Variable variableReferenceVariable = variableReference.getVariable();
            if (equal(variable, variableReferenceVariable)
                    || equal(variable.getName(), variableReferenceVariable.getName())) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public EClassifier getClassifierForLiteralComputationEnumValueReference(ValueReference valueReference) {
        if (valueReference instanceof LiteralValueReference) {
            if (valueReference instanceof IntValue) {
                return EcorePackage.Literals.EINT;
            } else if (valueReference instanceof StringValue) {
                return EcorePackage.Literals.ESTRING;
            } else if (valueReference instanceof BoolValue) {
                return EcorePackage.Literals.EBOOLEAN;
            } else if (valueReference instanceof DoubleValue) {
                return EcorePackage.Literals.EDOUBLE;
            } else if (valueReference instanceof ListValue) {
                return null;
            }
        } else if (valueReference instanceof AggregatedValue) {
            AggregatedValue aggregatedValue = (AggregatedValue) valueReference;
            if (aggregatedValue.getAggregator() instanceof CountAggregator) {
                return EcorePackage.Literals.EINT;
            }
        } else if (valueReference instanceof FunctionEvaluationValue) {
            FunctionEvaluationValue eval = (FunctionEvaluationValue) valueReference;
            final XExpression xExpression = eval.getExpression();
            EDataType dataType;            
            if (!compilerPhases.isIndexing(xExpression)){
            	JvmTypeReference type = getCommonReturnType(xExpression, true);
            	if (type == null) {
            	    //Return type can be null - in that case return Object
            	    //XXX very hacky solution
            	    dataType = EcorePackage.Literals.EJAVA_OBJECT;
            	} else {
            	    dataType = EcoreFactory.eINSTANCE.createEDataType();
            	    dataType.setName(type.getSimpleName());
            	    dataType.setInstanceClassName(type.getQualifiedName());
            	}
            } else {
            	//During the indexing phase it is impossible to calculate the expression type
            	//XXX very hacky solution
            	dataType = EcorePackage.Literals.EJAVA_OBJECT;
            }
            return dataType;
        } else if (valueReference instanceof EnumValue) {
            EnumValue enumValue = (EnumValue) valueReference;
            return enumValue.getEnumeration();
        }
        return null;
    }

    @Override
    public Type getTypeFromPathExpressionTail(PathExpressionTail pathExpressionTail) {
        if (pathExpressionTail == null) {
            return null;
        }
        if (pathExpressionTail.getTail() != null) {
            return getTypeFromPathExpressionTail(pathExpressionTail.getTail());
        }
        return pathExpressionTail.getType();
    }
    
    @Override
    public Map<PathExpressionTail,EStructuralFeature> getAllFeaturesFromPathExpressionTail(PathExpressionTail pathExpressionTail) {
        Map<PathExpressionTail,EStructuralFeature> types = Maps.newHashMap();
        getAllFeaturesFromPathExpressionTail(pathExpressionTail, types);
        return types;
    }
    
    private void getAllFeaturesFromPathExpressionTail(PathExpressionTail pathExpressionTail, Map<PathExpressionTail,EStructuralFeature> types) {
        if (pathExpressionTail != null) {
            Type type = pathExpressionTail.getType();
            if(type instanceof ReferenceType) {
                ReferenceType referenceType = (ReferenceType) type;
                EStructuralFeature refname = referenceType.getRefname();
                if(refname != null) {
                    types.put(pathExpressionTail,refname);
                }
            }
            getAllFeaturesFromPathExpressionTail(pathExpressionTail.getTail(), types);
        }
    }

}
