/*******************************************************************************
 * Copyright (c) 2010-2012, Abel Hegedus, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Abel Hegedus - initial API and implementation
 *******************************************************************************/
package org.eclipse.incquery.querybasedfeatures.runtime.util.validation;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.incquery.patternlanguage.annotations.IPatternAnnotationAdditionalValidator;
import org.eclipse.incquery.patternlanguage.emf.eMFPatternLanguage.ReferenceType;
import org.eclipse.incquery.patternlanguage.emf.types.IEMFTypeProvider;
import org.eclipse.incquery.patternlanguage.helper.CorePatternLanguageHelper;
import org.eclipse.incquery.patternlanguage.patternLanguage.AggregatedValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.Annotation;
import org.eclipse.incquery.patternlanguage.patternLanguage.BoolValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.CompareConstraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.CompareFeature;
import org.eclipse.incquery.patternlanguage.patternLanguage.Constraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.ParameterRef;
import org.eclipse.incquery.patternlanguage.patternLanguage.PathExpressionConstraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.PathExpressionTail;
import org.eclipse.incquery.patternlanguage.patternLanguage.Pattern;
import org.eclipse.incquery.patternlanguage.patternLanguage.PatternBody;
import org.eclipse.incquery.patternlanguage.patternLanguage.PatternCall;
import org.eclipse.incquery.patternlanguage.patternLanguage.PatternCompositionConstraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.PatternLanguagePackage;
import org.eclipse.incquery.patternlanguage.patternLanguage.StringValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.Type;
import org.eclipse.incquery.patternlanguage.patternLanguage.ValueReference;
import org.eclipse.incquery.patternlanguage.patternLanguage.Variable;
import org.eclipse.incquery.patternlanguage.patternLanguage.VariableValue;
import org.eclipse.incquery.patternlanguage.validation.IIssueCallback;
import org.eclipse.incquery.querybasedfeatures.runtime.QueryBasedFeatureKind;
import org.eclipse.incquery.runtime.base.api.FunctionalDependency;
import org.eclipse.incquery.runtime.base.api.FunctionalDependencyHelper;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

/**
 * @author Abel Hegedus
 * 
 */
public class QueryBasedFeaturePatternValidator implements IPatternAnnotationAdditionalValidator {

    private static final String VALIDATOR_BASE_CODE = "org.eclipse.incquery.querybasedfeatures.";
    public static final String GENERAL_ISSUE_CODE = VALIDATOR_BASE_CODE + "general";
    public static final String METAMODEL_ISSUE_CODE = VALIDATOR_BASE_CODE + "faulty_metamodel";
    public static final String PATTERN_ISSUE_CODE = VALIDATOR_BASE_CODE + "faulty_pattern";
    public static final String ANNOTATION_ISSUE_CODE = VALIDATOR_BASE_CODE + "faulty_annotation";

