package edu.umn.cs.recsys.ii;

import org.grouplens.lenskit.basic.AbstractGlobalItemScorer;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Global item scorer to find similar items.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleGlobalItemScorer extends AbstractGlobalItemScorer {
    private final SimpleItemItemModel model;

    @Inject
    public SimpleGlobalItemScorer(SimpleItemItemModel mod) {
        model = mod;
    }

    /**
     * Score items with respect to a set of reference items.
     * @param items The reference items.
     * @param scores The score vector. Its domain is the items to be scored, and the scores should
     *               be stored into this vector.
     */
    @Override
    public void globalScore(@Nonnull Collection<Long> items, @Nonnull MutableSparseVector scores) {
        scores.fill(0);
        // TODO score items in the domain of scores
        // each item's score is the sum of its similarity to each item in items, if they are
        // neighbors in the model.

        double sum =0;
        ScoredId scoreId =null;
        List<ScoredId> neighbors;

        for (VectorEntry e: scores.fast(VectorEntry.State.EITHER)) {
            long movieId = e.getKey();
            Iterator <Long> itr = items.iterator();

            sum = 0.0;
            while(itr.hasNext())
            {
                long refMovieId = itr.next();
                neighbors = model.getNeighbors(refMovieId);
                if((scoreId=listContainsMovieId(neighbors,movieId))!=null)
                {
                    sum= sum + scoreId.getScore();
                }
            }

            scores.set(movieId,sum);
        }
    }

    private ScoredId listContainsMovieId(List<ScoredId> list, long movieId) {

        Iterator <ScoredId> itr = list.iterator();
        ScoredId scoreId = null;

        while(itr.hasNext()) {
            scoreId = itr.next();

            if(scoreId.getId() == movieId) return scoreId;
        }

        return null;
    }
}
