package edu.umn.cs.recsys.ii;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import org.grouplens.lenskit.collections.LongUtils;
import org.grouplens.lenskit.core.Transient;
import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.scored.ScoredIdListBuilder;
import org.grouplens.lenskit.scored.ScoredIds;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.*;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleItemItemModelBuilder implements Provider<SimpleItemItemModel> {
    private final ItemDAO itemDao;
    private final UserEventDAO userEventDao;
    private static final Logger logger = LoggerFactory.getLogger(SimpleItemItemModelBuilder.class);

    @Inject
    public SimpleItemItemModelBuilder(@Transient ItemDAO idao,
                                      @Transient UserEventDAO uedao) {
        itemDao = idao;
        userEventDao = uedao;
    }

    @Override
    public SimpleItemItemModel get() {
        // Get the transposed rating matrix
        // This gives us a map of item IDs to those items' rating vectors
        Map<Long, ImmutableSparseVector> itemVectors = getItemVectors();

        // Get all items - you might find this useful
        LongSortedSet items = LongUtils.packedSet(itemVectors.keySet());
        // Map items to vectors of item similarities
        Map<Long,MutableSparseVector> itemSimilarities = new HashMap<Long, MutableSparseVector>();

        // TODO Compute the similarities between each pair of items
        for(Long i : items)
        {
            ImmutableSparseVector vectorI = itemVectors.get(i);
            MutableSparseVector similarities = vectorI.mutableCopy();
            for(Long j : items)
            {
                if(i.equals(j)) continue;

                if(similarities.containsKey(j)== false) continue;

                ImmutableSparseVector vectorJ = itemVectors.get(j);
                double sim = computeSimilarity(vectorI, vectorJ);
                similarities.set(j, sim);
            }
            itemSimilarities.put(i, similarities);
        }

        // build list of score
        Map<Long,List<ScoredId>> nbrhoods  = new HashMap<Long, List<ScoredId>>();
        try {

            for(Long i : items)
            {
                ScoredIdListBuilder scoredIdListBuilder = ScoredIds.newListBuilder();
                MutableSparseVector similarities = itemSimilarities.get(i);
                for(VectorEntry entry : similarities.fast()){
                    double similarity = entry.getValue();
                    // keep only positive similarity
                    if(similarity > 0.000001) scoredIdListBuilder.add(entry.getKey(),similarity);
                }
                scoredIdListBuilder.sort(ScoreIdComparator);
                nbrhoods.put(i,scoredIdListBuilder.build() );
            }

        } catch (Exception ex)
        {
            logger.debug(ex.toString());
        }

        // It will need to be in a map of longs to lists of Scored IDs to store in the model
        return new SimpleItemItemModel(nbrhoods);
    }

    private double computeSimilarity(ImmutableSparseVector vectorI, ImmutableSparseVector vectorJ) {

        double dotProduct = vectorI.dot(vectorJ);
        double normI = vectorI.norm();
        double normJ = vectorJ.norm();
        return  dotProduct / (normI*normJ);
    }

    // sort descending order
    public static Comparator<ScoredId> ScoreIdComparator = new Comparator<ScoredId>() {
        public int compare(ScoredId scored1, ScoredId scored2) {
            if(scored2.getScore() > scored1.getScore()) return 1;
            else if(scored2.getScore() < scored1.getScore()) return -1;
            else return 0;
        }
    };

    /**
     * Load the data into memory, indexed by item.
     * @return A map from item IDs to item rating vectors. Each vector contains users' ratings for
     * the item, keyed by user ID.
     */
    public Map<Long,ImmutableSparseVector> getItemVectors() {
        // set up storage for building each item's rating vector
        LongSet items = itemDao.getItemIds();
        // map items to maps from users to ratings
        Map<Long,Map<Long,Double>> itemData = new HashMap<Long, Map<Long, Double>>();
        for (long item: items) {
            itemData.put(item, new HashMap<Long, Double>());
        }
        // itemData should now contain a map to accumulate the ratings of each item

        // stream over all user events
        Cursor<UserHistory<Event>> stream = userEventDao.streamEventsByUser();
        try {
            for (UserHistory<Event> evt: stream) {
                MutableSparseVector vector = RatingVectorUserHistorySummarizer.makeRatingVector(evt).mutableCopy();
                // vector is now the user's rating vector
                // TODO Normalize this vector and store the ratings in the item data
                double normFactor = vector.norm();
                Long userId = evt.getUserId();
                vector.multiply(1.0/normFactor);

                for(VectorEntry fast : vector.fast()){
                    long itemId = fast.getKey();
                    double rating = fast.getValue();
                    Map<Long, Double> map = itemData.get(itemId);
                    if(map.containsKey(userId)== false )
                        map.put(userId, rating);
                }
            }
        } finally {
            stream.close();
        }

        // This loop converts our temporary item storage to a map of item vectors
        Map<Long,ImmutableSparseVector> itemVectors = new HashMap<Long, ImmutableSparseVector>();
        for (Map.Entry<Long,Map<Long,Double>> entry: itemData.entrySet()) {
            MutableSparseVector vec = MutableSparseVector.create(entry.getValue());
            itemVectors.put(entry.getKey(), vec.immutable());
        }
        return itemVectors;
    }


}
