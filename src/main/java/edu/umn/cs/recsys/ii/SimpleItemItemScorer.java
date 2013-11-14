package edu.umn.cs.recsys.ii;

import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.History;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.knn.NeighborhoodSize;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.List;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleItemItemScorer extends AbstractItemScorer {
    private final SimpleItemItemModel model;
    private final UserEventDAO userEvents;
    private final int neighborhoodSize;
    private static final Logger logger = LoggerFactory.getLogger(SimpleItemItemScorer.class);

    @Inject
    public SimpleItemItemScorer(SimpleItemItemModel m, UserEventDAO dao,
                                @NeighborhoodSize int nnbrs) {
        model = m;
        userEvents = dao;
        neighborhoodSize = nnbrs;
    }

    /**
     * Score items for a user.
     * @param user The user ID.
     * @param scores The score vector.  Its key domain is the items to score, and the scores
     *               (rating predictions) should be written back to this vector.
     */
    @Override
    public void score(long user, @Nonnull MutableSparseVector scores) {
        SparseVector ratings = getUserRatingVector(user);

        for (VectorEntry e: scores.fast(VectorEntry.State.EITHER)) {
            long item = e.getKey();
            List<ScoredId> neighbors;
            neighbors = model.getNeighbors(item);
            neighbors = neighbors.subList(0, Math.min(neighborhoodSize,neighbors.size()));

            // TODO Score this item and save the score into scores
            logger.info(String.format("item %d : ", item));
            StringBuilder sb = new StringBuilder();
            double weightedSum = 0;
            int count = 0;
            for(ScoredId scoredId : neighbors)
            {
                // get rating
                if(ratings.containsKey(scoredId.getId()))
                {
                    count++;
                    double rating = ratings.get(scoredId.getId());
                    weightedSum += scoredId.getScore() * rating;
                    sb.append(String.format("(%d) = %f,", scoredId.getId(), scoredId.getScore()));
                }
            }
            if(count > 0)
            {
                weightedSum /= count;
                sb.append(" score=");
                sb.append(weightedSum);
                sb.append("\n");
                logger.info(sb.toString());
                scores.set(item, weightedSum);
            }
        }
    }

    /**
     * Get a user's ratings.
     * @param user The user ID.
     * @return The ratings to retrieve.
     */
    private SparseVector getUserRatingVector(long user) {
        UserHistory<Rating> history = userEvents.getEventsForUser(user, Rating.class);
        if (history == null) {
            history = History.forUser(user);
        }

        return RatingVectorUserHistorySummarizer.makeRatingVector(history);
    }
}
