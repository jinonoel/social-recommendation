package org.nicta.lr.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;


public class UserUtil 
{
	/**
	 * Retrieves user features from the database and saves them into a HashMap.
	 * User features are normalized between 0 and 1. Only features that don't grow are currently used.
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static HashMap<Long, Double[]> getUserFeatures()
		throws SQLException
	{
		HashMap<Long, Double[]> userFeatures = new HashMap<Long, Double[]>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String userQuery = 
			"SELECT uid, gender, birthday, location_id, hometown_id "
			+ "FROM linkrUser";
		
		ResultSet result = statement.executeQuery(userQuery);
		
		while (result.next()) {
			String sex = result.getString("gender");
			
			//We're only interested on the age for this one.
			int birthYear = 0;
			String birthday = result.getString("birthday");
			if (birthday.length() == 10) {
				birthYear = Integer.parseInt(birthday.split("/")[2]);
			}
			
			double currentLocation = result.getLong("location_id") / 300000000000000.0;
			double hometownLocation = result.getLong("hometown_id") / 300000000000000.0;
			
			//Features are normalized between 0 and 1
			Double[] feature = new Double[Constants.USER_FEATURE_COUNT];
			if ("male".equals(sex)) {
				feature[0] = 0.5;
			}
			else if ("female".equals(sex)){
				feature[0] = 1.0;
			}
			else {
				feature[0] = 0.0;
			}
			
			feature[1] = birthYear / 2012.0;
			
			feature[2] = currentLocation;
			feature[3] = hometownLocation;
			
			userFeatures.put(result.getLong("uid"), feature);
		}
		
		statement.close();
		
		return userFeatures;
	}
	
	/**
	 * Gets all user friendship connections that are saved in the DB.
	 * Each user will have an entry in the HashMap and a HashSet that will contain the ids of 
	 * the user's friends.
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static HashMap<Long, HashMap<Long, Double>> getFriendships()
		throws SQLException
	{
		HashMap<Long, HashMap<Long, Double>> friendships = new HashMap<Long, HashMap<Long, Double>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String friendQuery =
			"SELECT uid1, uid2 "
			+ "FROM linkrFriends";
		
		ResultSet result = statement.executeQuery(friendQuery);
		
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			friendships.get(uid1).put(uid2, 1.0);
			friendships.get(uid2).put(uid1, 1.0);
		}
		
		
		statement.close();
		
		
		return friendships;
	}

	/**
	 * Friendship measure which is a normalized sum of the user-user interaction. Values are normalized by the 
	 * maximum number of interactions. Should interactions all carry the same weight? Maybe something like getting 
	 * tagged in the same photo carry a heavier weight?
	 * 
	 * Interaction measure between user1 and user2 will be equal to user2 and user1.
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static HashMap<Long, HashMap<Long, Double>> getFriendInteractionMeasure()
		throws SQLException
	{
		HashMap<Long, HashMap<Long, Double>> friendships = new HashMap<Long, HashMap<Long, Double>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		//First interaction is the friend links. Friend links are now just one kind of interaction
		String friendQuery =
			"SELECT uid1, uid2 "
			+ "FROM linkrFriends";
		
		ResultSet result = statement.executeQuery(friendQuery);
		
		double max_value = 0;
		
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			friendships.get(uid1).put(uid2, 1.0);
			friendships.get(uid2).put(uid1, 1.0);
		}
		
		// Comments on photos
		result = statement.executeQuery("SELECT uid, from_id FROM linkrPhotoComments");
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		// Likes on photos
		result = statement.executeQuery("SELECT uid, id FROM linkrPhotoLikes");
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		// Times a user has posted a photo on someone else's wall
		result = statement.executeQuery("SELECT uid, from_id FROM linkrPhotos");
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Times that a user has been tagged in another user's photo
		//Also get the people that are tagged per photo. Users getting tagged in the same photo is a pretty good measure 
		//that they're friends.
		//
		HashMap<Long, HashSet<Long>> photoTags = new HashMap<Long, HashSet<Long>>();
		
		result = statement.executeQuery("SELECT l.uid, t.uid, t.photo_id FROM linkrPhotos l, linkrPhotoTags t WHERE t.photo_id=l.id");
		while (result.next()) {
			Long uid1 = result.getLong("l.uid");
			Long uid2 = result.getLong("t.uid");
			Long photoId = result.getLong("t.photo_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
			
			HashSet<Long> tagged; //Users that were already tagged in this photo
			if (photoTags.containsKey(photoId)) {
				tagged = photoTags.get(photoId);
			}
			else {
				tagged = new HashSet<Long>();
				photoTags.put(photoId, tagged);
			}
			
			if (tagged.contains(uid2)) continue;
			
			//Given the new tagged user, increment the interaction count between the user and
			//all users that were already tagged in the photo.
			for (Long alreadyTagged : tagged) {		
				double tagVal = 1;
				
				if (friendships.get(alreadyTagged).containsKey(uid2)) {
					tagVal += friendships.get(alreadyTagged).get(uid2);
				}
				
				if (tagVal > max_value) max_value = tagVal;
				
				friendships.get(alreadyTagged).put(uid2, tagVal);
				friendships.get(uid2).put(alreadyTagged, tagVal);
			}
		}
		
		//Users posting on another user's wall
		result = statement.executeQuery("SELECT uid, from_id FROM linkrPost");
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Users commenting on another user's posts
		result = statement.executeQuery("SELECT uid, from_id FROM linkrPostComments");
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Users liking another user's posts.
		result = statement.executeQuery("SELECT uid, id FROM linkrPostLikes");
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Users being tagged in another user's posts.
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrPostTags");
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Users that went to the same classes
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrSchoolClassesWith");
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Users that went to the same school
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrSchoolWith");
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Users playing the same sports
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrSportsWith");
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Posting videos into another user's wall
		result = statement.executeQuery("SELECT uid, from_id FROM linkrVideos");
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Commenting on another user's video
		result = statement.executeQuery("SELECT uid, from_id FROM linkrVideoComments");
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Liking another user's video
		result = statement.executeQuery("SELECT uid, id FROM linkrVideoLikes");
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//User's getting tagged in another user's video.
		//Also get the people that are tagged per photo. Users getting tagged in the same photo is a pretty good measure 
		//that they're friends.
		HashMap<Long, HashSet<Long>> videoTags = new HashMap<Long, HashSet<Long>>();
		result = statement.executeQuery("SELECT t.uid, l.uid, t.video_id FROM linkrVideos l, linkrVideoTags t WHERE t.video_id=l.id");
		while (result.next()) {
			Long uid1 = result.getLong("l.uid");
			Long uid2 = result.getLong("t.uid");
			Long videoId = result.getLong("t.video_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
			
			HashSet<Long> tagged; //Users that were already tagged in this photo
			if (videoTags.containsKey(videoId)) {
				tagged = videoTags.get(videoId);
			}
			else {
				tagged = new HashSet<Long>();
				videoTags.put(videoId, tagged);
			}
			
			if (tagged.contains(uid2)) continue;
			
			//Given the new tagged user, increment the interaction count between the user and
			//all users that were already tagged in the photo.
			for (Long alreadyTagged : tagged) {		
				double tagVal = 1;
				
				if (friendships.get(alreadyTagged).containsKey(uid2)) {
					tagVal += friendships.get(alreadyTagged).get(uid2);
				}
				
				if (tagVal > max_value) max_value = tagVal;
				
				friendships.get(alreadyTagged).put(uid2, tagVal);
				friendships.get(uid2).put(alreadyTagged, tagVal);
			}
		}
		
		//User's working in the same project
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrWorkProjectsWith");
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//User's working in the same company
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrWorkWith");
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//User's liking a persons's link.
		result = statement.executeQuery("SELECT uid, id FROM linkrLinkLikes");
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//User's commenting on a person's link
		result = statement.executeQuery("SELECT uid, from_id FROM linkrLinkComments");
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//User's posting a link on someone else's wall
		result = statement.executeQuery("SELECT uid, from_id FROM linkrLinks");
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Normalize all friendship values with the maximum friendship value
		for (long uid1 : friendships.keySet()) {
			HashMap<Long, Double> friendValues = friendships.get(uid1);
			
			for (long uid2 : friendValues.keySet()) {
				double val = friendValues.get(uid2);
				val /= max_value;
				
				friendValues.put(uid2, val);
			}
		}
		
		statement.close();
		return friendships;
	}
	
	public static HashMap<Long, HashMap<Long, Double>> getFriendLikeSimilarity()
		throws SQLException
	{
		HashMap<Long, HashMap<Long, Double>> friendships = new HashMap<Long, HashMap<Long, Double>>();
		
		double max_value = 0;
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		ResultSet result = statement.executeQuery("SELECT DISTINCT l.id, ll.id FROM linkrPhotoLikes l, linkrPhotoLikes ll WHERE l.photo_id=ll.photo_id");
		
		while (result.next()) {
			Long uid1 = result.getLong("l.id");
			Long uid2 = result.getLong("ll.id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		result = statement.executeQuery("SELECT DISTINCT l.id, ll.id FROM linkrPostLikes l, linkrPostLikes ll WHERE l.post_id=ll.post_id");
		while (result.next()) {
			Long uid1 = result.getLong("l.id");
			Long uid2 = result.getLong("ll.id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		result = statement.executeQuery("SELECT DISTINCT l.id, ll.id FROM linkrVideoLikes l, linkrVideoLikes ll WHERE l.video_id=ll.video_id");
		while (result.next()) {
			Long uid1 = result.getLong("l.id");
			Long uid2 = result.getLong("ll.id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		result = statement.executeQuery("SELECT DISTINCT l.id, ll.id FROM linkrLinkLikes l, linkrLinkLikes ll WHERE l.link_id=ll.link_id");
		while (result.next()) {
			Long uid1 = result.getLong("l.id");
			Long uid2 = result.getLong("ll.id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		for (long uid1 : friendships.keySet()) {
			HashMap<Long, Double> friendValues = friendships.get(uid1);
			
			for (long uid2 : friendValues.keySet()) {
				double val = friendValues.get(uid2);
				val /= max_value;
				
				friendValues.put(uid2, val);
			}
		}
		
		statement.close();
		return friendships;
	}
	
	/**
	 * Calculates s=Ux where U is the latent matrix and x is the user vector.
	 * 
	 * @param matrix
	 * @param idColumns
	 * @param features
	 * @return
	 */
	public static HashMap<Long, Double[]> getUserTraitVectors(Double[][] matrix, 
														HashMap<Long, Double[]> idColumns,
														HashMap<Long, Double[]> features)
	{
		HashMap<Long, Double[]> traitVectors = new HashMap<Long, Double[]>();
		
		for (long id : features.keySet()) {
			Double[] feature = features.get(id);
			Double[] vector = new Double[Constants.K];
			Double[] idColumn = idColumns.get(id);
		
			for (int x = 0; x < Constants.K; x++) {
				vector[x] = 0.0;
		
				for (int y = 0; y < feature.length; y++) {
					vector[x] += matrix[x][y] * feature[y];
				}
		
				vector[x] += idColumn[x];
			}
		
			traitVectors.put(id, vector);
		}
		
		return traitVectors;
	}
}