    @Inject
    private IEMFTypeProvider typeProvider;

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.incquery.patternlanguage.annotations.IPatternAnnotationAdditionalValidator#executeAdditionalValidation
     * (org.eclipse.incquery.patternlanguage.patternLanguage.Annotation,
     * org.eclipse.incquery.patternlanguage.validation.IIssueCallback)
     */
    @Override
    public void executeAdditionalValidation(Annotation annotation, IIssueCallback validator) {
        Pattern pattern = (Pattern) annotation.eContainer();

        // 1. at least two parameters
        if (pattern.getParameters().size() < 2) {
            validator.error("Query-based feature pattern must have at least two parameters.", pattern,
                    PatternLanguagePackage.Literals.PATTERN__PARAMETERS, PATTERN_ISSUE_CODE);
            return;
        }
        // 2. first parameter or "source" (if set) is EClassifier -> Source
        Variable source = null;
        ValueReference ref = CorePatternLanguageHelper.getFirstAnnotationParameter(annotation, "source");
        if (ref == null) {
            source = pattern.getParameters().get(0);
        } else if (ref instanceof VariableValue) {
            source = CorePatternLanguageHelper.getParameterByName(pattern, ((VariableValue) ref).getValue().getVar());
            if (pattern.getParameters().get(0).equals(source)) {
                validator.warning("The 'source' parameter is not needed if it is the first pattern parameter.", ref,
                        PatternLanguagePackage.Literals.VARIABLE_VALUE__VALUE, ANNOTATION_ISSUE_CODE);
            }
        }
        EClassifier sourceClassifier = null;
        if (source != null) {
            sourceClassifier = typeProvider.getClassifierForVariable(source);
        }
        if (sourceClassifier == null || !(sourceClassifier instanceof EClass)) {
            validator.error("The 'source' parameter must be EClass.", source,
                    PatternLanguagePackage.Literals.VARIABLE__TYPE, PATTERN_ISSUE_CODE);
            return;
        }
        EClass sourceClass = (EClass) sourceClassifier;

        // 3. pattern name or "feature" is a feature of Source
        String featureName = null;
        EObject contextForFeature = null;
        EStructuralFeature contextESFForFeature = null;
        ref = CorePatternLanguageHelper.getFirstAnnotationParameter(annotation, "feature");
        if (ref == null) {
            featureName = pattern.getName();
            contextForFeature = pattern;
            contextESFForFeature = PatternLanguagePackage.Literals.PATTERN__NAME;
        } else if (ref instanceof StringValue) {
            featureName = ((StringValue) ref).getValue();
            contextForFeature = ref;
            contextESFForFeature = PatternLanguagePackage.Literals.STRING_VALUE__VALUE;
        }
        if (featureName == null || featureName.isEmpty()) {
            validator.error("The 'feature' parameter must not be empty.", ref,
                    PatternLanguagePackage.Literals.STRING_VALUE__VALUE, ANNOTATION_ISSUE_CODE);
            return;
        }
        EStructuralFeature feature = null;
        for (EStructuralFeature f : sourceClass.getEStructuralFeatures()) {
            if (featureName.equals(f.getName())) {
                feature = f;
                break;
            }
        }
        if (feature == null) {
            validator.error(String.format("Cannot find feature %s of EClass %s.", featureName, sourceClass.getName()),
                    contextForFeature, contextESFForFeature, ANNOTATION_ISSUE_CODE);
            return;
        } else {
            if (feature instanceof EReference) {
                boolean featureError = false;
                if (!feature.isDerived()) {
                    validator.error(String.format("Feature %s is not derived.", featureName), contextForFeature,
                            contextESFForFeature, METAMODEL_ISSUE_CODE);
                    featureError = true;
                }
                if (!feature.isTransient()) {
                    validator.error(String.format("Feature %s is not transient.", featureName), contextForFeature,
                            contextESFForFeature, METAMODEL_ISSUE_CODE);
                    featureError = true;
                }
                if (!feature.isVolatile()) {
                    validator.error(String.format("Feature %s is not volatile.", featureName), contextForFeature,
                            contextESFForFeature, METAMODEL_ISSUE_CODE);
                    featureError = true;
                }
                if (featureError) {
                    return;
                }
                if (feature.isChangeable()) {
                    validator.warning(
                            String.format("Feature %s is changeable, make sure to implement setter.", featureName),
                            contextForFeature, contextESFForFeature, METAMODEL_ISSUE_CODE);
                }
            }
        }
        EClassifier classifier = feature.getEGenericType().getEClassifier();
        if (classifier == null) {
            validator.error(String.format("Feature %s has no type information set in the metamodel", featureName),
                    contextForFeature, contextESFForFeature, METAMODEL_ISSUE_CODE);
            return;
        }
        // 4. second parameter or "target" (if set) is compatible(?) with feature type -> Target
        Variable target = null;
        ref = CorePatternLanguageHelper.getFirstAnnotationParameter(annotation, "target");
        if (ref == null) {
            target = pattern.getParameters().get(1);
        } else if (ref instanceof VariableValue) {
            target = CorePatternLanguageHelper.getParameterByName(pattern, ((VariableValue) ref).getValue().getVar());
            if (pattern.getParameters().get(1).equals(target)) {
                validator.warning("The 'target' parameter is not needed if it is the second pattern parameter.", ref,
                        PatternLanguagePackage.Literals.VARIABLE_VALUE__VALUE, ANNOTATION_ISSUE_CODE);
            }
        }
        EClassifier targetClassifier = typeProvider.getClassifierForVariable(target);
        if (targetClassifier == null) {
            validator.warning("Cannot find target EClassifier", target, PatternLanguagePackage.Literals.VARIABLE__TYPE,
                    PATTERN_ISSUE_CODE);
        }

        // 5. "kind" (if set) is valid enum value
        QueryBasedFeatureKind kind = null;
        ref = CorePatternLanguageHelper.getFirstAnnotationParameter(annotation, "kind");
        if (ref instanceof StringValue) {
            String kindStr = ((StringValue) ref).getValue();
            if (QueryBasedFeatureKind.getStringValue(QueryBasedFeatureKind.SINGLE_REFERENCE).equals(kindStr)) {
                if (feature.getUpperBound() != 1) {
                    validator.error(
                            String.format("Upper bound of feature %s should be 1 for single 'kind'.", featureName),
                            ref, PatternLanguagePackage.Literals.STRING_VALUE__VALUE, METAMODEL_ISSUE_CODE);
                    return;
                }
                kind = QueryBasedFeatureKind.SINGLE_REFERENCE;
            } else if (QueryBasedFeatureKind.getStringValue(QueryBasedFeatureKind.MANY_REFERENCE).equals(kindStr)) {
                if (feature.getUpperBound() != -1 && feature.getUpperBound() < 2) {
                    validator.error(String.format(
                            "Upper bound of feature %s should be -1 or larger than 1 for many 'kind'.", featureName),
                            ref, PatternLanguagePackage.Literals.STRING_VALUE__VALUE, METAMODEL_ISSUE_CODE);
                    return;
                }
                kind = QueryBasedFeatureKind.MANY_REFERENCE;
            } else if (QueryBasedFeatureKind.getStringValue(QueryBasedFeatureKind.COUNTER).equals(kindStr)
                    || QueryBasedFeatureKind.getStringValue(QueryBasedFeatureKind.SUM).equals(kindStr)) {
                if (!classifier.equals(EcorePackage.Literals.EINT)) {
                    validator.error(
                            String.format("Type of feature %s should be EInt for %s 'kind'.", featureName, kindStr),
                            ref, PatternLanguagePackage.Literals.STRING_VALUE__VALUE, METAMODEL_ISSUE_CODE);
                    return;
                }
                kind = QueryBasedFeatureKind.COUNTER;
            } else if (QueryBasedFeatureKind.getStringValue(QueryBasedFeatureKind.ITERATION).equals(kindStr)) {
                validator.warning("Don't forget to subclass QueryBasedFeature for iteration 'kind'.", ref,
                        PatternLanguagePackage.Literals.STRING_VALUE__VALUE, ANNOTATION_ISSUE_CODE);
                kind = QueryBasedFeatureKind.ITERATION;
            }

        }

        if (!classifier.equals(targetClassifier)
                && (kind == QueryBasedFeatureKind.SINGLE_REFERENCE || kind == QueryBasedFeatureKind.MANY_REFERENCE)) {
            validator.warning(String.format("The 'target' parameter type %s is not equal to actual feature type %s.",
                    featureName, sourceClass.getName()), target, PatternLanguagePackage.Literals.VARIABLE__TYPE,
                    PATTERN_ISSUE_CODE);
        }

        // 6. check feature multiplicity based on functional dependencies
        Set<FunctionalDependency<Variable>> dependencies = FunctionalDependencyHelper.project(getDependencies(pattern),
                Sets.newHashSet(source, target));
        FunctionalDependency<Variable> targetDependsOnSource = new FunctionalDependency<Variable>(
                Sets.newHashSet(source), target);

        if (FunctionalDependencyHelper.implies(dependencies, targetDependsOnSource)) {
            if (feature.isMany() && kind == null) {
                String message = String.format(
                        "This feature will have a single value based on the query. To ignore this warning, "
                                + "explicitly set 'kind' to '%s' in the annotation.",
                        QueryBasedFeatureKind.getStringValue(QueryBasedFeatureKind.SINGLE_REFERENCE));
                validator.warning(message, contextForFeature, contextESFForFeature, METAMODEL_ISSUE_CODE);
            }
        } else {
            if (!feature.isMany() && kind == null) {
                String message = String.format(
                        "This feature might have multiple values based on the query. To ignore this warning, "
                                + "explicitly set 'kind' to '%s' in the annotation.",
                        QueryBasedFeatureKind.getStringValue(QueryBasedFeatureKind.MANY_REFERENCE));
                validator.warning(message, contextForFeature, contextESFForFeature, METAMODEL_ISSUE_CODE);
            }
        }

        // 7. keepCache (if set) is correct for the kind
        ref = CorePatternLanguageHelper.getFirstAnnotationParameter(annotation, "keepCache");
        if (ref instanceof BoolValue) {
            boolean keepCache = ((BoolValue) ref).isValue();
            if (keepCache == false) {
                switch (kind) {
                case SINGLE_REFERENCE:
                case MANY_REFERENCE:
                    // OK
                    break;
                default:
                    validator.error("Cacheless behavior only available for single and many kinds.", ref,
                            PatternLanguagePackage.Literals.STRING_VALUE__VALUE, ANNOTATION_ISSUE_CODE);
                    break;
                }
            }
        }
    }

