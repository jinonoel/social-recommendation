package org.nicta.lr;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.nicta.lr.minimizer.FeatureMinimizer;
import org.nicta.lr.minimizer.MSRSocialMinimizer;
import org.nicta.lr.minimizer.Minimizer;
import org.nicta.lr.minimizer.SocialMinimizer;
import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.RecommenderUtil;
import org.nicta.lr.util.UserUtil;

public class LinkRecommender 
{
	private Minimizer minimizer;
	
	public LinkRecommender(String type)
	{
		if ("Feature".equals(type)) {
			minimizer = new FeatureMinimizer();
		}
		else if ("Social".equals(type)) {
			minimizer = new SocialMinimizer();
		}	
		else if ("MSR".equals(type)) {
			minimizer = new MSRSocialMinimizer();
		}
	}
	
	public static void main(String[] args)
		throws Exception
	{
		//LinkRecommender main = new LinkRecommender(args[1]);
		LinkRecommender lr = new LinkRecommender("Feature");
		lr.crossValidate();
	}
	
	public void crossValidate()
		throws Exception
	{
		HashMap<Long, Double[]> users = UserUtil.getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = LinkUtil.getLinkFeatures(false);
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, HashSet<Long>> linkLikes = LinkUtil.getLinkLikes(links.keySet());
		HashMap<Long, HashMap<Long, Double>> friendships = UserUtil.getFriendships();
		
		//HashMap<Long, HashMap<Long, Double>> friendConnections = getFriendInteractionMeasure();
		//HashMap<Long, HashMap<Long, Double>> friendConnections = getFriendLikeSimilarity();
		HashMap<Long, HashMap<Long, Double>> friendConnections = friendships;
		
		HashMap<Long, HashSet<Long>> userLinkSamples = getUserLinksSample(users.keySet(), friendships, false);
		System.out.println("Samples: " + userLinkSamples.size());
		
		HashSet<Long> withLikes = new HashSet<Long>();

		for (long userId : userLinkSamples.keySet()) {
			if (userLinkSamples.get(userId).size() >= 30) {
				withLikes.add(userId);
			}
		}
		
		System.out.println("For Testing: " + withLikes.size());
		
		Set<String> words = LinkUtil.getMostCommonWords();
		System.out.println("Words: " + words.size());
		HashMap<Long, HashSet<String>> linkWords = LinkUtil.getLinkWordFeatures(words, false);

		RecommenderUtil.closeSqlConnection();
		
		HashMap<Long, HashSet<Long>> tested = new HashMap<Long, HashSet<Long>>();
		for (long userId : withLikes) {
			tested.put(userId, new HashSet<Long>());
		}
		
		double totalRMSE = 0;
		for (int x = 0; x < 10; x++) {
			HashMap<Long, HashSet<Long>> forTesting = new HashMap<Long, HashSet<Long>>();
			
			for (long userId : withLikes) {
				HashSet<Long> userTesting = new HashSet<Long>();
				forTesting.put(userId, userTesting);
				
				HashSet<Long> samples = userLinkSamples.get(userId);
				Object[] sampleArray = samples.toArray();
				
				int addedCount = 0;
				
				while (addedCount < 3) {
					int randomIndex = (int)(Math.random() * (sampleArray.length));
					Long randomLinkId = (Long)sampleArray[randomIndex];
					
					if (!tested.get(userId).contains(randomLinkId) && ! userTesting.contains(randomLinkId)) {
						userTesting.add(randomLinkId);
						tested.get(userId).add(randomLinkId);
						samples.remove(randomLinkId);
						addedCount++;
					}
				}		
			}
			
			Double[][] userFeatureMatrix = getPrior(Constants.USER_FEATURE_COUNT);
			Double[][] linkFeatureMatrix = getPrior(Constants.LINK_FEATURE_COUNT);
	
			HashMap<Long, Double[]> userIdColumns = getMatrixIdColumns(users.keySet());
			HashMap<Long, Double[]> linkIdColumns = getMatrixIdColumns(links.keySet());
			HashMap<String, Double[]> wordColumns = getWordColumns(words);
			
			minimizer.minimize(linkLikes, userFeatureMatrix, linkFeatureMatrix, users, links, friendConnections, userIdColumns, linkIdColumns, userLinkSamples, wordColumns, linkWords, words);
			
			HashMap<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, users);
			HashMap<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, links, linkWords, wordColumns);
			
