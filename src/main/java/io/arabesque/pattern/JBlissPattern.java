package io.arabesque.pattern;

import io.arabesque.conf.Configuration;
import fi.tkk.ics.jbliss.Graph;
import fi.tkk.ics.jbliss.Reporter;
import com.koloboke.collect.map.IntIntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.hash.HashIntIntMap;
import org.apache.log4j.Logger;

public class JBlissPattern extends BasicPattern {
    private static final Logger LOG = Logger.getLogger(JBlissPattern.class);

    private Graph<Integer> jblissGraph;

    public JBlissPattern() {
        super();
    }

    public JBlissPattern(JBlissPattern other) {
        super(other);
    }

    @Override
    public void init(Configuration config) {
        super.init(config);
        jblissGraph = new Graph<>(this, mainGraph);
    }

    @Override
    public Pattern copy() {
        Pattern pattern = new JBlissPattern(this);
        pattern.init(this.configuration);
        return pattern;
    }

    protected class VertexPositionEquivalencesReporter implements Reporter {
        VertexPositionEquivalences equivalences;

        public VertexPositionEquivalencesReporter(VertexPositionEquivalences equivalences) {
            this.equivalences = equivalences;
        }

        @Override
        public void report(HashIntIntMap generator, Object user_param) {
            IntIntCursor generatorCursor = generator.cursor();

            while (generatorCursor.moveNext()) {
                int oldPos = generatorCursor.key();
                int newPos = generatorCursor.value();

                equivalences.addEquivalence(oldPos, newPos);
                //equivalences.addEquivalence(newPos, oldPos);
            }
        }
    }

    @Override
    protected void fillVertexPositionEquivalences(VertexPositionEquivalences vertexPositionEquivalences) {
        for (int i = 0; i < getNumberOfVertices(); ++i) {
            vertexPositionEquivalences.addEquivalence(i, i);
        }

        VertexPositionEquivalencesReporter reporter = new VertexPositionEquivalencesReporter(vertexPositionEquivalences);
        jblissGraph.findAutomorphisms(reporter, null);
        vertexPositionEquivalences.propagateEquivalences();
    }

    @Override
    protected void fillCanonicalLabelling(IntIntMap canonicalLabelling) {
        jblissGraph.fillCanonicalLabeling(canonicalLabelling);
    }

    @Override
    public boolean turnCanonical() {
        dirtyVertexPositionEquivalences = true;

        return super.turnCanonical();
    }
}