    private final static Set<FunctionalDependency<Variable>> getDependencies(Pattern pattern) {
        if (pattern.getBodies().size() == 1) {
            return normalizeVariablesOf(getDependencies(pattern.getBodies().get(0)));
        } else {
            // Would have to combine the sets of dependencies from each body. I don't know how to do that.
            return Collections.emptySet();
        }
    }

    private final static Set<FunctionalDependency<Variable>> normalizeVariablesOf(
            Set<FunctionalDependency<Variable>> dependencies) {
        return Sets.newHashSet(Collections2.transform(dependencies,
                new Function<FunctionalDependency<Variable>, FunctionalDependency<Variable>>() {
                    @Override
                    public FunctionalDependency<Variable> apply(FunctionalDependency<Variable> arg0) {
                        return normalizeVariablesOf(arg0);
                    }
                }));

    }

    private final static FunctionalDependency<Variable> normalizeVariablesOf(FunctionalDependency<Variable> dependency) {
        return new FunctionalDependency<Variable>(normalizeVariables(dependency.getDeterminant()),
                normalizeVariable(dependency.getDependent()));
    }

    private final static Set<Variable> normalizeVariables(Set<Variable> variableSet) {
        return Sets.newHashSet(Collections2.transform(variableSet, new Function<Variable, Variable>() {
            @Override
            public Variable apply(Variable arg0) {
                return normalizeVariable(arg0);
            }
        }));
    }