			double foldRMSE = RecommenderUtil.calcRMSE(userTraits, linkTraits, linkLikes, forTesting);
			System.out.println("RMSE of Run " + (x+1) + ": " + foldRMSE);
			totalRMSE += foldRMSE;
			
			for (long userId : forTesting.keySet()) {
				HashSet<Long> tests = forTesting.get(userId);
				for (long linkId : tests) {
					userLinkSamples.get(userId).add(linkId);
				}
			}
		}
		
		System.out.println("Average RMSE: " + (totalRMSE / 10));
	}
	
	public void recommend()
		throws Exception
	{
		System.out.println("Loading Data..." + new Date());
		
		HashMap<Long, Double[]> users = UserUtil.getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = LinkUtil.getLinkFeatures(true);
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, HashSet<Long>> linkLikes = LinkUtil.getLinkLikes(links.keySet());
		HashMap<Long, HashMap<Long, Double>> friendships = UserUtil.getFriendships();
		HashMap<Long, HashMap<Long, Double>> friendConnections = UserUtil.getFriendInteractionMeasure();
		//HashMap<Long, HashMap<Long, Double>> friendConnections = UserUtil.getFriendLikeSimilarity();
		
		Set<String> words = LinkUtil.loadMostCommonWords();
		if (words.size() == 0) {
			words = LinkUtil.getMostCommonWords();
		}
		HashMap<Long, HashSet<String>> linkWords = LinkUtil.getLinkWordFeatures(words, true);
	
		Double[][] userFeatureMatrix = loadFeatureMatrix("lrUserMatrix", Constants.USER_FEATURE_COUNT, "Social");
		if (userFeatureMatrix == null) {
			userFeatureMatrix = getPrior(Constants.USER_FEATURE_COUNT);
		}
		Double[][] linkFeatureMatrix = loadFeatureMatrix("lrLinkMatrix", Constants.LINK_FEATURE_COUNT, "Social");
		if (linkFeatureMatrix == null) {
			linkFeatureMatrix = getPrior(Constants.LINK_FEATURE_COUNT);
		}
		HashMap<Long, Double[]>userIdColumns = loadIdColumns("lrUserMatrix", "Social");
		if (userIdColumns.size() == 0) {
			userIdColumns = getMatrixIdColumns(users.keySet());
		}
		
		HashMap<Long, Double[]>linkIdColumns = loadIdColumns("lrLinkMatrix", "Social");
		if (linkIdColumns.size() == 0) {
			linkIdColumns = getMatrixIdColumns(links.keySet());
		}
		
		HashMap<String, Double[]> wordColumns = loadWordColumns("Social");
		if (wordColumns.size() == 0) {
			wordColumns = getWordColumns(words);
		}
		
		updateMatrixColumns(links.keySet(), linkIdColumns);
		updateMatrixColumns(users.keySet(), userIdColumns);
		
		HashMap<Long, HashSet<Long>> userLinkSamples = getUserLinksSample(users.keySet(), friendships, true);
		
		System.out.println("Minimizing...");
		minimizer.minimize(linkLikes, userFeatureMatrix, linkFeatureMatrix, users, links, friendConnections, userIdColumns, linkIdColumns, userLinkSamples, wordColumns, linkWords, words);
		
		System.out.println("Recommending...");
		HashMap<Long, HashSet<Long>> linksToRecommend = getLinksForRecommending(friendships, "Social");
		HashMap<Long, HashMap<Long, Double>> recommendations = recommendLinks(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, 
																							users, links, linksToRecommend, linkWords, wordColumns);
		
		System.out.println("Saving...");
		saveLinkRecommendations(recommendations, "Social");
		saveMatrices(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, wordColumns, "Social");
		
		RecommenderUtil.closeSqlConnection();
		
		System.out.println("Done");
	}
	
	/**
	 * For creation of the latent matrices.
	 * 
	 * @param featureCount
	 * @return
	 */
	public Double[][] getPrior(int featureCount)
	{
		Random random = new Random();
		
		Double[][] prior = new Double[Constants.K][featureCount];
		
		for (int x = 0; x < Constants.K; x++) {
			for (int y = 0; y < featureCount; y++) {
				prior[x][y] = random.nextGaussian();
				//prior[x][y] = 0.0;
			}
		}
		
		return prior;
	}
	
	/**
	 * For training, get a sample of links for each user.
	 * We look for links only given a certain date range, and only get 9 times as much 'unliked' links as 'liked' links
	 * because we do not want the 'liked' links to be drowned out during training.
	 * This means that if user hasn't liked any links, we do not get any unliked link as well.
	 * 
	 * @param userIds
	 * @param friendships
	 * @return
	 * @throws SQLException
	 */
	public HashMap<Long, HashSet<Long>> getUserLinksSample(Set<Long> userIds, HashMap<Long, HashMap<Long, Double>> friendships, boolean limit)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> userLinkSamples = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		for (Long id : userIds) {
			HashSet<Long> samples = new HashSet<Long>();
			
			//Get the links that were liked by the user
			String selectStr = "SELECT l.id FROM linkrLinks l, linkrLinkLikes lp WHERE l.id=lp.link_id AND lp.id=" + id;
			if (limit) {
				selectStr += " AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.WINDOW_RANGE + "))";
			}
			
			ResultSet result = statement.executeQuery(selectStr);
			while (result.next()) {
				samples.add(result.getLong("l.id"));
			}
			
			if (samples.size() == 0) continue;
			
			//Sample links that weren't liked.
			//Links here should be links that were shared by friends to increase the chance that the user has actually seen this and not
			//liked them
			Set<Long> friends;
			if (friendships.containsKey(id)) {
				friends = friendships.get(id).keySet();
			}
			else {
				friends = new HashSet<Long>();
			}
			
			StringBuilder query = new StringBuilder("SELECT id FROM linkrLinks WHERE uid IN (0");
			for (Long friendId : friends) {
				query.append(",");
				query.append(friendId);
			}
			query.append(") AND id NOT IN(0");
			for (Long likedId : samples) {
				query.append(",");
				query.append(likedId);
			}	
			query.append(") ");
			if (limit) {
				query.append("AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.WINDOW_RANGE + ")) ");
			}
			query.append("ORDER BY created_time DESC LIMIT ");
			query.append(samples.size() * 9);
			result = statement.executeQuery(query.toString());
			
			while (result.next()) {
				samples.add(result.getLong("id"));
			}
			
			userLinkSamples.put(id, samples);
		}
		
		return userLinkSamples;
	}
	
	/**
	 * After training, start recommending links to the user. This will get a set of links that haven't been liked by the user and calculate
	 * their 'like score'. Most likely only the positive scores should be recommended, with a higher score meaning more highly recommended.
	 * 
	 * Links to be recommending are those that have not been shared by his friends, to increase the likelihood of the user 
	 * not having seen these links before.
	 * 
	 * @param friendships
	 * @param type
	 * @return
	 * @throws SQLException
	 */
	public HashMap<Long, HashSet<Long>> getLinksForRecommending(HashMap<Long, HashMap<Long, Double>> friendships, String type)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> userLinks = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		//Recommend only for users that have not installed the LinkRecommender app.
		//These users are distinguished by having priority 1 in the trackUserUpdates table.
		HashSet<Long> userIds = new HashSet<Long>();
		ResultSet result = statement.executeQuery("SELECT linkrLinks.uid FROM linkrLinks, trackUserUpdates "
													+ "WHERE linkrLinks.uid=trackUserUpdates.uid "
													+ "AND priority=1");
		
		while (result.next()) {
			userIds.add(result.getLong("uid"));
		}
		
		for (Long id : userIds) {
			HashSet<Long> links = new HashSet<Long>();
			userLinks.put(id, links);
			
			Set<Long> friends = friendships.get(id).keySet();
			if (friends == null) friends = new HashSet<Long>();
			
			HashSet<Long> dontInclude = new HashSet<Long>();
			
			// Don't recommend links that were already liked
			result = statement.executeQuery("SELECT l.id FROM linkrLinks l, linkrPostLikes lp WHERE l.id=lp.post_id AND lp.id=" + id);
			while (result.next()) {
				dontInclude.add(result.getLong("l.id"));
			}
			
			// Don't recommend links have already been recommended
			result = statement.executeQuery("SELECT link_id FROM lrRecommendations WHERE user_id=" + id + " AND type='" + type + "'");
			while(result.next()) {
				dontInclude.add(result.getLong("link_id"));
			}
			
			// Get the most recent links.
			StringBuilder query = new StringBuilder("SELECT id FROM linkrLinks WHERE uid NOT IN (0");
			for (Long friendId : friends) {
				query.append(",");
				query.append(friendId);
			}
			query.append(") AND id NOT IN(0");
			for (long linkIds : dontInclude) {
				query.append(",");
				query.append(linkIds);
			}
			
			query.append(") ORDER BY created_time DESC LIMIT 20");
			
			result = statement.executeQuery(query.toString());
			
			while (result.next()) {
				links.add(result.getLong("id"));
			}
		}
		
		return userLinks;
	}
	
	/**
	 * Save the recommended links into the database.
	 * 
	 * @param recommendations
	 * @param type
	 * @throws SQLException
	 */
	public void saveLinkRecommendations(HashMap<Long, HashMap<Long, Double>> recommendations, String type)
		throws SQLException
	{
		Connection conn = RecommenderUtil.getSqlConnection();
		
		Statement statement = conn.createStatement();
		
		for (long userId : recommendations.keySet()) {
			HashMap<Long, Double> recommendedLinks = recommendations.get(userId);
			
			//statement.executeUpdate("DELETE FROM " + tableName + " WHERE user_id=" + userId);
			
			for (long linkId : recommendedLinks.keySet()) {
				PreparedStatement ps = conn.prepareStatement("INSERT INTO lrRecommendations VALUES(?,?,?,CURRENT_DATE(),?)");
				ps.setLong(1, userId);
				ps.setLong(2, linkId);
				ps.setDouble(3, recommendedLinks.get(linkId));
				ps.setString(4, type);
				
				ps.executeUpdate();
				ps.close();
			}
		}
		
		statement.close();	
	}
	
	/**
	 * Loads the previously trained matrix from the database
	 * 
	 * @param tableName
	 * @param featureCount
	 * @param type
	 * @return
	 * @throws SQLException
	 */
	public Double[][] loadFeatureMatrix(String tableName, int featureCount, String type)
		throws SQLException
	{
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		Double[][] matrix = new Double[Constants.K][featureCount];
		
		ResultSet result = statement.executeQuery("SELECT * FROM " + tableName + " WHERE id < " + Constants.K + " AND type='" + type + "'");
		
		int count = 0;
		
		//Columns were saved in the database with id being row and the column values as one CSV string
		while (result.next()) {
			count++;
			int id = result.getInt("id");
			String values = result.getString("value");
			String[] tokens = values.split(",");
			
			for (int x = 0; x < tokens.length; x++) {
				matrix[id][x] = Double.parseDouble(tokens[x]);
			}
		}
		
		statement.close();
		
		if (count == 0) return null;
		
		return matrix;
	}
	
	/**
	 * Loads the previously trained matrix id columns from the database.
	 * 
	 * @param tableName
	 * @param type
	 * @return
	 * @throws SQLException
	 */
	public HashMap<Long, Double[]> loadIdColumns(String tableName, String type)
		throws SQLException
	{
		HashMap<Long, Double[]> idColumns = new HashMap<Long, Double[]>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		ResultSet result = statement.executeQuery("SELECT * FROM " + tableName + " WHERE id >" + Constants.K + " AND type='" + type + "'");
		while (result.next()) {
			long id = result.getLong("id");
			String values = result.getString("value");
			String[] tokens = values.split(",");
			
			//Column valuess were saved as one CSV string
			Double[] val = new Double[Constants.K];
			for (int x = 0; x < Constants.K; x++) {
				val[x] = Double.parseDouble(tokens[x]);
			}
			
			idColumns.put(id, val);
		}
		
		statement.close();
		
		
		return idColumns;
	}
	
	/**
	 * Save the trained matrices into the database.
	 * 
	 * @param userFeatureMatrix
	 * @param linkFeatureMatrix
	 * @param userIdColumns
	 * @param linkIdColumns
	 * @param wordColumns
	 * @param type
	 * @throws SQLException
	 */
	public void saveMatrices(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, HashMap<String, Double[]> wordColumns, String type)
		throws SQLException
	{
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		statement.executeUpdate("DELETE FROM lrUserMatrix WHERE type='" + type + "'");
		statement.executeUpdate("DELETE FROM lrLinkMatrix WHERE type='" + type + "'");
		statement.executeUpdate("DELETE FROM lrWordColumns WHERE type='" + type + "'");
		
		for (int x = 0; x < userFeatureMatrix.length; x++) {
			StringBuilder userBuf = new StringBuilder();
			for (int y = 0; y < Constants.USER_FEATURE_COUNT; y++) {
				userBuf.append(userFeatureMatrix[x][y]);
				userBuf.append(",");
			}
			
			StringBuilder linkBuf = new StringBuilder();
			for (int y = 0; y < Constants.LINK_FEATURE_COUNT; y++) {
				linkBuf.append(linkFeatureMatrix[x][y]);
				linkBuf.append(",");
			}
			
			PreparedStatement userInsert = conn.prepareStatement("INSERT INTO lrUserMatrix VALUES(?,?,?)");
			userInsert.setLong(1, x);
			userInsert.setString(2, userBuf.toString());
			userInsert.setString(3, type);
			userInsert.executeUpdate();
			userInsert.close();
			
			PreparedStatement linkInsert = conn.prepareStatement("INSERT INTO lrLinkMatrix VALUES(?,?,?)");
			linkInsert.setLong(1, x);
			linkInsert.setString(2, linkBuf.toString());
			linkInsert.setString(3, type);
			linkInsert.executeUpdate();
			linkInsert.close();
		}
		
		//Save the id column values as a CSV string
		for (long userId : userIdColumns.keySet()) {
			StringBuilder buf = new StringBuilder();
			
			Double[] col = userIdColumns.get(userId);
			for (int x = 0; x < Constants.K; x++) {
				buf.append(col[x]);
				buf.append(",");
			}
			
			PreparedStatement userInsert = conn.prepareStatement("INSERT INTO lrUserMatrix VALUES(?,?,?)");
			userInsert.setLong(1, userId);
			userInsert.setString(2, buf.toString());
			userInsert.setString(3, type);
			userInsert.executeUpdate();
			userInsert.close();
		}
		
		for (long linkId : linkIdColumns.keySet()) {
			StringBuilder buf = new StringBuilder();
			
			Double[] col = linkIdColumns.get(linkId);
			for (int x = 0; x < Constants.K; x++) {
				buf.append(col[x]);
				buf.append(",");
			}
			
			PreparedStatement linkInsert = conn.prepareStatement("INSERT INTO lrLinkMatrix VALUES(?,?,?)");
			linkInsert.setLong(1, linkId);
			linkInsert.setString(2, buf.toString());
			linkInsert.setString(3, type);
			linkInsert.executeUpdate();
			linkInsert.close();
		}
		
		//Save the word column values as a CSV string
		for (String word : wordColumns.keySet()) {
			StringBuilder buf = new StringBuilder();
			
			Double[] col = wordColumns.get(word);
			for (int x = 0; x < Constants.K; x++) {
				buf.append(col[x]);
				buf.append(",");
			}
			
			PreparedStatement wordInsert = conn.prepareStatement("INSERT INTO lrWordColumns VALUES(?,?,?)");
			wordInsert.setString(1, word);
			wordInsert.setString(2, buf.toString());
			wordInsert.setString(3, type);
			wordInsert.executeUpdate();
			wordInsert.close();
		}
	}
	
	/**
	 * Loads the previously trained word columns from the database.
	 * 
	 * @param tableName
	 * @param type
	 * @return
	 * @throws SQLException
	 */
	public HashMap<String, Double[]> loadWordColumns(String type)
		throws SQLException
	{
		HashMap<String, Double[]> columns = new HashMap<String, Double[]>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		ResultSet result = statement.executeQuery("SELECT * FROM lrWordColumns WHERE type='" + type + "'");
		while (result.next()) {
			String word = result.getString("word");
			String values = result.getString("value");
			String[] tokens = values.split(",");
			
			Double[] val = new Double[Constants.K];
			for (int x = 0; x < Constants.K; x++) {
				val[x] = Double.parseDouble(tokens[x]);
			}
			
			columns.put(word, val);
		}
		
		statement.close();
		
		return columns;
	}
	
	/**
	 * Since we're doing online updating, we need to update the matrix columns by removing links/users from the previous training that aren't included
	 * anymore and adding the new ones that weren't existing in the previous training.
	 * 
	 * @param ids
	 * @param idColumns
	 */
	public void updateMatrixColumns(Set<Long> ids, HashMap<Long, Double[]> idColumns)
	{
		HashSet<Long> columnsToRemove = new HashSet<Long>();
		
		//Remove columns that are past the range
		for (long id : idColumns.keySet()) {
			if (!ids.contains(id)) {
				columnsToRemove.add(id);
			}
		}
		for (long id : columnsToRemove) {
			idColumns.remove(id);
		}
		
		//Add columns for the new ones
		HashSet<Long> columnsToAdd = new HashSet<Long>();
		
		for (long id : ids) {
			if (!idColumns.containsKey(id)) {
				columnsToAdd.add(id);
			}
		}
		HashMap<Long, Double[]> newColumns = getMatrixIdColumns(columnsToAdd);
		idColumns.putAll(newColumns);
	}
	
	/**
	 * Columns for the ids are placed into a HashMap
	 * 
	 * @param ids
	 * @return
	 */
	public HashMap<Long, Double[]> getMatrixIdColumns(Set<Long> ids)
	{
		Random random = new Random();
		
		HashMap<Long, Double[]> idColumns = new HashMap<Long, Double[]>();
		
		for (long id : ids) {
			Double[] column = new Double[Constants.K];
			
			for (int x = 0; x < column.length; x++) {
				column[x] = random.nextGaussian();
				//column[x] = 0.0;
			}
			
			idColumns.put(id, column);
		}
		
		return idColumns;
	}
	
	/**
	 * Columns for words in the link feature are placed into a HashMap
	 * 
	 * @param words
	 * @return
	 */
	public HashMap<String, Double[]> getWordColumns(Set<String> words)
	{
		Random random = new Random();
		
		HashMap<String, Double[]> wordColumns = new HashMap<String, Double[]>();
		
		for (String word : words) {
			Double[] column = new Double[Constants.K];
			
			for (int x = 0; x < column.length; x++) {
				column[x] = random.nextGaussian();
				//column[x] = 0.0;
			}
			
			wordColumns.put(word, column);
		}
		
		return wordColumns;
	}
	
	/**
	 * Calculate the recommendation scores of the link
	 * 
	 * @param userFeatureMatrix
	 * @param linkFeatureMatrix
	 * @param userIdColumns
	 * @param linkIdColumns
	 * @param userFeatures
	 * @param linkFeatures
	 * @param linksToRecommend
	 * @param linkWords
	 * @param wordColumns
	 * @return
	 */
	public HashMap<Long, HashMap<Long, Double>> recommendLinks(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
																HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, 
																HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures,
																HashMap<Long, HashSet<Long>> linksToRecommend, HashMap<Long, HashSet<String>> linkWords,
																HashMap<String, Double[]> wordColumns)
	{
		HashMap<Long, HashMap<Long, Double>> recommendations = new HashMap<Long, HashMap<Long, Double>>();
		
		HashMap<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
		HashMap<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures, linkWords, wordColumns);
		
		for (long userId :linksToRecommend.keySet()) {
			HashSet<Long> userLinks = linksToRecommend.get(userId);
			
			HashMap<Long, Double> linkValues = new HashMap<Long, Double>();
			recommendations.put(userId, linkValues);
			
			for (long linkId : userLinks) {
				if (!linkTraits.containsKey(linkId)) continue;
				
				double prediction = RecommenderUtil.dot(userTraits.get(userId), linkTraits.get(linkId));
				
				//Recommend only if prediction score is a positive value
				if (prediction > 0) {
					//We recommend only a set number of links per day/run. 
					//If the recommended links are more than the max number, recommend only the highest scoring links.
					if (linkValues.size() < Constants.RECOMMENDATION_COUNT) {
						linkValues.put(linkId, prediction);
					}
					else {
						//Get the lowest scoring recommended link and replace it with the current link
						//if this one has a better score.
						long lowestKey = 0;
						double lowestValue = Double.MAX_VALUE;
						
						for (long id : linkValues.keySet()) {
							if (linkValues.get(id) < lowestValue) {
								lowestKey = id;
								lowestValue = linkValues.get(id);
							}
						}
						
						if (prediction < lowestValue) {
							linkValues.remove(lowestKey);
							linkValues.put(linkId, prediction);
						}
					}
				}
			}
		}
		
		return recommendations;
	}
}