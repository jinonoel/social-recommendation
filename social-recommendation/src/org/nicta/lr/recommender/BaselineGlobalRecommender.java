package org.nicta.lr.recommender;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BaselineGlobalRecommender extends Recommender
{	
	public BaselineGlobalRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures)
	{
		super(linkLikes, userFeatures, linkFeatures, null);
	}
	
	public void train(Map<Long, Set<Long>> trainSamples) 
	{
		//do nothing
	}
	
	public Map<Long, Double[]> getPrecisionRecall(Map<Long, Set<Long>> testData, int boundary)
	{
		HashMap<Long, Double[]> precisionRecalls = new HashMap<Long, Double[]>();
		
		HashSet<Long> combinedTest = new HashSet<Long>();
		for (long userId : testData.keySet()) {
			combinedTest.addAll(testData.get(userId));
		}
		
		
		for (long userId : testData.keySet()) {
			Set<Long> testLinks = testData.get(userId);
			
			ArrayList<Double> scores = new ArrayList<Double>();
			ArrayList<Long> ids = new ArrayList<Long>();
			
			for (long testId : combinedTest) {
				Double[] feature = linkFeatures.get(testId);
				double prediction = feature[0] + feature[1];
				
				scores.add(prediction);
				ids.add(testId);
			}
			
			Object[] sorted = sort(scores, ids);
			List<Long> idLength = (List<Long>)sorted[1];
			
			int limit = boundary;
			if (idLength.size() < limit) limit = idLength.size();
		
			
			Long[] top = new Long[limit];
			for (int x = 0; x < top.length; x++) {
				top[x] = idLength.get(x);
			}
			
			
			double precision = getUserPrecision(top, userId);
			double recall = getUserRecall(top, userId, testData.get(userId));
			
			precisionRecalls.put(userId, new Double[]{precision, recall});
		}
		
		return precisionRecalls;
	}
	
	public Map<Long, Map<Long, Double>> getPredictions(Map<Long, Set<Long>> testData)
	{
		Map<Long, Map<Long, Double>> predictions = new HashMap<Long, Map<Long, Double>>();
		
		for (long userId : testData.keySet()) {
			HashMap<Long, Double> userPredictions = new HashMap<Long, Double>();
			predictions.put(userId, userPredictions);
			
			Set<Long> testLinks = testData.get(userId);
			
			for (long testId : testLinks) {
				Double[] feature = linkFeatures.get(testId);
				double prediction = feature[0] + feature[1];
				
				userPredictions.put(testId, prediction);
			}
		}
		
		return predictions;
	}
	
	public void saveModel()
		throws SQLException
	{
		//do nothing
	}
	
	public Map<Long, Map<Long, Double>> recommend(Map<Long, Set<Long>> linksToRecommend)
	{
		//Will never be used for online recommendatons
		return null;
	}
}