    private final static Variable normalizeVariable(Variable variable) {
        if (variable instanceof ParameterRef) {
            return ((ParameterRef) variable).getReferredParam();
        } else {
            return variable;
        }
    }

    private final static Set<FunctionalDependency<Variable>> getDependencies(PatternBody body) {
        Set<FunctionalDependency<Variable>> result = null;

        for (Constraint constraint : body.getConstraints()) {
            Set<FunctionalDependency<Variable>> dependencies = getDependencies(constraint);

            if (result == null) {
                result = Sets.newHashSet(dependencies);
            } else {
                result.addAll(dependencies);
            }
        }

        return result;
    }

    private final static Set<FunctionalDependency<Variable>> getDependencies(Constraint constraint) {
        if (constraint instanceof CompareConstraint) {
            return getDependencies((CompareConstraint) constraint);
        } else if (constraint instanceof PathExpressionConstraint) {
            return getDependencies((PathExpressionConstraint) constraint);
        } else if (constraint instanceof PatternCompositionConstraint) {
            return getDependencies((PatternCompositionConstraint) constraint);
        } else {
            return Collections.emptySet();
        }
    }

    private final static Set<FunctionalDependency<Variable>> getDependencies(CompareConstraint constraint) {
        final Set<FunctionalDependency<Variable>> dependencies = Sets.newHashSetWithExpectedSize(2);

        if (constraint.getFeature() == CompareFeature.EQUALITY) {
            final ValueReference leftOp = constraint.getLeftOperand();
            final ValueReference rightOp = constraint.getRightOperand();
            final Variable leftVar = asVariable(leftOp);
            final Variable rightVar = asVariable(rightOp);
            final Set<Variable> leftVarSet = asVariableSet(leftOp);
            final Set<Variable> rightVarSet = asVariableSet(rightOp);

            if (rightVar != null)
                dependencies.add(new FunctionalDependency<Variable>(leftVarSet, rightVar));
            if (leftVar != null)
                dependencies.add(new FunctionalDependency<Variable>(rightVarSet, leftVar));
        }

        return dependencies;
    }

