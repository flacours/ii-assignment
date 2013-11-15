package edu.umn.cs.recsys.ii;

import org.grouplens.lenskit.basic.AbstractGlobalItemScorer;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Collection;
import java.util.List;

/**
 * Global item scorer to find similar items.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleGlobalItemScorer extends AbstractGlobalItemScorer {
    private final SimpleItemItemModel model;
    private static final Logger logger = LoggerFactory.getLogger(SimpleGlobalItemScorer.class);
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
        for (VectorEntry e: scores.fast(VectorEntry.State.EITHER)) {
            long item = e.getKey();
            List<ScoredId> neighbors;
            neighbors = model.getNeighbors(item);
            logger.debug("GlobalItem: "+item );
            double sum = 0;
            int count = 0;

            for(Long i: items) {

                sum += getScored(neighbors, i);
            }

            scores.set(item, sum);
        }

    }

    private double getScored(List<ScoredId> neighbors, Long i) {
        double retVal = 0;
        for(ScoredId scoredId : neighbors) {
            if(scoredId.getId() == i) {
                retVal = scoredId.getScore();
                break;
            }
        }
        return  retVal;
    }
}
