package org.apache.rya.rdftriplestore.inference;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.rya.accumulo.AccumuloRdfConfiguration;
import org.apache.rya.api.utils.NullableStatementImpl;
import org.apache.rya.rdftriplestore.utils.FixedStatementPattern;
import org.junit.Assert;
import org.junit.Test;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.model.vocabulary.OWL;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.query.algebra.Join;
import org.openrdf.query.algebra.Projection;
import org.openrdf.query.algebra.ProjectionElem;
import org.openrdf.query.algebra.ProjectionElemList;
import org.openrdf.query.algebra.StatementPattern;
import org.openrdf.query.algebra.TupleExpr;
import org.openrdf.query.algebra.Union;
import org.openrdf.query.algebra.Var;

public class AllValuesFromVisitorTest {
    private final AccumuloRdfConfiguration conf = new AccumuloRdfConfiguration();
    private final ValueFactory vf = new ValueFactoryImpl();

    // Value types
    private final URI person = vf.createURI("urn:Person");
    private final URI dog = vf.createURI("urn:Dog");
    // Predicates
    private final URI parent = vf.createURI("urn:parent");
    private final URI relative = vf.createURI("urn:relative");
    // Restriction types
    private final URI parentsAreTallPeople = vf.createURI("urn:parentsAreTallPeople");
    private final URI parentsArePeople = vf.createURI("urn:parentsArePeople");
    private final URI relativesArePeople = vf.createURI("urn:relativesArePeople");
    private final URI parentsAreDogs = vf.createURI("urn:parentsAreDogs");

    @Test
    public void testRewriteTypePattern() throws Exception {
        // Configure a mock instance engine with an ontology:
        final InferenceEngine inferenceEngine = mock(InferenceEngine.class);
        Map<Resource, Set<URI>> personAVF = new HashMap<>();
        personAVF.put(parentsAreTallPeople, new HashSet<>());
        personAVF.put(parentsArePeople, new HashSet<>());
        personAVF.put(relativesArePeople, new HashSet<>());
        personAVF.get(parentsAreTallPeople).add(parent);
        personAVF.get(parentsArePeople).add(parent);
        personAVF.get(relativesArePeople).add(relative);
        personAVF.get(relativesArePeople).add(parent);
        Map<Resource, Set<URI>> dogAVF = new HashMap<>();
        dogAVF.put(parentsAreDogs, new HashSet<>());
        dogAVF.get(parentsAreDogs).add(parent);
        when(inferenceEngine.getAllValuesFromByValueType(person)).thenReturn(personAVF);
        when(inferenceEngine.getAllValuesFromByValueType(dog)).thenReturn(dogAVF);
        // Query for a specific type and rewrite using the visitor:
        StatementPattern originalSP = new StatementPattern(new Var("s"), new Var("p", RDF.TYPE), new Var("o", person));
        final Projection query = new Projection(originalSP, new ProjectionElemList(new ProjectionElem("s", "subject")));
        query.visit(new AllValuesFromVisitor(conf, inferenceEngine));
        // Expected structure: a union of two elements: one is equal to the original statement
        // pattern, and the other one joins a list of predicate/restriction type combinations
        // with another join querying for values of that predicate for members of that type.
        Assert.assertTrue(query.getArg() instanceof Union);
        TupleExpr left = ((Union) query.getArg()).getLeftArg();
        TupleExpr right = ((Union) query.getArg()).getRightArg();
        final Join join;
        if (left instanceof StatementPattern) {
            Assert.assertEquals(originalSP, left);
            Assert.assertTrue(right instanceof Join);
            join = (Join) right;
        }
        else {
            Assert.assertEquals(originalSP, right);
            Assert.assertTrue(left instanceof Join);
            join = (Join) left;
        }
        Assert.assertTrue(join.getLeftArg() instanceof FixedStatementPattern);
        Assert.assertTrue(join.getRightArg() instanceof Join);
        FixedStatementPattern fsp = (FixedStatementPattern) join.getLeftArg();
        left = ((Join) join.getRightArg()).getLeftArg();
        right = ((Join) join.getRightArg()).getRightArg();
        Assert.assertTrue(left instanceof StatementPattern);
        Assert.assertTrue(right instanceof StatementPattern);
        // Verify expected predicate/restriction pairs
        Assert.assertTrue(fsp.statements.contains(new NullableStatementImpl(parentsArePeople, OWL.ONPROPERTY, parent)));
        Assert.assertTrue(fsp.statements.contains(new NullableStatementImpl(relativesArePeople, OWL.ONPROPERTY, relative)));
        Assert.assertTrue(fsp.statements.contains(new NullableStatementImpl(relativesArePeople, OWL.ONPROPERTY, parent)));
        Assert.assertTrue(fsp.statements.contains(new NullableStatementImpl(parentsAreTallPeople, OWL.ONPROPERTY, parent)));
        Assert.assertEquals(4, fsp.statements.size());
        // Verify general pattern for matching instances of each pair: Join on unknown subject; left
        // triple states it belongs to the restriction while right triple relates it to the original
        // subject variable by the relevant property. Restriction and property variables are given
        // by the FixedStatementPattern.
        StatementPattern leftSP = (StatementPattern) left;
        StatementPattern rightSP = (StatementPattern) right;
        Assert.assertEquals(rightSP.getSubjectVar(), leftSP.getSubjectVar());
        Assert.assertEquals(RDF.TYPE, leftSP.getPredicateVar().getValue());
        Assert.assertEquals(fsp.getSubjectVar(), leftSP.getObjectVar());
        Assert.assertEquals(fsp.getObjectVar(), rightSP.getPredicateVar());
        Assert.assertEquals(originalSP.getSubjectVar(), rightSP.getObjectVar());
    }
}