    private final static Set<FunctionalDependency<Variable>> getDependencies(PathExpressionConstraint constraint) {
        final Variable srcVar = constraint.getHead().getSrc().getVariable();
        final Variable dstVar = asVariable(constraint.getHead().getDst());
        final Set<Variable> srcVarSet = asVariableSet(srcVar);
        final Set<Variable> dstVarSet = asVariableSet(dstVar);
        final PathExpressionTail tail = constraint.getHead().getTail();

        if (tail == null)
            return Collections.emptySet();

        final Set<FunctionalDependency<Variable>> dependencies = Sets.newHashSetWithExpectedSize(2);

        MultiplicityResult pathMultiplicity = getMultiplicity(tail);
        if (dstVar != null && pathMultiplicity.isToOne) {
            dependencies.add(new FunctionalDependency<Variable>(srcVarSet, dstVar));
        }

        if (srcVar != null && pathMultiplicity.isOneTo) {
            dependencies.add(new FunctionalDependency<Variable>(dstVarSet, srcVar));
        }

        return dependencies;
    }

    private final static MultiplicityResult getMultiplicity(PathExpressionTail tail) {
        final EStructuralFeature feature = getFeature(tail);
        if (feature == null)
            return MultiplicityResult.from(true, true);
        else
            return MultiplicityResult.from(isFeatureMultiplicityToOne(feature), isFeatureMultiplicityOneTo(feature))
                    .and(getMultiplicity(tail.getTail()));
    }

    private static final class MultiplicityResult {
        public final boolean isToOne;
        public final boolean isOneTo;

        private static final MultiplicityResult[] values = new MultiplicityResult[] {
                new MultiplicityResult(false, false), new MultiplicityResult(false, true),
                new MultiplicityResult(true, false), new MultiplicityResult(true, true) };

        private MultiplicityResult(final boolean toOne, final boolean oneTo) {
            isToOne = toOne;
            isOneTo = oneTo;
        }

        public static final MultiplicityResult from(final boolean toOne, final boolean oneTo) {
            return values[hashCode(toOne, oneTo)];
        }

        public final MultiplicityResult and(MultiplicityResult other) {
            return from(this.isToOne && other.isToOne, this.isOneTo && other.isOneTo);
        }

        private static final int hashCode(final boolean toOne, final boolean oneTo) {
            return (toOne ? 1 : 0) * 2 + (oneTo ? 1 : 0);
        }

        @Override
        public int hashCode() {
            return hashCode(isToOne, isOneTo);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof MultiplicityResult && equals((MultiplicityResult) obj);
        }

        public boolean equals(MultiplicityResult other) {
            return this == other;  // NOTE: 'other' can either be null or an element of 'values'
            //return (other.isOneTo == this.isOneTo) && (other.isToOne == this.isToOne);
        }
    }

    private final static boolean isFeatureMultiplicityToOne(EStructuralFeature feature) {
        return feature == null ? false : !feature.isMany();
    }

