package io.arabesque.embedding;

import io.arabesque.conf.Configuration;
import io.arabesque.utils.collection.IntArrayList;
import com.koloboke.collect.IntCollection;
import com.koloboke.function.IntConsumer;

import java.io.DataInput;
import java.io.IOException;
import java.io.ObjectInput;

public class VertexInducedEmbedding extends BasicEmbedding {
    // Consumers {{
    private UpdateEdgesConsumer updateEdgesConsumer;
    // }}

    // Edge tracking for incremental modifications {{
    private IntArrayList numEdgesAddedWithWord;
    // }}
    //

    public VertexInducedEmbedding() {
        super();
        updateEdgesConsumer = new UpdateEdgesConsumer();
        numEdgesAddedWithWord = new IntArrayList();
    }

    @Override
    public void init(Configuration config) {
        super.init(config);
    }

    @Override
    public void reset() {
        super.reset();
        numEdgesAddedWithWord.clear();
    }

    @Override
    public IntArrayList getWords() {
        return getVertices();
    }

    @Override
    public int getNumWords() {
        return getNumVertices();
    }

    @Override
    public String toOutputString() {
        StringBuilder sb = new StringBuilder();

        IntArrayList vertices = getVertices();

        for (int i = 0; i < vertices.size(); ++i) {
            sb.append(vertices.getUnchecked(i));
            sb.append(" ");
        }

        return sb.toString();
    }


    @Override
    public int getNumVerticesAddedWithExpansion() {
        if (vertices.isEmpty()) {
            return 0;
        }

        return 1;
    }

    @Override
    public int getNumEdgesAddedWithExpansion() {
        return numEdgesAddedWithWord.getLastOrDefault(0);
    }

    protected IntCollection getValidNeighboursForExpansion(int vertexId) {
        return configuration.getMainGraph().getVertexNeighbours(vertexId);
    }

    @Override
    protected boolean areWordsNeighbours(int wordId1, int wordId2) {
        return configuration.getMainGraph().isNeighborVertex(wordId1, wordId2);
    }

    @Override
    public void addWord(int word) {
        super.addWord(word);
        vertices.add(word);
        updateEdges(word, vertices.size() - 1);
    }

    @Override
    public void removeLastWord() {
        if (getNumVertices() == 0) {
            return;
        }

        int numEdgesToRemove = numEdgesAddedWithWord.pop();
        edges.removeLast(numEdgesToRemove);
        vertices.removeLast();

        super.removeLastWord();
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        reset();
        
        init(Configuration.get(in.readInt()));

        vertices.readFields(in);

        int numVertices = vertices.size();

        for (int i = 0; i < numVertices; ++i) {
            updateEdges(vertices.getUnchecked(i), i);
        }
    }

    @Override
    public void readExternal(ObjectInput objInput) throws IOException, ClassNotFoundException {
       readFields(objInput);
    }

    /**
     * Updates the list of edges of this embedding based on the addition of a new vertex.
     *
     * @param newVertexId The id of the new vertex that was just added.
     */
    private void updateEdges(int newVertexId, int positionAdded) {
        IntArrayList vertices = getVertices();

        int addedEdges = 0;

        // For each vertex (except the last one added)
        for (int i = 0; i < positionAdded; ++i) {
            int existingVertexId = vertices.getUnchecked(i);

            updateEdgesConsumer.reset();
            configuration.getMainGraph().
               forEachEdgeId(existingVertexId, newVertexId, updateEdgesConsumer);
            addedEdges += updateEdgesConsumer.getNumAdded();
        }

        numEdgesAddedWithWord.add(addedEdges);
    }

    private class UpdateEdgesConsumer implements IntConsumer {
        private int numAdded;

        public void reset() {
            numAdded = 0;
        }

        public int getNumAdded() {
            return numAdded;
        }

        @Override
        public void accept(int i) {
            edges.add(i);
            ++numAdded;
        }
    }
}
