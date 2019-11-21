/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.integrationtests;

import io.crate.metadata.RelationName;
import io.crate.statistics.TableStats;
import io.crate.types.DataTypes;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;

public class AnalyzeITest extends SQLTransportIntegrationTest{

    @Test
    public void test_analyze_statement_refreshes_table_stats_and_stats_are_visible_in_pg_class_and_pg_stats() {
        execute("create table doc.tbl (x int)");
        execute("insert into doc.tbl (x) values (1), (2), (3), (null), (3), (3)");
        execute("refresh table doc.tbl");
        execute("analyze");
        for (TableStats tableStats : internalCluster().getInstances(TableStats.class)) {
            assertThat(tableStats.numDocs(new RelationName("doc", "tbl")), is(6L));
        }
        execute("select reltuples from pg_class where relname = 'tbl'");
        assertThat(response.rows()[0][0], is(6.0f));

        execute(
            "select " +
            "   null_frac," +
            "   avg_width," +
            "   n_distinct," +
            "   most_common_vals," +
            "   most_common_freqs," +
            "   histogram_bounds " +
            "from pg_stats where tablename = 'tbl'");
        Object[] row = response.rows()[0];
        assertThat(((Float) row[0]).doubleValue(), Matchers.closeTo(0.166, 0.01));
        assertThat(row[1], is(DataTypes.INTEGER.fixedSize()));
        assertThat(row[2], is(3.0f));
        assertThat(((List<String>) row[3]), Matchers.empty());
        assertThat(((List<Double>) row[4]), Matchers.empty());
        assertThat(((List<String>) row[5]), Matchers.contains("1", "2", "3"));
    }
}