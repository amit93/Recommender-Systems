package edu.umn.cs.recsys.uu;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.History;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.List;

/**
 * User-user item scorer.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleUserUserItemScorer extends AbstractItemScorer {
    private final UserEventDAO userDao;
    private final ItemEventDAO itemDao;

    @Inject
    public SimpleUserUserItemScorer(UserEventDAO udao, ItemEventDAO idao) {
        userDao = udao;
        itemDao = idao;
    }

    @Override
    public void score(long user, @Nonnull MutableSparseVector scores) {
        MutableSparseVector userVector = getUserRatingVector(user).mutableCopy();
        double userMean = userVector.mean();

        for(VectorEntry e:userVector)
        {
            userVector.set(e.getKey(), e.getValue() - userMean);
        }

        // TODO Score items for this user using user-user collaborative filtering
        MutableSparseVector similarityVector,userForCompare;
        LongArrayList keysSorted;

        // This is the loop structure to iterate over items to score
        for (VectorEntry e: scores.fast(VectorEntry.State.EITHER))
        {
                 similarityVector =  MutableSparseVector.create(itemDao.getUsersForItem(e.getKey()));

                for(Long UserId:itemDao.getUsersForItem(e.getKey()))
                {
                    if(UserId!=user)
                    {
                       userForCompare = (getUserRatingVector(UserId).mutableCopy();
                       Double userForComparemean = userForCompare.mean();
                       for (VectorEntry em:userForCompare)
                           userForCompare.set(em.getKey(),em.getValue()-userForComparemean);

                       double similarity =  new CosineVectorSimilarity().similarity(userVector,userForCompare);
                        similarityVector.set(UserId,similarity);
                    }

                }
               keysSorted = similarityVector.keysByValue();
        }
    }

    /**
     * Get a user's rating vector.
     * @param user The user ID.
     * @return The rating vector.
     */
    private SparseVector getUserRatingVector(long user) {
        UserHistory<Rating> history = userDao.getEventsForUser(user, Rating.class);
        if (history == null) {
            history = History.forUser(user);
        }
        return RatingVectorUserHistorySummarizer.makeRatingVector(history);
    }
}
