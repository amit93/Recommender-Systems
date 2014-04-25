package edu.umn.cs.recsys.ii;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
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
import org.grouplens.lenskit.scored.PackedScoredIdList;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.scored.ScoredIdListBuilder;
import org.grouplens.lenskit.scored.ScoredIds;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Comparator;


/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */

public class SimpleItemItemModelBuilder implements Provider<SimpleItemItemModel> {
    private final ItemDAO itemDao;
    private final UserEventDAO userEventDao;
    private static final Logger logger = LoggerFactory.getLogger(SimpleItemItemModelBuilder.class);;

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
        long movieId[] = items.toLongArray();
        // Map items to vectors of item similarities
        Map<Long,MutableSparseVector> itemSimilarities = new HashMap<Long, MutableSparseVector>();
        // TODO Compute the similarities between each pair of items
        // It will need to be in a map of longs to lists of Scored IDs to store in the model

        MutableSparseVector currentItemRatingVector;
        MutableSparseVector compareItemRatingVector;
        MutableSparseVector similarityVector =null;

        HashMap <Long, List<ScoredId>> modelMap = new HashMap<Long, List<ScoredId>>();

        ScoredIdListBuilder listBuilder = null;
        ComparatorClass comp = new ComparatorClass();
        double similarity= 0.0;

        LongBidirectionalIterator itemItr1 = items.iterator();
        while(itemItr1.hasNext())
        {
            long itemID1 = itemItr1.next();

            //Defining similarity vector and ListBuilder each time a new itemID1 arrives.
            similarityVector = MutableSparseVector.create(items);
            listBuilder = new ScoredIdListBuilder();

            currentItemRatingVector = itemVectors.get(itemID1).mutableCopy();

            LongBidirectionalIterator itemItr2 = items.iterator();
            while(itemItr2.hasNext())
            {
                long itemID2 = itemItr2.next();
                compareItemRatingVector = itemVectors.get(itemID2).mutableCopy();
                if(itemID1 != itemID2)
                {
                    similarity = new CosineVectorSimilarity().similarity(currentItemRatingVector,compareItemRatingVector);
                    if(similarity >=0.0)
                    {
                        similarityVector.set(itemID2, similarity);
                        listBuilder.add(itemID2, similarity);
                    }
                }
            }
            itemSimilarities.put(itemID1, similarityVector);

            PackedScoredIdList list = listBuilder.sort(comp).finish();
            modelMap.put(itemID1, list);
        }

        return new SimpleItemItemModel(modelMap);
    }

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
                long UserID = evt.getUserId();
                // vector is now the user's rating vector
                // TODO Normalize this vector and store the ratings in the item data
                double mean = vector.mean();
                for(VectorEntry e: vector)
                {
                    itemData.get(e.getKey()).put(UserID,e.getValue()-mean);
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

    class ComparatorClass implements Comparator<ScoredId> {

        @Override
        public int compare(ScoredId o1, ScoredId o2) {
            double score1 = o1.getScore();
            double score2 = o2.getScore();
            return (score1 > score2 ? -1 : (score1 == score2) ? 0 : 1);
        }

    }
}