    private final static boolean isFeatureMultiplicityOneTo(EStructuralFeature feature) {
        if (feature instanceof EReference) {
            final EReference reference = (EReference) feature;
            final EReference eOpposite = reference.getEOpposite();
            return reference.isContainment() || (eOpposite != null && !eOpposite.isMany());
        } else
            return false;
    }

    private final static EStructuralFeature getFeature(ReferenceType refType) {
        return refType == null ? null : refType.getRefname();
    }

    private final static EStructuralFeature getFeature(Type type) {
        return getFeature(type instanceof ReferenceType ? (ReferenceType) type : null);
    }

    private final static EStructuralFeature getFeature(PathExpressionTail tail) {
        return getFeature(tail == null ? null : tail.getType());
    }

    private final static Set<FunctionalDependency<Variable>> getDependencies(PatternCompositionConstraint constraint) {
        PatternCall call = constraint.getCall();
        Pattern callee = call.getPatternRef();

        if (constraint.isNegative()) {
            return Collections.emptySet();
        }

        Set<FunctionalDependency<Variable>> calleeDependencies = getParameterDependencies(callee);
        Map<Variable, ValueReference> parameterTranslationTable = getTranslationTable(callee.getParameters(),
                call.getParameters());

        return translate(calleeDependencies, parameterTranslationTable);
    }

    private final static Set<FunctionalDependency<Variable>> getParameterDependencies(Pattern pattern) {
        return FunctionalDependencyHelper.project(getDependencies(pattern), Sets.newHashSet(pattern.getParameters()));
    }

    private final static Variable asVariable(VariableValue varValue) {
        return varValue.getValue().getVariable();
    }

    private final static Variable asVariable(ValueReference valueRef) {
        return valueRef instanceof VariableValue ? asVariable((VariableValue) valueRef) : null;
    }

    private final static Set<Variable> asVariableSet(ValueReference valueRef) {
        if (valueRef instanceof VariableValue) {
            return asVariableSet(asVariable((VariableValue) valueRef));
        } else if (valueRef instanceof AggregatedValue) {
            AggregatedValue aggrValue = (AggregatedValue) valueRef;
            Set<Variable> result = Sets.newHashSet();
            for (ValueReference param : aggrValue.getCall().getParameters()) {
                result.addAll(asVariableSet(param));
            }
            return result;
        } else {
            return Collections.emptySet();
        }
    }

    private final static Set<Variable> asVariableSet(Variable var) {
        if (var == null) {
            return Collections.emptySet();
        } else {
            return Collections.singleton(var);
        }
    }

    private final static Map<Variable, ValueReference> getTranslationTable(EList<Variable> calleeParams,
            EList<ValueReference> callParams) {
        if (calleeParams.size() != callParams.size()) {
            throw new IllegalArgumentException(
                    "The number of the callee's parameters and the number of arguments provided by the call don't match.");
        }

        Map<Variable, ValueReference> table = Maps.newHashMap();

        for (int i = 0; i < calleeParams.size(); ++i) {
            table.put(calleeParams.get(i), callParams.get(i));
        }

        return table;
    }

    private final static Set<Variable> translateVarSet(Set<Variable> variableSet,
            Map<Variable, ValueReference> translationTable) {
        Set<Variable> result = Sets.newHashSet();

        for (Variable var : variableSet) {
            Variable localVar = asVariable(translationTable.get(var));

            if (localVar != null)
                result.add(localVar);
        }

        return result;
    }

    private final static Set<FunctionalDependency<Variable>> translate(
            Set<FunctionalDependency<Variable>> dependencies, Map<Variable, ValueReference> translationTable) {
        Set<FunctionalDependency<Variable>> result = Sets.newHashSet();

        for (FunctionalDependency<Variable> dependency : dependencies) {
            Variable localDependent = asVariable(translationTable.get(dependency.getDependent()));

            if (localDependent != null) {
                Set<Variable> localDeterminant = translateVarSet(dependency.getDeterminant(), translationTable);
                result.add(new FunctionalDependency<Variable>(localDeterminant, localDependent));
            }
        }

        return result;
    }
}
